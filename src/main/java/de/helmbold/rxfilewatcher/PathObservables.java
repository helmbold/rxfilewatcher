package de.helmbold.rxfilewatcher;


import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

public final class PathObservables {

  private PathObservables() {
  }

  /**
   * Creates an observable that watches the given directory and all its subdirectories. Directories
   * that are created after subscription are watched, too.
   * @param path Root directory to be watched
   * @return Observable that emits an event for each filesystem event.
   * @throws IOException
   */
  public static Observable<WatchEvent<?>> watchRecursive(final Path path) throws IOException {
    final boolean recursive = true;
    return new ObservableFactory(path, recursive).create();
  }

  /**
   * Creates an observable that watches the given path but not its subdirectories.
   * @param path Path to be watched
   * @return Observable that emits an event for each filesystem event.
   * @throws IOException
   */
  public static Observable<WatchEvent<?>> watchNonRecursive(final Path path) throws IOException {
    final boolean recursive = false;
    return new ObservableFactory(path, recursive).create();
  }

  private static class ObservableFactory {

    private final WatchService watcher;
    private final Map<WatchKey, Path> directoriesByKey = new HashMap<>();
    private final Path directory;
    private final boolean recursive;

    private ObservableFactory(final Path path, final boolean recursive) throws IOException {
      final FileSystem fileSystem = path.getFileSystem();
      watcher = fileSystem.newWatchService();
      directory = path;
      this.recursive = recursive;
    }

    private Observable<WatchEvent<?>> create() {
      return Observable.create(subscriber -> {
        boolean errorFree = true;
        try {
          if (recursive) {
            registerAll(directory);
          } else {
            register(directory);
          }
        } catch (IOException exception) {
          subscriber.onError(exception);
          errorFree = false;
        }
        while (errorFree) {
          final WatchKey key;
          try {
            key = watcher.take();
          } catch (InterruptedException exception) {
            subscriber.onError(exception);
            errorFree = false;
            break;
          }
          final Path dir = directoriesByKey.get(key);
          for (final WatchEvent<?> event : key.pollEvents()) {
            subscriber.onNext(event);
            registerNewDirectory(subscriber, dir, event);
          }
          // reset key and remove from set if directory is no longer accessible
          boolean valid = key.reset();
          if (!valid) {
            directoriesByKey.remove(key);
            // nothing to be watched
            if (directoriesByKey.isEmpty()) {
              break;
            }
          }
        }
        if (errorFree) {
          subscriber.onComplete();
        }
      });
    }

    /**
     * Register the rootDirectory, and all its sub-directories.
     */
    private void registerAll(final Path rootDirectory) throws IOException {
      Files.walkFileTree(rootDirectory, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
            throws IOException {
          register(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    }

    private void register(final Path dir) throws IOException {
      final WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
      directoriesByKey.put(key, dir);
    }

    // register newly created directory to watching in recursive mode
    private void registerNewDirectory(
        final ObservableEmitter<WatchEvent<?>> subscriber,
        final Path dir,
        final WatchEvent<?> event) {
      final Kind<?> kind = event.kind();
      if (recursive && kind.equals(ENTRY_CREATE)) {
        // Context for directory entry event is the file name of entry
        @SuppressWarnings("unchecked")
        final WatchEvent<Path> eventWithPath = (WatchEvent<Path>) event;
        final Path name = eventWithPath.context();
        final Path child = dir.resolve(name);
        try {
          if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
            registerAll(child);
          }
        } catch (final IOException exception) {
          subscriber.onError(exception);
        }
      }
    }
  }
}
