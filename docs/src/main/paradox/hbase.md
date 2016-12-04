#HBbase connector

A flow and a composite sink to write element in [HBase](http://hbase.apache.org).

HBase is a column family NoSQL Database backed by HDFS.

 
#Usage

Build a converter and a tableSetting.

Converter will map the domain object to HBase column.

scala
:   @@snip (../../../../hbase/src/test/scala/akka/stream/alpakka/hbase/scaladsl/HBaseStageSpec.scala) { #create-converter }

java
:   @@snip (../../../../hbase/src/test/java/akka/stream/alpakka/hbase/javadsl/HBaseStageTest.java) { #create-converter }

Table will be created on demand.

scala
:   @@snip (../../../../hbase/src/test/scala/akka/stream/alpakka/hbase/scaladsl/HBaseStageSpec.scala) { #create-settings }

java
:   @@snip (../../../../hbase/src/test/java/akka/stream/alpakka/hbase/javadsl/HBaseStageTest.java) { #create-settings }

###Flow usage 

scala
: @@snip (../../../../hbase/src/test/scala/akka/stream/alpakka/hbase/scaladsl/HBaseStageSpec.scala) { #flow }

java
: @@snip (../../../../hbase/src/test/java/akka/stream/alpakka/hbase/javadsl/HBaseStageTest.java) { #flow }


###Sink usage

scala
: @@snip (../../../../hbase/src/test/scala/akka/stream/alpakka/hbase/scaladsl/HBaseStageSpec.scala) { #sink }

java
: @@snip (../../../../hbase/src/test/java/akka/stream/alpakka/hbase/javadsl/HBaseStageTest.java) { #sink }

##Hbase basic command:

```
$HBASE_HOME/bin/start-hbase.sh

$HBASE_HOME/bin/ shell

```

From the hbase shell:

```
list //list table
scan "person" // select * from person
disable "person" //Disable table "person", before drop
drop "person" 
```
