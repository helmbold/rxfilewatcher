package de.helmbold.rxfilewatcher;

import org.apache.commons.io.FileUtils;
import org.testng.annotations.Test;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

public final class PathObservablesTest {

  @Test
  public void shouldWatchDirectoryRecursively() throws IOException, InterruptedException {

    final Path directory = Files.createTempDirectory(".");
    final Observable<WatchEvent<?>> observable = PathObservables.watchRecursive(directory);
    final List<WatchEvent<?>> events = new LinkedList<>();

    observable.subscribeOn(Schedulers.io()).subscribe(events::add);

    Thread.sleep(10); // needed because the registration of the watcher needs some time

    final CompletableFuture<Void> fileModificationFuture = CompletableFuture.runAsync(() -> {
      try {
        final Path newDir = directory.resolve(Paths.get("a"));
        Files.createDirectory(newDir);
        final Path file = newDir.resolve("b");
        Thread.sleep(10); // needed because the registration of the watcher needs some time
        Files.createFile(file);
      } catch (final IOException ex) {
        throw new RuntimeException(ex);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    fileModificationFuture.join();
    assertThat(events).hasSize(2);
    final WatchEvent<?> firstEvent = events.get(0);
    final WatchEvent<?> secondEvent = events.get(1);
    assertThat(firstEvent.kind()).isEqualTo(StandardWatchEventKinds.ENTRY_CREATE);
    assertThat(firstEvent.context().toString()).isEqualTo("a");
    assertThat(secondEvent.kind()).isEqualTo(StandardWatchEventKinds.ENTRY_CREATE);
    assertThat(secondEvent.context().toString()).isEqualTo("b");

    FileUtils.deleteDirectory(directory.toFile());
  }

  @Test
  public void shouldWatchDirectoryNonRecursively() throws IOException {
    final Path directory = Files.createTempDirectory(".");
    final Observable<WatchEvent<?>> observable = PathObservables.watchNonRecursive(directory);
    final List<WatchEvent<?>> events = new LinkedList<>();

    observable.subscribeOn(Schedulers.io()).subscribe(events::add);

    final CompletableFuture<Void> fileModificationFuture = CompletableFuture.runAsync(() -> {
      try {
        final Path newDir = directory.resolve(Paths.get("a"));
        Files.createDirectory(newDir);
        final Path file = newDir.resolve("b");
        Files.createFile(file);
      } catch (final IOException ex) {
        throw new RuntimeException(ex);
      }
    });

    fileModificationFuture.join();
    assertThat(events).hasSize(1);
    final WatchEvent<?> event = events.get(0);
    assertThat(event.kind()).isEqualTo(StandardWatchEventKinds.ENTRY_CREATE);
    assertThat(event.context().toString()).isEqualTo("a");

    FileUtils.deleteDirectory(directory.toFile());
  }

}