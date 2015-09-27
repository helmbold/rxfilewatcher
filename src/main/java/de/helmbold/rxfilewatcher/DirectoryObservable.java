package de.helmbold.rxfilewatcher;

import rx.Observable;
import rx.Subscriber;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

public final class DirectoryObservable {

  private DirectoryObservable() {
  }

  /**
   * Creates an observable that watches the given directory and all its subdirectories. It doesen't
   * matter when a subdirectory is created.
   * @param dir Root directory to be watched
   * @return Observable that emits an event for each filesystem event.
   * @throws IOException
   */
  public static Observable<WatchEvent<?>> createRecursive(final Path dir) throws IOException {
    return new ObservableFactory(dir, true).create();
  }

  /**
   * Creates an observable that watches the given directory but not its subdirectories.
   * @param dir Directory to be watched
   * @return Observable that emits an event for each filesystem event.
   * @throws IOException
   */
  public static Observable<WatchEvent<?>> createNonRecursive(final Path dir) throws IOException {
    return new ObservableFactory(dir, false).create();
  }

  private static class ObservableFactory {

    private final WatchService watcher;
    private final Map<WatchKey, Path> directoriesByKey = new HashMap<>();
    private final Path directory;
    private final boolean recursive;

    private ObservableFactory(final Path directory, final boolean recursive) throws IOException {
      final FileSystem fileSystem = directory.getFileSystem();
      watcher = fileSystem.newWatchService();
      this.directory = directory;
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
          subscriber.onCompleted();
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
        final Subscriber<? super WatchEvent<?>> subscriber,
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
