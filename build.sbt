name := "circuit-breaker"

version := "1.0"

scalaVersion := "2.10.2"

compileOrder := CompileOrder.JavaThenScala

mainClass in (Compile,run) := Some("com.tngtech.akka.Main")

javacOptions ++= Seq("-source", "1.7", "-target", "1.7")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.2",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.2",
  "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.2",
  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
  "junit" % "junit" % "4.11" % "test",
  "com.novocode" % "junit-interface" % "0.10" % "test",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.2",
  "ch.qos.logback" % "logback-classic" % "1.0.9"
)

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")