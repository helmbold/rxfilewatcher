package de.helmbold.rxfilewatcher;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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


  /**
   * Test the fix for https://github.com/helmbold/rxfilewatcher/issues/8
   *
   * Previously, the PathObservables.ObservableFactory did not call WatchService.close()
   * before completing its subscription. This caused WatchService instances to leak, which in turn
   * could eventually cause the OS to enforce its limits on the leaky process.
   */
  @Test(timeOut = 45000)
  public void shouldCloseWatchService() throws IOException {
    final Path directory = Files.createTempDirectory(null);
    final byte[] empty = new byte[0];
    final int cycles = 768; // 768 = largest known default watch limit (windows) * 1.5
    // due to a small time gap between subscription and watcher starting to publish events,
    // a small delay is necessary in order to reliably exercise the observable's lifecycle
    final int writeDelayMs = 20;

    for (int i = 0; i < cycles; i++) {
      int finalI = i;
      final WatchEvent<?> event = PathObservables.watchNonRecursive(directory)
              .doOnSubscribe((disposable) -> Single.just(directory.resolve(String.valueOf(finalI)))
                      .delay(writeDelayMs, TimeUnit.MILLISECONDS)
                      .subscribe(noise -> Files.write(noise, empty)))
              .blockingFirst();
      assertThat(event).isNotNull();
      System.out.println(i);
    }

    FileUtils.deleteDirectory(directory.toFile());
  }
}