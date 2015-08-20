package de.helmbold.rxfilewatcher;

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

import static org.assertj.core.api.Assertions.assertThat;

public final class FileWatcherTest {

  @Test
  public void shouldWatchDirectoryRecursively() throws IOException, InterruptedException {
    final Path directory = Files.createTempDirectory(".");
    final Observable<WatchEvent<?>> observable = DirectoryObservable.createRecursive(directory);
    final List<WatchEvent<?>> events = new LinkedList<>();

    observable.subscribeOn(Schedulers.io()).subscribe(events::add);

    new Thread() {
      @Override
      public void run() {
        try {
          final Path newDir = directory.resolve(Paths.get("a"));
          Files.createDirectory(newDir);
          final Path file = newDir.resolve("b");
          Files.createFile(file);
        } catch (final IOException _) {
          throw new RuntimeException();
        }
      }
    }.start();


    events.stream().forEach(e -> System.out.println(e.kind()));
    assertThat(events).hasSize(3);
    FileUtils.deleteDirectory(directory.toFile());
  }

  @Test
  public void shouldWatchDirectoryNonRecursively() throws IOException {
    final Path directory = Files.createTempDirectory(".");
    final Observable<WatchEvent<?>> observable = DirectoryObservable.createNonRecursive(directory);
    final List<WatchEvent<?>> events = new LinkedList<>();

    observable.subscribeOn(Schedulers.io()).subscribe(events::add);

    new Thread() {
      @Override
      public void run() {
        try {
          final Path newDir = directory.resolve(Paths.get("a"));
          Files.createDirectory(newDir);
          final Path file = newDir.resolve("b");
          Files.createFile(file);
        } catch (final IOException _) {
          throw new RuntimeException();
        }
      }
    }.start();

    assertThat(events).hasSize(2);
    FileUtils.deleteDirectory(directory.toFile());
  }

}