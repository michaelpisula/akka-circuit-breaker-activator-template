name := "circuit-breaker"

version := "1.0"

scalaVersion := "2.10.2"

compileOrder := CompileOrder.JavaThenScala

mainClass in (Compile,run) := Some("Main")

javacOptions ++= Seq("-source", "1.7", "-target", "1.7")

val akkaVersion = "2.3.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-experimental" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
  "junit" % "junit" % "4.11" % "test",
  "com.novocode" % "junit-interface" % "0.10" % "test",
  "ch.qos.logback" % "logback-classic" % "1.0.9"
)

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")