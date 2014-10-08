name := "akkabook"

version := "1.0"

scalaVersion := "2.11.2"

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-testkit" % "2.3.6",
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "org.scalatest" %% "scalatest" % "2.2.1"
)
