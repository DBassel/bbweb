// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers ++= Seq(
  Classpaths.sbtPluginReleases,
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
)

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.12")

addSbtPlugin("com.typesafe.sbt" % "sbt-rjs" % "1.0.7")

addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.0.6")

addSbtPlugin("com.typesafe.sbt" % "sbt-jshint" % "1.0.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.0")

// plugin for Play Framework apps which works with a Chrome Extension to auto-refresh your browser
// when changes are made to the web app
addSbtPlugin("com.jamesward" %% "play-auto-refresh" % "0.0.13")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.4.0")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.1.0")

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.0.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.1")
