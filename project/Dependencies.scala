import sbt._
import Keys._

object Dependencies {

  val Scala211 = "2.11.12"
  val Scala212 = "2.12.7"
  val Scala213 = "2.13.0-M5"
  val ScalaVersions = Seq(Scala212, Scala211, Scala213)

  val AkkaVersion = sys.env.get("AKKA_SERIES") match {
    case Some("2.4") => sys.error("Akka 2.4 is not supported anymore")
    case _ => "2.5.22"
  }

  val AwsSdkVersion = "1.11.476"
  val AwsSdk2Version = "2.5.20"
  val AkkaHttpVersion = "10.1.7"
  val mockitoVersion = "2.27.0"

  val CouchbaseVersion = "2.7.2"
  val CouchbaseVersionForDocs = "2.7"

  // Allows to silence scalac compilation warnings selectively by code block or file path
  // This is only compile time dependency, therefore it does not affect the generated bytecode
  // https://github.com/ghik/silencer
  val Silencer = {
    val Version = "1.3.1"
    Seq(
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % Version),
      "com.github.ghik" %% "silencer-lib" % Version % Provided
    )
  }

  val Common = Seq(
    // These libraries are added to all modules via the `Common` AutoPlugin
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
      "ch.qos.logback" % "logback-classic" % "1.2.3" % Test, // Eclipse Public License 1.0
      "org.scalatest" %% "scalatest" % "3.0.7" % Test, // ApacheV2
      "com.novocode" % "junit-interface" % "0.11" % Test, // BSD-style
      "junit" % "junit" % "4.12" % Test // Eclipse Public License 1.0
    )
  )

  val Amqp = Seq(
    libraryDependencies ++= Seq(
      "com.rabbitmq" % "amqp-client" % "5.3.0" // APLv2
    )
  )

  val AwsLambda = Seq(
    libraryDependencies ++= Seq(
      "software.amazon.awssdk" % "lambda" % AwsSdk2Version, // ApacheV2
      "org.mockito" % "mockito-core" % mockitoVersion % Test // MIT
    )
  )

  val AzureStorageQueue = Seq(
    libraryDependencies ++= Seq(
      "com.microsoft.azure" % "azure-storage" % "8.0.0" // ApacheV2
    )
  )

  val Cassandra = Seq(
    libraryDependencies ++= Seq(
      "com.datastax.cassandra" % "cassandra-driver-core" % "3.7.1" // ApacheV2
    )
  )

  val Couchbase = Seq(
    libraryDependencies ++= Seq(
      "com.couchbase.client" % "java-client" % CouchbaseVersion, // ApacheV2
      "io.reactivex" % "rxjava-reactive-streams" % "1.2.1", //ApacheV2
      "com.typesafe.play" %% "play-json" % "2.7.1" % Test, // MIT like: http://www.slf4j.org/license.html
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion % Test // Apache V2
    )
  )

  val Csv = Seq(
    libraryDependencies ++= Seq()
  )

  val `Doc-examples` = Seq(
    libraryDependencies ++= Seq(
      // https://mina.apache.org/ftpserver-project/downloads.html
      "org.apache.ftpserver" % "ftpserver-core" % "1.1.1", // ApacheV2
      "org.apache.sshd" % "sshd-scp" % "2.1.0", // ApacheV2
      "org.apache.sshd" % "sshd-sftp" % "2.1.0", // ApacheV2
      "com.google.jimfs" % "jimfs" % "1.1", // ApacheV2
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http-xml" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      // https://github.com/akka/alpakka-kafka/releases
      "com.typesafe.akka" %% "akka-stream-kafka" % "1.0.1",
      // https://github.com/embeddedkafka/embedded-kafka/releases
      "io.github.embeddedkafka" %% "embedded-kafka" % "2.1.1", // MIT
      // https://github.com/javaee/javax.jms
      "javax.jms" % "jms" % "1.1", // CDDL Version 1.1
      // http://activemq.apache.org/download.html
      "org.apache.activemq" % "activemq-all" % "5.15.4" exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2
      "com.h2database" % "h2" % "1.4.197", // Eclipse Public License 1.0
      "org.elasticsearch.client" % "elasticsearch-rest-client" % "6.3.1", // ApacheV2
      "org.codelibs" % "elasticsearch-cluster-runner" % "6.3.1.0", // ApacheV2
      "io.netty" % "netty-all" % "4.1.29.Final", // ApacheV2
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % "2.9.8",
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.9.8",
      "org.slf4j" % "log4j-over-slf4j" % "1.7.25",
      "org.slf4j" % "jcl-over-slf4j" % "1.7.25",
      "ch.qos.logback" % "logback-classic" % "1.2.3" // Eclipse Public License 1.0
    )
  )

  val DynamoDB = Seq(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-dynamodb" % AwsSdkVersion, // ApacheV2
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion
    )
  )

  val Elasticsearch = Seq(
    libraryDependencies ++= Seq(
      "org.elasticsearch.client" % "elasticsearch-rest-client" % "6.3.1", // ApacheV2
      "io.spray" %% "spray-json" % "1.3.5", // ApacheV2
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.8", // ApacheV2
      "org.codelibs" % "elasticsearch-cluster-runner" % "6.3.1.0" % Test, // ApacheV2
      "org.slf4j" % "jcl-over-slf4j" % "1.7.25" % Test,
    )
  )

  val File = Seq(
    libraryDependencies ++= Seq(
      "com.google.jimfs" % "jimfs" % "1.1" % Test // ApacheV2
    )
  )

  val AvroParquet = Seq(
    libraryDependencies ++= Seq(
      "org.apache.parquet" % "parquet-avro" % "1.10.0", //Apache2
      "org.apache.hadoop" % "hadoop-client" % "3.1.0" % Test exclude ("log4j", "log4j"), //Apache2
      "org.apache.hadoop" % "hadoop-common" % "2.2.0" % Test exclude ("log4j", "log4j"), //Apache2
      "org.specs2" %% "specs2-core" % "4.4.1" % Test, //MIT like: https://github.com/etorreborre/specs2/blob/master/LICENSE.txt
      "org.slf4j" % "log4j-over-slf4j" % "1.7.25" % Test // MIT like: http://www.slf4j.org/license.html
    )
  )

  val Ftp = Seq(
    libraryDependencies ++= Seq(
      "commons-net" % "commons-net" % "3.6", // ApacheV2
      "com.hierynomus" % "sshj" % "0.26.0", // ApacheV2
      "org.apache.ftpserver" % "ftpserver-core" % "1.1.1" % Test, // ApacheV2
      "org.apache.sshd" % "sshd-scp" % "2.1.0" % Test, // ApacheV2
      "org.apache.sshd" % "sshd-sftp" % "2.1.0" % Test, // ApacheV2
      "net.i2p.crypto" % "eddsa" % "0.3.0" % Test, // CC0 1.0 Universal
      "com.google.jimfs" % "jimfs" % "1.1" % Test // ApacheV2
    )
  )

  val GeodeVersion = "1.8.0"
  val GeodeVersionForDocs = "18"

  val Geode = {
    val slf4jVersion = "1.7.25"
    Seq(
      libraryDependencies ++=
        Seq("geode-core", "geode-cq")
          .map("org.apache.geode" % _ % GeodeVersion exclude ("org.slf4j", "slf4j-log4j12")) ++
        Seq(
          "com.chuusai" %% "shapeless" % "2.3.3",
          "org.slf4j" % "log4j-over-slf4j" % slf4jVersion % Test // MIT like: http://www.slf4j.org/license.html
        )
    )
  }

  val GooglePubSub = Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "com.pauldijou" %% "jwt-core" % "2.1.0", // ApacheV2
      "org.mockito" % "mockito-core" % mockitoVersion % Test, // MIT
      "com.github.tomakehurst" % "wiremock" % "2.18.0" % Test // ApacheV2
    )
  )

  val GooglePubSubGrpc = Seq(
    libraryDependencies ++= Seq(
      "com.google.api.grpc" % "grpc-google-cloud-pubsub-v1" % "0.12.0" % "protobuf", // ApacheV2
      "io.grpc" % "grpc-auth" % "1.16.1", // ApacheV2
      "com.google.auth" % "google-auth-library-oauth2-http" % "0.15.0" // BSD 3-clause
    ) ++ Silencer
  )

  val GooglePubSubGrpcAlpnAgent = "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.7"

  val GoogleFcm = Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "com.pauldijou" %% "jwt-core" % "2.1.0", // ApacheV2
      "org.mockito" % "mockito-core" % mockitoVersion % Test // MIT
    )
  )

  val HBase = {
    val hbaseVersion = "1.2.6.1"
    val hadoopVersion = "2.5.2"
    Seq(
      libraryDependencies ++= Seq(
        // for some reason version 2.2.3U1 started to get picked which was not accepted by Whitesource)
        "com.sun.xml.bind" % "jaxb-impl" % "2.2.3-1", // CDDL + GPLv2
        // TODO: remove direct dependency ^^ when updating from these very old versions
        "org.apache.hbase" % "hbase-shaded-client" % hbaseVersion exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2,
        "org.apache.hbase" % "hbase-common" % hbaseVersion exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2,
        "org.apache.hadoop" % "hadoop-common" % hadoopVersion exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2,
        "org.apache.hadoop" % "hadoop-mapreduce-client-core" % hadoopVersion exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2,
        "org.slf4j" % "log4j-over-slf4j" % "1.7.25" % Test // MIT like: http://www.slf4j.org/license.html
      )
    )
  }

  val HadoopVersion = "3.1.1"
  val Hdfs = Seq(
    libraryDependencies ++= Seq(
      "org.apache.hadoop" % "hadoop-client" % HadoopVersion exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2
      "org.typelevel" %% "cats-core" % "1.6.0", // MIT,
      "org.apache.hadoop" % "hadoop-hdfs" % HadoopVersion % Test classifier "tests" exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2
      "org.apache.hadoop" % "hadoop-common" % HadoopVersion % Test classifier "tests" exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2
      "org.apache.hadoop" % "hadoop-minicluster" % HadoopVersion % Test exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2
      "org.slf4j" % "log4j-over-slf4j" % "1.7.25" % Test // MIT like: http://www.slf4j.org/license.html
    )
  )

  val IronMq = Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "de.heikoseeberger" %% "akka-http-circe" % "1.21.0" // ApacheV2
    )
  )

  val Jms = Seq(
    libraryDependencies ++= Seq(
      "javax.jms" % "jms" % "1.1" % Provided, // CDDL + GPLv2
      "com.ibm.mq" % "com.ibm.mq.allclient" % "9.1.1.0" % Test, // IBM International Program License Agreement https://public.dhe.ibm.com/ibmdl/export/pub/software/websphere/messaging/mqdev/maven/licenses/L-APIG-AZYF2E/LI_en.html
      "org.apache.activemq" % "activemq-broker" % "5.15.4" % Test, // ApacheV2
      "org.apache.activemq" % "activemq-client" % "5.15.4" % Test, // ApacheV2
      "io.github.sullis" %% "jms-testkit" % "0.2.1" % Test, // ApacheV2
      "org.mockito" % "mockito-core" % mockitoVersion % Test // MIT
    ),
    // Having JBoss as a first resolver is a workaround for https://github.com/coursier/coursier/issues/200
    externalResolvers := ("jboss" at "http://repository.jboss.org/nexus/content/groups/public") +: externalResolvers.value
  )

  val JsonStreaming = Seq(
    libraryDependencies ++= Seq(
      "com.github.jsurfer" % "jsurfer" % "1.4.3", // MIT,
      "com.github.jsurfer" % "jsurfer-jackson" % "1.4.3" // MIT
    )
  )

  val Kinesis = Seq(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-kinesis" % AwsSdkVersion, // ApacheV2
      "org.mockito" % "mockito-core" % mockitoVersion % Test // MIT
    )
  )

  val KuduVersion = "1.7.1"
  val Kudu = Seq(
    libraryDependencies ++= Seq(
      "org.apache.kudu" % "kudu-client-tools" % KuduVersion, // ApacheV2
      "org.apache.kudu" % "kudu-client" % KuduVersion % Test // ApacheV2
    )
  )

  val MongoDb = Seq(
    libraryDependencies ++= Seq(
      "org.mongodb" % "mongodb-driver-reactivestreams" % "1.11.0", // ApacheV2
      "org.mongodb.scala" %% "mongo-scala-bson" % "2.4.2" % "test" // ApacheV2
    )
  )

  val Mqtt = Seq(
    libraryDependencies ++= Seq(
      "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.0" // Eclipse Public License 1.0
    )
  )

  val MqttStreaming = Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion, // ApacheV2
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % "test", // ApacheV2
      "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion // ApacheV2
    )
  )

  val OrientDB = Seq(
    libraryDependencies ++= Seq(
      "com.orientechnologies" % "orientdb-graphdb" % "3.0.13", // ApacheV2
      "com.orientechnologies" % "orientdb-object" % "3.0.13" // ApacheV2
    )
  )

  val Reference = Seq(
    // connector specific library dependencies and resolver settings
    libraryDependencies ++= Seq(
      )
  )

  val S3 = Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-xml" % AkkaHttpVersion,
      "com.amazonaws" % "aws-java-sdk-core" % AwsSdkVersion, // ApacheV2
      // in-memory filesystem for file related tests
      "com.google.jimfs" % "jimfs" % "1.1" % Test, // ApacheV2
      "com.github.tomakehurst" % "wiremock" % "2.18.0" % Test // ApacheV2
    )
  )

  val SpringWeb = {
    val SpringVersion = "5.1.4.RELEASE"
    val SpringBootVersion = "2.1.2.RELEASE"
    Seq(
      libraryDependencies ++= Seq(
        "org.springframework" % "spring-core" % SpringVersion,
        "org.springframework" % "spring-context" % SpringVersion,
        "org.springframework.boot" % "spring-boot-autoconfigure" % SpringBootVersion, // TODO should this be provided?
        "org.springframework.boot" % "spring-boot-configuration-processor" % SpringBootVersion % Optional,
        // for examples
        "org.springframework.boot" % "spring-boot-starter-web" % SpringBootVersion % Test
      )
    )
  }

  val SlickVersion = "3.3.0"
  val Slick = Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick" % SlickVersion, // BSD 2-clause "Simplified" License
      "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion, // BSD 2-clause "Simplified" License
      "com.h2database" % "h2" % "1.4.197" % Test // Eclipse Public License 1.0
    )
  )

  val Sns = Seq(
    libraryDependencies ++= Seq(
      "com.github.matsluni" %% "aws-spi-akka-http" % "0.0.5" excludeAll ExclusionRule(
        organization = "com.typesafe.akka"
      ), // ApacheV2
      "software.amazon.awssdk" % "sns" % AwsSdk2Version excludeAll (ExclusionRule(
        organization = "software.amazon.awssdk",
        name = "netty-nio-client"
      ), ExclusionRule(organization = "io.netty")), // ApacheV2
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion, // ApacheV2
      "org.mockito" % "mockito-core" % mockitoVersion % Test // MIT
    )
  )

  val SolrjVersion = "7.4.0"
  val SolrVersionForDocs = "7_4"

  val Solr = {
    val slf4jVersion = "1.7.25"
    Seq(
      libraryDependencies ++= Seq(
        "org.apache.solr" % "solr-solrj" % SolrjVersion, // ApacheV2
        "org.apache.solr" % "solr-test-framework" % SolrjVersion % Test exclude ("org.apache.logging.log4j", "log4j-slf4j-impl"), // ApacheV2
        "org.slf4j" % "log4j-over-slf4j" % slf4jVersion % Test // MIT like: http://www.slf4j.org/license.html
      ),
      resolvers += ("restlet" at "https://maven.restlet.com")
    )
  }

  val Sqs = Seq(
    libraryDependencies ++= Seq(
      "com.github.matsluni" %% "aws-spi-akka-http" % "0.0.5" excludeAll ExclusionRule(
        organization = "com.typesafe.akka"
      ), // ApacheV2
      "software.amazon.awssdk" % "sqs" % AwsSdk2Version excludeAll (ExclusionRule(
        organization = "software.amazon.awssdk",
        name = "netty-nio-client"
      ), ExclusionRule(organization = "io.netty")), // ApacheV2
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion, // ApacheV2
      "org.mockito" % "mockito-core" % mockitoVersion % Test, // MIT
      "org.mockito" % "mockito-inline" % mockitoVersion % Test // MIT
    )
  )

  val Sse = Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test
    )
  )

  val UnixDomainSocket = Seq(
    libraryDependencies ++= Seq(
      "com.github.jnr" % "jffi" % "1.2.17" classifier "complete", // ApacheV2
      "com.github.jnr" % "jnr-unixsocket" % "0.22" // BSD/ApacheV2/CPL/MIT as per https://github.com/akka/alpakka/issues/620#issuecomment-348727265
    )
  )

  val Xml = Seq(
    libraryDependencies ++= Seq(
      "com.fasterxml" % "aalto-xml" % "1.1.0" // ApacheV2
    )
  )

}
