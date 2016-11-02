# File Connectors

The File connectors provides additional connectors for filesystems complementing 
the sources and sinks for files already included in core Akka Streams 
(which can be found in `akka.stream.javadsl.FileIO` and `akka.stream.scaladsl.FileIO`).

## Artifacts

sbt
:   @@@vars
    ```scala
    libraryDependencies += "com.typesafe.akka" %% "akka-stream-alpakka-file" % "$version$"
    ```
    @@@

Maven
:   @@@vars
    ```xml
    <dependency>
      <groupId>com.typesafe.akka</groupId>
      <artifactId>akka-stream-alpakka-file_$scala.binaryVersion$</artifactId>
      <version>$version$</version>
    </dependency>
    ```
    @@@

Gradle
:   @@@vars
    ```gradle
    dependencies {
      compile group: "com.typesafe.akka", name: "akka-stream-alpakka-file_$scala.binaryVersion$", version: "$version$"
    }
    ```
    @@@

## Usage

### FileTailSource

The `FileTailSource` starts at a given offset in a file and emits chunks of bytes until reaching
the end of the file, it will then poll the file for changes and emit new changes as they are written
 to the file (unless there is backpressure).
 
A very common use case is combining reading bytes with parsing the bytes into lines, therefore 
`FileTailSource` contains a few factory methods to create a source that parses the bytes into
lines and emits those.

In this sample we simply tail the lines of a file and print them to standard out:

Scala
: @@snip (../../../../file/src/test/scala/akka/stream/alpakka/file/scaladsl/FileTailSourceSpec.scala) { #simple-lines }

Java
: @@snip (../../../../file/src/test/java/akka/stream/alpakka/file/javadsl/FileTailSourceTest.java) { #simple-lines }


### DirectoryChangesSource

The `DirectoryChangesSource` will emit elements every time there is a change to a watched directory
in the local filesystem, the emitted change concists of the path that was changed and an enumeration 
describing what kind of change it was.

In this sample we simply print each change to the directory to standard output:

Scala
: @@snip (../../../../file/src/test/scala/akka/stream/alpakka/file/scaladsl/DirectoryChangesSourceSpec.scala) { #minimal-sample }

Java
: @@snip (../../../../file/src/test/java/akka/stream/alpakka/file/javadsl/DirectoryChangesSourceTest.java) { #minimal-sample }


### Running the example code

Both the samples are contained in standalone runnable mains, they can be run
 from `sbt` like this:
 
Scala
:   ```
    sbt
    // tail source
    > akka-stream-alpakka-file/test:runMain akka.stream.alpakka.file.scaladsl.FileTailSourceSpec /some/path/toa/file
    // or directory changes
    > akka-stream-alpakka-file/test:runMain akka.stream.alpakka.file.scaladsl.DirectoryChangesSourceSpec /some/directory/path
    ```

Java
:   ```
    sbt
    // tail source
    > akka-stream-alpakka-file/test:runMain akka.stream.alpakka.file.javadsl.FileTailSourceTest /some/path/toa/file
    // or directory changes
    > akka-stream-alpakka-file/test:runMain akka.stream.alpakka.file.javadsl.DirectoryChangesSourceTest /some/directory/path
    ```
