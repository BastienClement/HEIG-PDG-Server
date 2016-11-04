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
	"mysql" % "mysql-connector-java" % "5.1.40",
	"com.typesafe.play" %% "play-slick" % "2.0.2",
	"org.sangria-graphql" %% "sangria" % "1.0.0-RC2",
	"org.sangria-graphql" %% "sangria-play-json" % "0.3.3"
)

dockerExposedPorts := Seq(9000)
