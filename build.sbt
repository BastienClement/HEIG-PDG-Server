name := """PDG-Server"""
version := "latest"

scalaVersion := "2.11.8"

lazy val root = (project in file(".")).enablePlugins(PlayScala, DockerPlugin)

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws
)

dockerExposedPorts := Seq(9000)
