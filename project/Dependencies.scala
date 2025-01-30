import sbt._
import Keys._

object Dependencies {

  // Java Platform version for JavaDoc creation
  // sync with Java version in .github/workflows/link-validator.yml
  lazy val JavaDocLinkVersion = 17

  val CronBuild = sys.env.get("GITHUB_EVENT_NAME").contains("schedule")

  val Scala213 = "2.13.15" // update even in link-validator.conf
  val Scala3 = "3.3.4"
  val Scala2Versions = Seq(Scala213)
  val ScalaVersions = Dependencies.Scala2Versions :+ Dependencies.Scala3

  val AkkaVersion = "2.10.0"
  val AkkaBinaryVersion = VersionNumber(AkkaVersion).numbers match { case Seq(major, minor, _*) => s"$major.$minor" }

  val InfluxDBJavaVersion = "2.15"

  val AwsSdk2Version = "2.25.16"
  val AwsSpiAkkaHttpVersion = "1.0.1"
  // Sync with plugins.sbt
  val AkkaGrpcBinaryVersion = "2.5"
  // sync ignore prefix in scripts/link-validator.conf#L30
  val AkkaHttpVersion = "10.7.0"
  val AkkaHttpBinaryVersion = VersionNumber(AkkaHttpVersion).numbers match {
    case Seq(major, minor, _*) => s"$major.$minor"
  }
  val AlpakkaKafkaVersion = "7.0.0"
  val ScalaTestVersion = "3.2.19"
  val TestContainersScalaTestVersion = "0.40.3" // pulls Testcontainers 1.16.2
  val mockitoVersion = "5.13.0" // check even https://github.com/scalatest/scalatestplus-mockito/releases
  val hoverflyVersion = "0.14.1"

  val CouchbaseVersion = "2.7.23"
  val CouchbaseVersionForDocs = VersionNumber(CouchbaseVersion).numbers match {
    case Seq(major, minor, _*) => s"$major.$minor"
  }

  // https://github.com/jwt-scala/jwt-scala/releases
  val JwtScalaVersion = "9.4.6"

  // https://github.com/akka/akka/blob/main/project/Dependencies.scala#L16
  val slf4jVersion = "2.0.16"
  val log4jOverSlf4jVersion = slf4jVersion
  val jclOverSlf4jVersion = slf4jVersion

  // https://github.com/akka/akka/blob/main/project/Dependencies.scala#L26
  val LogbackWithSlf4jV1 = "1.5.7"
  val wiremock = ("com.github.tomakehurst" % "wiremock" % "3.0.1" % Test)

  val Common = Seq(
    // These libraries are added to all modules via the `Common` AutoPlugin
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-stream" % AkkaVersion
      )
  )

  val testkit = Seq(
    libraryDependencies := Seq(
        "org.scala-lang.modules" %% "scala-collection-compat" % "2.7.0",
        "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
        "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
        "ch.qos.logback" % "logback-classic" % LogbackWithSlf4jV1,
        "org.scalatest" %% "scalatest" % ScalaTestVersion,
        "com.dimafeng" %% "testcontainers-scala-scalatest" % TestContainersScalaTestVersion,
        "com.novocode" % "junit-interface" % "0.11", // BSD-style
        "junit" % "junit" % "4.13" // Eclipse Public License 1.0
      )
  )

  val Mockito = Seq(
    "org.mockito" % "mockito-core" % mockitoVersion % Test
  )

  // Releases https://github.com/FasterXML/jackson-databind/releases
  // CVE issues https://github.com/FasterXML/jackson-databind/issues?utf8=%E2%9C%93&q=+label%3ACVE
  // This should align with the Jackson minor version used in Akka
  // https://github.com/akka/akka/blob/main/project/Dependencies.scala#L29
  val JacksonVersion = "2.17.3"
  val JacksonDatabindVersion = JacksonVersion
  val JacksonDatabindDependencies = Seq(
    "com.fasterxml.jackson.core" % "jackson-core" % JacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % JacksonDatabindVersion
  )

  val Amqp = Seq(
    libraryDependencies ++= Seq(
        "com.rabbitmq" % "amqp-client" % "5.21.0" // APLv2
      ) ++ Mockito
  )

  val AwsLambda = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-pki" % AkkaVersion,
        "com.github.matsluni" %% "aws-spi-akka-http" % AwsSpiAkkaHttpVersion excludeAll // ApacheV2
        (
          ExclusionRule(organization = "com.typesafe.akka")
        ),
        "software.amazon.awssdk" % "lambda" % AwsSdk2Version excludeAll // ApacheV2
        (
          ExclusionRule("software.amazon.awssdk", "apache-client"),
          ExclusionRule("software.amazon.awssdk", "netty-nio-client")
        )
      ) ++ Mockito
  )

  val AzureStorageQueue = Seq(
    libraryDependencies ++= Seq(
        "com.microsoft.azure" % "azure-storage" % "8.6.6" // ApacheV2
      )
  )

  val AzureStorage = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-xml" % AkkaHttpVersion,
        // for testing authorization signature
        "com.azure" % "azure-storage-common" % "12.26.1" % Test,
        wiremock
      )
  )

  val CassandraVersionInDocs = "4.0"
  val CassandraDriverVersion = "4.17.0"
  val CassandraDriverVersionInDocs = "4.17"

  val Cassandra = Seq(
    libraryDependencies ++= Seq(
        ("com.datastax.oss" % "java-driver-core" % CassandraDriverVersion)
          .exclude("com.github.spotbugs", "spotbugs-annotations")
          .exclude("org.apache.tinkerpop", "*") //https://github.com/akka/alpakka/issues/2200
          .exclude("com.esri.geometry", "esri-geometry-api"), //https://github.com/akka/alpakka/issues/2225
        "com.typesafe.akka" %% "akka-discovery" % AkkaVersion % Provided
      ) ++ JacksonDatabindDependencies // evict Jackson version from "com.datastax.oss" % "java-driver-core"
  )

  val Couchbase = Seq(
    libraryDependencies ++= Seq(
        "com.couchbase.client" % "java-client" % CouchbaseVersion, // ApacheV2
        "io.reactivex" % "rxjava-reactive-streams" % "1.2.1", //ApacheV2
        "com.typesafe.akka" %% "akka-discovery" % AkkaVersion % Provided, // Apache V2
        "com.typesafe.play" %% "play-json" % "2.9.4" % Test, // Apache V2
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion % Test, // Apache V2
        "com.typesafe.akka" %% "akka-pki" % AkkaVersion
      )
  )

  val `Doc-examples` = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
        "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
        "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion % Test,
        "junit" % "junit" % "4.13.2" % Test, // Eclipse Public License 1.0
        "org.scalatest" %% "scalatest" % ScalaTestVersion % Test // ApacheV2
      )
  )

  val DynamoDB = Seq(
    libraryDependencies ++= Seq(
        "com.github.matsluni" %% "aws-spi-akka-http" % AwsSpiAkkaHttpVersion excludeAll // ApacheV2
        (
          ExclusionRule(organization = "com.typesafe.akka")
        ),
        "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
        "software.amazon.awssdk" % "dynamodb" % AwsSdk2Version excludeAll // ApacheV2
        (
          ExclusionRule("software.amazon.awssdk", "apache-client"),
          ExclusionRule("software.amazon.awssdk", "netty-nio-client")
        ),
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-pki" % AkkaVersion
      )
  )

  val Elasticsearch = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-pki" % AkkaVersion,
        "org.slf4j" % "jcl-over-slf4j" % jclOverSlf4jVersion % Test
      ) ++ JacksonDatabindDependencies
  )

  val File = Seq(
    libraryDependencies ++= Seq(
        "com.google.jimfs" % "jimfs" % "1.3.0" % Test // ApacheV2
      )
  )

  val AvroParquet = Seq(
    libraryDependencies ++= Seq(
        "org.apache.parquet" % "parquet-avro" % "1.14.4", //Apache2
        // override the version brought in by parquet-avro to fix CVE-2023-39410
        "org.apache.avro" % "avro" % "1.12.0" //Apache2
      )
  )

  val AvroParquetTests = Seq(
    libraryDependencies ++= Seq(
        "org.apache.hadoop" % "hadoop-client" % "3.3.6" % Test exclude ("log4j", "log4j"), //Apache2
        "org.apache.hadoop" % "hadoop-common" % "3.3.6" % Test exclude ("log4j", "log4j"), //Apache2
        "com.sksamuel.avro4s" %% "avro4s-core" % "4.1.2" % Test,
        "org.scalacheck" %% "scalacheck" % "1.17.1" % Test,
        "org.specs2" %% "specs2-core" % "4.20.9" % Test, //MIT like: https://github.com/etorreborre/specs2/blob/master/LICENSE.txt
        "org.slf4j" % "log4j-over-slf4j" % log4jOverSlf4jVersion % Test // MIT like: http://www.slf4j.org/license.html
      )
  )

  val Ftp = Seq(
    libraryDependencies ++= Seq(
        "commons-net" % "commons-net" % "3.11.1",
        // Versions after sshj 0.35.0 depend on SLF4J 2.x
        "com.hierynomus" % "sshj" % "0.39.0",
        ("io.github.hakky54" % "sslcontext-kickstart-for-pem" % "8.3.7" % Test)
      )
  )

  val GeodeVersion = "1.15.1"
  val GeodeVersionForDocs = "115"

  val Geode = Seq(
    libraryDependencies ++=
      Seq("geode-core", "geode-cq")
        .map("org.apache.geode" % _ % GeodeVersion) ++
      Seq(
        "com.chuusai" %% "shapeless" % "2.3.12",
        "org.apache.logging.log4j" % "log4j-to-slf4j" % "2.17.2" % Test
      ) ++ JacksonDatabindDependencies
  )

  val GoogleAuthVersion = "1.24.1"

  val GoogleCommon = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
        "com.github.jwt-scala" %% "jwt-json-common" % JwtScalaVersion,
        // https://github.com/googleapis/google-auth-library-java
        "com.google.auth" % "google-auth-library-credentials" % GoogleAuthVersion,
        "io.specto" % "hoverfly-java" % hoverflyVersion % Test // ApacheV2
      ) ++ Mockito
  )

  val GoogleBigQuery = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-jackson" % AkkaHttpVersion % Provided,
        "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-pki" % AkkaVersion,
        "io.spray" %% "spray-json" % "1.3.6",
        "com.fasterxml.jackson.core" % "jackson-annotations" % JacksonVersion,
        "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % JacksonVersion % Test, // used from `hoverfly-java`
        "io.specto" % "hoverfly-java" % hoverflyVersion % Test //ApacheV2
      ) ++ Mockito
      ++ JacksonDatabindDependencies // pick possibly later version then `akka-http-jackson`
  )

  val GoogleBigQueryStorage = Seq(
    // see Akka gRPC version in plugins.sbt
    libraryDependencies ++= Seq(
        // https://github.com/googleapis/java-bigquerystorage/tree/master/proto-google-cloud-bigquerystorage-v1
        "com.google.api.grpc" % "proto-google-cloud-bigquerystorage-v1" % "1.23.2" % "protobuf-src", // ApacheV2
        "org.apache.avro" % "avro" % "1.12.0" % "provided",
        "org.apache.arrow" % "arrow-vector" % "13.0.0" % "provided",
        "io.grpc" % "grpc-auth" % akka.grpc.gen.BuildInfo.grpcVersion, // ApacheV2
        "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
        "org.apache.arrow" % "arrow-memory-netty" % "13.0.0" % Test,
        "com.typesafe.akka" %% "akka-discovery" % AkkaVersion
      ) ++ Mockito
  )

  val GooglePubSub = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
        wiremock
      ) ++ Mockito
  )

  val GooglePubSubGrpc = Seq(
    // see Akka gRPC version in plugins.sbt
    libraryDependencies ++= Seq(
        // https://github.com/googleapis/java-pubsub/tree/master/proto-google-cloud-pubsub-v1/
        "com.google.cloud" % "google-cloud-pubsub" % "1.132.4" % "protobuf-src", // ApacheV2
        "io.grpc" % "grpc-auth" % akka.grpc.gen.BuildInfo.grpcVersion,
        // https://github.com/googleapis/google-auth-library-java
        "com.google.auth" % "google-auth-library-oauth2-http" % GoogleAuthVersion,
        // pull in Akka Discovery for our Akka version
        "com.typesafe.akka" %% "akka-discovery" % AkkaVersion
      )
  )

  val GoogleFcm = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion
      ) ++ Mockito
  )

  val GoogleStorage = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
        "io.specto" % "hoverfly-java" % hoverflyVersion % Test // ApacheV2
      ) ++ Mockito
  )

  val HBase = {
    val hbaseVersion = "2.6.1"
    val hadoopVersion = "3.4.1"
    Seq(
      libraryDependencies ++= Seq(
          "org.apache.hbase" % "hbase-shaded-client" % hbaseVersion exclude ("log4j", "log4j"), // ApacheV2,
          "org.apache.hbase" % "hbase-common" % hbaseVersion exclude ("log4j", "log4j"), // ApacheV2,
          "org.apache.hadoop" % "hadoop-common" % hadoopVersion exclude ("log4j", "log4j"), // ApacheV2,
          "org.apache.hadoop" % "hadoop-mapreduce-client-core" % hadoopVersion, // ApacheV2,
          "org.slf4j" % "log4j-over-slf4j" % log4jOverSlf4jVersion % Test // MIT like: http://www.slf4j.org/license.html
        )
    )
  }

  val HadoopVersion = "3.4.1"
  val Hdfs = Seq(
    libraryDependencies ++= Seq(
        "org.apache.hadoop" % "hadoop-client" % HadoopVersion exclude ("log4j", "log4j"), // ApacheV2
        "org.typelevel" %% "cats-core" % "2.12.0", // MIT,
        "org.apache.hadoop" % "hadoop-hdfs" % HadoopVersion % Test, // ApacheV2
        "org.apache.hadoop" % "hadoop-common" % HadoopVersion % Test, // ApacheV2
        "org.apache.hadoop" % "hadoop-minicluster" % HadoopVersion % Test, // ApacheV2
        "org.slf4j" % "log4j-over-slf4j" % log4jOverSlf4jVersion % Test // MIT like: http://www.slf4j.org/license.html
      ) ++ Mockito
  )

  val HuaweiPushKit = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
        "com.github.jwt-scala" %% "jwt-json-common" % JwtScalaVersion
      ) ++ Mockito
  )

  val InfluxDB = Seq(
    libraryDependencies ++= Seq(
        "org.influxdb" % "influxdb-java" % InfluxDBJavaVersion // MIT
      )
  )

  val IronMq = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "de.heikoseeberger" %% "akka-http-circe" % "1.39.2" // ApacheV2
      )
  )

  val JakartaJms = Seq(
    libraryDependencies ++= Seq(
        "jakarta.jms" % "jakarta.jms-api" % "3.1.0", // Eclipse Public License 2.0 + + GPLv2
        ("org.apache.activemq" % "artemis-jakarta-server" % "2.33.0" % Test),
        ("org.apache.activemq" % "artemis-jakarta-client" % "2.33.0" % Test)
      ) ++ Mockito
  )

  val Jms = Seq(
    libraryDependencies ++= Seq(
        "javax.jms" % "jms" % "1.1" % Provided, // CDDL + GPLv2
        "com.ibm.mq" % "com.ibm.mq.allclient" % "9.3.5.1" % Test, // IBM International Program License Agreement https://public.dhe.ibm.com/ibmdl/export/pub/software/websphere/messaging/mqdev/maven/licenses/L-APIG-AZYF2E/LI_en.html
        "org.apache.activemq" % "activemq-broker" % "5.16.7" % Test, // ApacheV2
        "org.apache.activemq" % "activemq-client" % "5.16.7" % Test, // ApacheV2
        "io.github.sullis" %% "jms-testkit" % "1.0.4" % Test // ApacheV2
      ) ++ Mockito,
    // Having JBoss as a first resolver is a workaround for https://github.com/coursier/coursier/issues/200
    externalResolvers := ("jboss" at "https://repository.jboss.org/nexus/content/groups/public") +: externalResolvers.value
  )

  val JsonStreaming = Seq(
    libraryDependencies ++= Seq(
        "com.github.jsurfer" % "jsurfer-jackson" % "1.6.5" // MIT
      ) ++ JacksonDatabindDependencies
  )

  val KinesisProtobufJavaVersion = "3.24.0" // sync with Akka gRPC
  val Kinesis = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.github.matsluni" %% "aws-spi-akka-http" % AwsSpiAkkaHttpVersion excludeAll ExclusionRule(
          organization = "com.typesafe.akka"
        )
      ) ++ Seq(
        "software.amazon.awssdk" % "kinesis" % AwsSdk2Version, // ApacheV2
        "software.amazon.awssdk" % "firehose" % AwsSdk2Version, // ApacheV2
        "software.amazon.kinesis" % "amazon-kinesis-client" % "2.4.0", // ApacheV2
        "com.google.protobuf" % "protobuf-java" % KinesisProtobufJavaVersion // CVE in older transitive dependency
      ).map(
        _.excludeAll(
          ExclusionRule("software.amazon.awssdk", "apache-client"),
          ExclusionRule("software.amazon.awssdk", "netty-nio-client")
        )
      ) ++ Mockito
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
        "org.mongodb.scala" %% "mongo-scala-driver" % "5.3.0" // ApacheV2
      )
  )

  val Mqtt = Seq(
    libraryDependencies ++= Seq(
        "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.5" // Eclipse Public License 1.0
      )
  )

  val MqttStreaming = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
        "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
        "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test
      )
  )

  val OrientDB = Seq(
    libraryDependencies ++= Seq(
        ("com.orientechnologies" % "orientdb-graphdb" % "3.1.9")
          .exclude("com.tinkerpop.blueprints", "blueprints-core"),
        "com.orientechnologies" % "orientdb-object" % "3.1.9" // ApacheV2
      )
  )

  val PravegaVersion = "0.13.0"
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
        "com.google.jimfs" % "jimfs" % "1.3.0" % Test, // ApacheV2
        wiremock
      )
  )

  val SpringWeb = {
    val SpringVersion = "5.3.39"
    val SpringBootVersion = "2.7.18"
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

  val SlickVersion = "3.5.2"
  val Slick = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.slick" %% "slick" % SlickVersion,
        "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
        "com.h2database" % "h2" % "2.3.232" % Test
      )
  )
  val Eventbridge = Seq(
    libraryDependencies ++= Seq(
        "com.github.matsluni" %% "aws-spi-akka-http" % AwsSpiAkkaHttpVersion excludeAll // ApacheV2
        (
          ExclusionRule(organization = "com.typesafe.akka")
        ),
        "software.amazon.awssdk" % "eventbridge" % AwsSdk2Version excludeAll // ApacheV2
        (
          ExclusionRule("software.amazon.awssdk", "apache-client"),
          ExclusionRule("software.amazon.awssdk", "netty-nio-client")
        ),
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-pki" % AkkaVersion,
        "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2"
      ) ++ Mockito
  )

  val Sns = Seq(
    libraryDependencies ++= Seq(
        "com.github.matsluni" %% "aws-spi-akka-http" % AwsSpiAkkaHttpVersion excludeAll // ApacheV2
        (
          ExclusionRule(organization = "com.typesafe.akka")
        ),
        "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
        "software.amazon.awssdk" % "sns" % AwsSdk2Version excludeAll // ApacheV2
        (
          ExclusionRule("software.amazon.awssdk", "apache-client"),
          ExclusionRule("software.amazon.awssdk", "netty-nio-client")
        ),
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion
      ) ++ Mockito
  )

  val SolrjVersion = "8.11.3"
  val SolrVersionForDocs = "8_11"

  val Solr = Seq(
    libraryDependencies ++= Seq(
        "org.apache.solr" % "solr-solrj" % SolrjVersion, // ApacheV2
        "org.apache.solr" % "solr-test-framework" % SolrjVersion % Test, // ApacheV2
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
        "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
        "software.amazon.awssdk" % "sqs" % AwsSdk2Version excludeAll // ApacheV2
        (
          ExclusionRule("software.amazon.awssdk", "apache-client"),
          ExclusionRule("software.amazon.awssdk", "netty-nio-client")
        ),
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion
      ) ++ Mockito
  )

  val Sse = Seq(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test
      )
  )

  val UnixDomainSocket = Seq(
    libraryDependencies ++= Seq(
        "com.github.jnr" % "jffi" % "1.3.13", // classifier "complete", // Is the classifier needed anymore?
        "com.github.jnr" % "jnr-unixsocket" % "0.38.23" // BSD/ApacheV2/CPL/MIT as per https://github.com/akka/alpakka/issues/620#issuecomment-348727265
      )
  )

  val Xml = Seq(
    libraryDependencies ++= Seq(
        "com.fasterxml" % "aalto-xml" % "1.3.3" // ApacheV2
      )
  )

}
