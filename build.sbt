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
	filters,
	"org.postgresql" % "postgresql" % "9.4.1212",
	"com.typesafe.play" %% "play-slick" % "2.0.2"
)

dockerExposedPorts := Seq(9000)

fork in run := true
