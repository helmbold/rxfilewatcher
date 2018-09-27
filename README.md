# Reactive Wrapper for Java8 WatchService

**RxFileWatcher** allows you to observe directories (recursively or not) for file system events with a [RxJava](https://github.com/ReactiveX/RxJava) observable. It is based on the [JDK WatchService](https://docs.oracle.com/javase/8/docs/api/java/nio/file/WatchService.html), but it is much more convenient.

# What's New?

1. `Sep 12, 2018` Updated to Rx2, fix some bugs.
2. `Jul 18, 2015` Initial project, first release by [Helmbold](https://github.com/helmbold).


# Usage

The following example creates an observable that watches the given directory and all its subdirectories for file system events. Directories which are created later are watched, too. Each event will be emitted as a [WatchEvent](https://docs.oracle.com/javase/8/docs/api/java/nio/file/WatchEvent.html).

```java
PathObservables
  .watchRecursive(Paths.get("some/directory/"))
  .subscribe(event -> System.out.println(event));
```

To watch only the top-level directory, you call `watchNonRecursive` instead of `watchRecursive`:

```java
PathObservables
  .watchNonRecursive(Paths.get("some/directory/"))
  .subscribe(event -> System.out.println(event));
```

That's it!

See [RxJava Documentation](https://github.com/ReactiveX/RxJava/wiki) for more information, e. g. how you can filter certain types of events.

# Get it

Available on Maven Central.

## Maven

```xml
<dependency>
  <groupId>de.helmbold</groupId>
  <artifactId>rxfilewatcher</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Gradle

```groovy
implementation('de.helmbold:rxfilewatcher:1.0.0')
```

