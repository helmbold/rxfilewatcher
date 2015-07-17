package de.helmbold.filewatcher;

import org.apache.commons.io.FileUtils;
import org.testng.annotations.Test;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public final class DirectoryObservableTest {

  @Test
  public void shouldWatchDirectoryRecursivly() throws IOException {
    final int expectedEventCount = 3;
    createFilesAndWatch((dir) -> {
      try {
        return DirectoryObservable.createRecursive(dir);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }, expectedEventCount);
  }

  @Test
  public void shouldWatchDirectoryNonRecursivly() throws IOException {
    final int expectedEventCount = 2;
    createFilesAndWatch((dir) -> {
      try {
        return DirectoryObservable.createNonRecursive(dir);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }, expectedEventCount);
  }

  private static void createFilesAndWatch(
      final Function<Path, Observable<WatchEvent<?>>> observableFactory,
      final int expectedEventCount)
      throws IOException {
    final Path directory = Files.createTempDirectory(".");
    final List<WatchEvent<?>> events = new LinkedList<>();
    final Observable<WatchEvent<?>> observable = observableFactory.apply(directory);
    observable.subscribeOn(Schedulers.io()).subscribe(events::add);
    observable.subscribeOn(Schedulers.io()).subscribe(System.out::println);

    new Thread() {
      @Override
      public void run() {
        try {
          final Path newDir = directory.resolve(Paths.get("a"));
          Files.createDirectory(newDir);
          final Path file = newDir.resolve("b");
          Files.createFile(file);
        } catch (IOException e) {
          throw new RuntimeException();
        }
      }
    }.run();

    assertThat(events).hasSize(expectedEventCount);
    FileUtils.deleteDirectory(directory.toFile());
  }
}