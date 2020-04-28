import sbt._
import Keys._

object Dependencies {

  val Nightly = sys.env.get("TRAVIS_EVENT_TYPE").contains("cron")

  val Scala211 = "2.11.12"
  val Scala212 = "2.12.10"
  val Scala213 = "2.13.1"
  val ScalaVersions = Seq(Scala212, Scala211, Scala213).filterNot(_ == Scala211 && Nightly)

  val Akka25Version = "2.5.31"
  val Akka26Version = "2.6.4"
  val AkkaVersion = if (Nightly) Akka26Version else Akka25Version
  val AkkaBinaryVersion = if (Nightly) "2.6" else "2.5"

  val InfluxDBJavaVersion = "2.15"

  val AwsSdk2Version = "2.11.3"
  val AwsSpiAkkaHttpVersion = "0.0.8"
  // Sync with plugins.sbt
  val AkkaGrpcBinaryVersion = "0.8"
  val AkkaHttpVersion = "10.1.11"
  val AkkaHttpBinaryVersion = "10.1"
  val mockitoVersion = "3.1.0"

  val CouchbaseVersion = "2.7.13"
  val CouchbaseVersionForDocs = "2.7"

  val JwtCoreVersion = "3.0.1"

  val log4jOverSlf4jVersion = "1.7.30"
  val jclOverSlf4jVersion = "1.7.29"

  // Allows to silence scalac compilation warnings selectively by code block or file path
  // This is only compile time dependency, therefore it does not affect the generated bytecode
  // https://github.com/ghik/silencer
  val Silencer = {
    val Version = "1.6.0"
    Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % Version cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % Version % Provided cross CrossVersion.full
    )
  }

  val Common = Seq(
    // These libraries are added to all modules via the `Common` AutoPlugin
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-stream" % AkkaVersion
      )
  )

  val testkit = Seq(
    libraryDependencies := Seq(
        "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.1",
        "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
        "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
        "ch.qos.logback" % "logback-classic" % "1.2.3", // Eclipse Public License 1.0
        "org.scalatest" %% "scalatest" % "3.1.0", // ApacheV2
        "com.novocode" % "junit-interface" % "0.11", // BSD-style
        "ch.qos.logback" % "logback-classic" % "1.2.3", // Eclipse Public License 1.0
        "junit" % "junit" % "4.13" // Eclipse Public License 1.0
      )
  )

  val Mockito = Seq(
    "org.mockito" % "mockito-core" % mockitoVersion % Test,
    "org.scalatestplus" %% "mockito-1-10" % "3.1.0.0" % Test
  )

  // Releases https://github.com/FasterXML/jackson-databind/releases
  // CVE issues https://github.com/FasterXML/jackson-databind/issues?utf8=%E2%9C%93&q=+label%3ACVE
  // This should align with the version used in Akka 2.6.x
  // https://github.com/akka/akka/blob/master/project/Dependencies.scala#L23
  val JacksonDatabindVersion = "2.10.3"
  val JacksonDatabindDependencies = Seq(
    "com.fasterxml.jackson.core" % "jackson-core" % JacksonDatabindVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % JacksonDatabindVersion
  )

  val Amqp = Seq(
    libraryDependencies ++= Seq(
        "com.rabbitmq" % "amqp-client" % "5.3.0" // APLv2
      ) ++ Mockito
  )

  val AwsLambda = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion, // ApacheV2
        "com.github.matsluni" %% "aws-spi-akka-http" % AwsSpiAkkaHttpVersion excludeAll // ApacheV2
        (
          ExclusionRule(organization = "com.typesafe.akka")
        ),
        "software.amazon.awssdk" % "lambda" % AwsSdk2Version excludeAll // ApacheV2
        (
          ExclusionRule("software.amazon.awssdk", "apache-client"),
          ExclusionRule("software.amazon.awssdk", "netty-nio-client")
        )
      ) ++ JacksonDatabindDependencies
      ++ Mockito
  )

  val AzureStorageQueue = Seq(
    libraryDependencies ++= Seq(
        "com.microsoft.azure" % "azure-storage" % "8.0.0" // ApacheV2
      )
  )

  val CassandraVersionInDocs = "4.0"
  val CassandraDriverVersion = "4.5.1"
  val CassandraDriverVersionInDocs = "4.5"
  // https://github.com/akka/alpakka/issues/2226
  // Performance dropped by ~40% when the driver upgraded to latest netty version
  // override for now https://datastax-oss.atlassian.net/browse/JAVA-2676
  val CassandraOverrideNettyVersion = "4.1.39.Final"

  val Cassandra = Seq(
    libraryDependencies ++= Seq(
        ("com.datastax.oss" % "java-driver-core" % CassandraDriverVersion)
          .exclude("com.github.spotbugs", "spotbugs-annotations")
          .exclude("org.apache.tinkerpop", "*") //https://github.com/akka/alpakka/issues/2200
          .exclude("com.esri.geometry", "esri-geometry-api") //https://github.com/akka/alpakka/issues/2225
          .exclude("io.netty", "netty-handler"), // https://github.com/akka/alpakka/issues/2226
        "io.netty" % "netty-handler" % CassandraOverrideNettyVersion,
        "com.typesafe.akka" %% "akka-discovery" % AkkaVersion % Provided
      ) ++ JacksonDatabindDependencies
  )

  val Couchbase = Seq(
    libraryDependencies ++= Seq(
        "com.couchbase.client" % "java-client" % CouchbaseVersion, // ApacheV2
        "io.reactivex" % "rxjava-reactive-streams" % "1.2.1", //ApacheV2
        "com.typesafe.akka" %% "akka-discovery" % AkkaVersion % Provided, // Apache V2
        "com.typesafe.play" %% "play-json" % "2.7.4" % Test, // Apache V2
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion % Test // Apache V2
      )
  )

  val `Doc-examples` = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
        "com.typesafe.akka" %% "akka-stream-kafka" % "1.1.0",
        "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
        "junit" % "junit" % "4.12" % Test, // Eclipse Public License 1.0
        "org.scalatest" %% "scalatest" % "3.1.0" % Test // ApacheV2
      )
  )

  val DynamoDB = Seq(
    libraryDependencies ++= Seq(
        "com.github.matsluni" %% "aws-spi-akka-http" % AwsSpiAkkaHttpVersion excludeAll // ApacheV2
        (
          ExclusionRule(organization = "com.typesafe.akka")
        ),
        "software.amazon.awssdk" % "dynamodb" % AwsSdk2Version excludeAll // ApacheV2
        (
          ExclusionRule("software.amazon.awssdk", "apache-client"),
          ExclusionRule("software.amazon.awssdk", "netty-nio-client")
        ),
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion // ApacheV2
      ) ++ JacksonDatabindDependencies
  )

  val Elasticsearch = Seq(
    libraryDependencies ++= Seq(
        "org.elasticsearch.client" % "elasticsearch-rest-client" % "6.3.1", // ApacheV2
        "io.spray" %% "spray-json" % "1.3.5", // ApacheV2
        "org.slf4j" % "jcl-over-slf4j" % jclOverSlf4jVersion % Test
      ) ++ JacksonDatabindDependencies
  )

  val File = Seq(
    libraryDependencies ++= Seq(
        "com.google.jimfs" % "jimfs" % "1.1" % Test // ApacheV2
      )
  )

  val AvroParquet = Seq(
    libraryDependencies ++= Seq(
        "org.apache.parquet" % "parquet-avro" % "1.10.0", //Apache2
        "org.apache.hadoop" % "hadoop-client" % "3.2.1" % Test exclude ("log4j", "log4j"), //Apache2
        "org.apache.hadoop" % "hadoop-common" % "3.2.1" % Test exclude ("log4j", "log4j"), //Apache2
        "org.specs2" %% "specs2-core" % "4.8.0" % Test, //MIT like: https://github.com/etorreborre/specs2/blob/master/LICENSE.txt
        "org.slf4j" % "log4j-over-slf4j" % log4jOverSlf4jVersion % Test // MIT like: http://www.slf4j.org/license.html
      )
  )

  val Ftp = Seq(
    libraryDependencies ++= Seq(
        "commons-net" % "commons-net" % "3.6", // ApacheV2
        "com.hierynomus" % "sshj" % "0.27.0" // ApacheV2
      )
  )

  val GeodeVersion = "1.12.0"
  val GeodeVersionForDocs = "112"

  val Geode = Seq(
    libraryDependencies ++=
      Seq("geode-core", "geode-cq")
        .map("org.apache.geode" % _ % GeodeVersion) ++
      Seq(
        "com.chuusai" %% "shapeless" % "2.3.3",
        "org.apache.logging.log4j" % "log4j-to-slf4j" % "2.12.1" % Test
      ) ++ JacksonDatabindDependencies
  )

  val GooglePubSub = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
        "com.pauldijou" %% "jwt-core" % JwtCoreVersion, // ApacheV2
        "com.github.tomakehurst" % "wiremock" % "2.25.1" % Test // ApacheV2
      ) ++ Mockito
  )

  val GooglePubSubGrpc = Seq(
    // see Akka gRPC version in plugins.sbt
    libraryDependencies ++= Seq(
        // https://github.com/googleapis/java-pubsub/tree/master/proto-google-cloud-pubsub-v1/
        "com.google.api.grpc" % "grpc-google-cloud-pubsub-v1" % "1.85.1" % "protobuf-src", // ApacheV2
        "io.grpc" % "grpc-auth" % "1.28.0", // ApacheV2
        "com.google.auth" % "google-auth-library-oauth2-http" % "0.20.0", // BSD 3-clause
        // pull in Akka Discovery for our Akka version
        "com.typesafe.akka" %% "akka-discovery" % AkkaVersion
      ) ++ Silencer
  )

  val GooglePubSubGrpcAlpnAgent = "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9"

  val GoogleFcm = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
        "com.pauldijou" %% "jwt-core" % JwtCoreVersion // ApacheV2
      ) ++ Mockito
  )

  val GoogleStorage = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
        "com.pauldijou" %% "jwt-core" % JwtCoreVersion, //ApacheV2
        "com.github.tomakehurst" % "wiremock" % "2.25.1" % Test // ApacheV2
      ) ++ Mockito
  )

  val HBase = {
    val hbaseVersion = "1.4.9"
    val hadoopVersion = "2.7.4"
    Seq(
      libraryDependencies ++= Seq(
          "org.apache.hbase" % "hbase-shaded-client" % hbaseVersion exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2,
          "org.apache.hbase" % "hbase-common" % hbaseVersion exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2,
          "org.apache.hadoop" % "hadoop-common" % hadoopVersion exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2,
          "org.apache.hadoop" % "hadoop-mapreduce-client-core" % hadoopVersion exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2,
          "org.slf4j" % "log4j-over-slf4j" % log4jOverSlf4jVersion % Test // MIT like: http://www.slf4j.org/license.html
        )
    )
  }

  val HadoopVersion = "3.2.1"
  val Hdfs = Seq(
    libraryDependencies ++= Seq(
        "org.apache.hadoop" % "hadoop-client" % HadoopVersion exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2
        "org.typelevel" %% "cats-core" % "2.0.0", // MIT,
        "org.apache.hadoop" % "hadoop-hdfs" % HadoopVersion % Test exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2
        "org.apache.hadoop" % "hadoop-common" % HadoopVersion % Test exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2
        "org.apache.hadoop" % "hadoop-minicluster" % HadoopVersion % Test exclude ("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"), // ApacheV2
        "org.slf4j" % "log4j-over-slf4j" % log4jOverSlf4jVersion % Test // MIT like: http://www.slf4j.org/license.html
      )
  )

  val InfluxDB = Seq(
    libraryDependencies ++= Seq(
        "org.influxdb" % "influxdb-java" % InfluxDBJavaVersion // MIT
      )
  )

  val IronMq = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "de.heikoseeberger" %% "akka-http-circe" % "1.29.1" // ApacheV2
      )
  )

  val Jms = Seq(
    libraryDependencies ++= Seq(
        "javax.jms" % "jms" % "1.1" % Provided, // CDDL + GPLv2
        "com.ibm.mq" % "com.ibm.mq.allclient" % "9.1.3.0" % Test, // IBM International Program License Agreement https://public.dhe.ibm.com/ibmdl/export/pub/software/websphere/messaging/mqdev/maven/licenses/L-APIG-AZYF2E/LI_en.html
        "org.apache.activemq" % "activemq-broker" % "5.15.9" % Test, // ApacheV2
        "org.apache.activemq" % "activemq-client" % "5.15.9" % Test, // ApacheV2
        "io.github.sullis" %% "jms-testkit" % "0.2.8" % Test // ApacheV2
      ) ++ Mockito,
    // Having JBoss as a first resolver is a workaround for https://github.com/coursier/coursier/issues/200
    externalResolvers := ("jboss" at "https://repository.jboss.org/nexus/content/groups/public") +: externalResolvers.value
  )

  val JsonStreaming = Seq(
    libraryDependencies ++= Seq(
        "com.github.jsurfer" % "jsurfer" % "1.6.0", // MIT,
        "com.github.jsurfer" % "jsurfer-jackson" % "1.6.0" // MIT
      ) ++ JacksonDatabindDependencies
  )

  val Kinesis = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion, // ApacheV2
        "com.github.matsluni" %% "aws-spi-akka-http" % AwsSpiAkkaHttpVersion excludeAll ExclusionRule(
          organization = "com.typesafe.akka"
        )
      ) ++ Seq(
        "software.amazon.awssdk" % "kinesis" % AwsSdk2Version, // ApacheV2
        "software.amazon.awssdk" % "firehose" % AwsSdk2Version, // ApacheV2
        "software.amazon.kinesis" % "amazon-kinesis-client" % "2.2.7" // ApacheV2
      ).map(
        _.excludeAll(
          ExclusionRule("software.amazon.awssdk", "apache-client"),
          ExclusionRule("software.amazon.awssdk", "netty-nio-client")
        )
      ) ++ JacksonDatabindDependencies
      ++ Mockito
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
        "org.mongodb" % "mongodb-driver-reactivestreams" % "1.12.0", // ApacheV2
        "org.mongodb.scala" %% "mongo-scala-bson" % "2.7.0" % "test" // ApacheV2
      )
  )

  val Mqtt = Seq(
    libraryDependencies ++= Seq(
        "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.2" // Eclipse Public License 1.0
      )
  )

  val MqttStreaming = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor-typed" % Akka26Version, // ApacheV2
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % Akka26Version % Test, // ApacheV2
        "com.typesafe.akka" %% "akka-stream-typed" % Akka26Version, // ApacheV2
        "com.typesafe.akka" %% "akka-stream-testkit" % Akka26Version % Test // ApacheV2
      )
  )

  val OrientDB = Seq(
    libraryDependencies ++= Seq(
        "com.orientechnologies" % "orientdb-graphdb" % "3.0.13", // ApacheV2
        "com.orientechnologies" % "orientdb-object" % "3.0.13" // ApacheV2
      )
  )

  val PravegaVersion = "0.7.0"
  val PravegaVersionForDocs = s"v${PravegaVersion}"

  val Pravega = {
    Seq(
      libraryDependencies ++= Seq(
          "io.pravega" % "pravega-client" % PravegaVersion,
          "org.slf4j" % "log4j-over-slf4j" % log4jOverSlf4jVersion % Test // MIT like: http://www.slf4j.org/license.html
        )
    )
  }

  val Reference = Seq(
    // connector specific library dependencies and resolver settings
    libraryDependencies ++= Seq(
        )
  )

  val S3 = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-xml" % AkkaHttpVersion,
        "software.amazon.awssdk" % "auth" % AwsSdk2Version,
        // in-memory filesystem for file related tests
        "com.google.jimfs" % "jimfs" % "1.1" % Test, // ApacheV2
        "com.github.tomakehurst" % "wiremock" % "2.25.1" % Test // ApacheV2
      ) ++ JacksonDatabindDependencies
      ++ Silencer
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

  val SlickVersion = "3.3.2"
  val Slick = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.slick" %% "slick" % SlickVersion, // BSD 2-clause "Simplified" License
        "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion, // BSD 2-clause "Simplified" License
        "com.h2database" % "h2" % "1.4.200" % Test // Eclipse Public License 1.0
      )
  )

  val Sns = Seq(
    libraryDependencies ++= Seq(
        "com.github.matsluni" %% "aws-spi-akka-http" % AwsSpiAkkaHttpVersion excludeAll // ApacheV2
        (
          ExclusionRule(organization = "com.typesafe.akka")
        ),
        "software.amazon.awssdk" % "sns" % AwsSdk2Version excludeAll // ApacheV2
        (
          ExclusionRule("software.amazon.awssdk", "apache-client"),
          ExclusionRule("software.amazon.awssdk", "netty-nio-client")
        ),
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion // ApacheV2
      ) ++ JacksonDatabindDependencies
      ++ Mockito
  )

  val SolrjVersion = "7.7.2"
  val SolrVersionForDocs = "7_7"

  val Solr = Seq(
    libraryDependencies ++= Seq(
        "org.apache.solr" % "solr-solrj" % SolrjVersion, // ApacheV2
        "org.apache.solr" % "solr-test-framework" % SolrjVersion % Test exclude ("org.apache.logging.log4j", "log4j-slf4j-impl"), // ApacheV2
        "org.slf4j" % "log4j-over-slf4j" % log4jOverSlf4jVersion % Test // MIT like: http://www.slf4j.org/license.html
      ),
    resolvers += ("restlet" at "https://maven.restlet.com")
  )

  val Sqs = Seq(
    libraryDependencies ++= Seq(
        "com.github.matsluni" %% "aws-spi-akka-http" % AwsSpiAkkaHttpVersion excludeAll // ApacheV2
        (
          ExclusionRule(organization = "com.typesafe.akka")
        ),
        "software.amazon.awssdk" % "sqs" % AwsSdk2Version excludeAll // ApacheV2
        (
          ExclusionRule("software.amazon.awssdk", "apache-client"),
          ExclusionRule("software.amazon.awssdk", "netty-nio-client")
        ),
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion, // ApacheV2
        "org.mockito" % "mockito-inline" % mockitoVersion % Test // MIT
      ) ++ JacksonDatabindDependencies
      ++ Mockito
  )

  val Sse = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test
      )
  )

  val UnixDomainSocket = Seq(
    libraryDependencies ++= Seq(
        "com.github.jnr" % "jffi" % "1.2.23", // classifier "complete", // Is the classifier needed anymore?
        "com.github.jnr" % "jnr-unixsocket" % "0.28" // BSD/ApacheV2/CPL/MIT as per https://github.com/akka/alpakka/issues/620#issuecomment-348727265
      )
  )

  val Xml = Seq(
    libraryDependencies ++= Seq(
        "com.fasterxml" % "aalto-xml" % "1.2.2" // ApacheV2
      )
  )

}
