addSbtPlugin("de.heikoseeberger"                 % "sbt-header"       % "1.6.0")
addSbtPlugin("com.geirsson"                      % "sbt-scalafmt"     % "0.4.10")
addSbtPlugin("com.dwijnand"                      % "sbt-dynver"       % "1.1.1")
addSbtPlugin("com.lightbend.paradox"             % "sbt-paradox"      % "0.2.6")
addSbtPlugin("com.eed3si9n"                      % "sbt-unidoc"       % "0.3.3")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "1.0.0")
addSbtPlugin("me.lessis"                         % "bintray-sbt"      % "0.3.0-8-g6d0c3f8")

resolvers += Resolver.url("2m-sbt-plugin-releases", url("https://dl.bintray.com/2m/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
