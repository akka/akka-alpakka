resolvers += "Akka library repository".at("https://repo.akka.io/maven")

addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.7")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.6.1")
// discipline
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.10.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.4")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4")
addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.8.0")
// docs
addSbtPlugin("io.akka" % "sbt-paradox-akka" % "24.10.6")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox-dependencies" % "0.2.4")
addSbtPlugin("com.lightbend.sbt" % "sbt-publish-rsync" % "0.3")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")
addSbtPlugin("com.github.sbt" % "sbt-site-paradox" % "1.5.0")
// Akka gRPC -- sync with version in Dependencies.scala:22
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.5.0")
// templating
addSbtPlugin("com.github.sbt" % "sbt-boilerplate" % "0.7.0")
