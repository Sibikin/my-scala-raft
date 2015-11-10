name := "my-raft"
 
version := "1.0"
 
scalaVersion := "2.11.5"
 

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9",
  "junit" % "junit" % "4.11" % "test",
  "org.specs2" %% "specs2" % "2.3.11" % "test",
  "org.scalacheck" %% "scalacheck" % "1.10.1",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test")