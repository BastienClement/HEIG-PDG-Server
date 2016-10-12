name := """PDG-Server"""
version := "latest"

scalaVersion := "2.11.8"
scalacOptions ++= Seq(
	//"-Xlog-implicits",
	"-feature",
	"-deprecation",
	"-Xfatal-warnings",
	"-unchecked",
	"-language:reflectiveCalls",
	"-language:higherKinds"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala, DockerPlugin)

libraryDependencies ++= Seq(
	jdbc,
	cache,
	ws,
	"mysql" % "mysql-connector-java" % "5.1.39",
	"com.typesafe.play" %% "play-slick" % "2.0.0"
)

dockerExposedPorts := Seq(9000)
