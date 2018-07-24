import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

val AKKA_VERSION = "2.5.13"

name := "akka-hub"
version := "0.1"
organization := "org.iot-dsa"

scalaVersion := "2.12.6"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.bintrayRepo("tanukkii007", "maven")
)

scalacOptions ++= Seq(
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-language:_",
  "-target:jvm-1.8",
  "-encoding", "UTF-8",
  "-Xexperimental"
)

// packaging
enablePlugins(DockerPlugin, JavaAppPackaging)
dockerBaseImage := "java:latest"
maintainer := "Vlad Orzhekhovskiy <vlad@uralian.com>"
packageName in Docker := "uralian/akka-hub"
dockerExposedPorts := Seq(9000, 2551)
dockerExposedVolumes := Seq("/opt/docker/conf", "/opt/docker/logs")
dockerUpdateLatest := true

dockerEntrypoint ++= Seq(
  """-Dakka.remote.netty.tcp.hostname="$(eval "echo $AKKA_REMOTING_BIND_HOST")"""",
  """-Dakka.remote.netty.tcp.port="$AKKA_REMOTING_BIND_PORT"""",
  """$(IFS=','; I=0; for NODE in $AKKA_SEED_NODES; do echo "-Dakka.cluster.seed-nodes.$I=akka.tcp://$AKKA_ACTOR_SYSTEM_NAME@$NODE"; I=$(expr $I + 1); done)""",
  """-Dconfig.file="$CONF_FILE""""
)

dockerCommands :=
  dockerCommands.value.flatMap {
    case ExecCmd("ENTRYPOINT", args @ _*) => Seq(Cmd("ENTRYPOINT", args.mkString(" ")))
    case v => Seq(v)
  }

libraryDependencies ++= Seq(
  guice,
  "com.typesafe.akka"       %% "akka-cluster"                    % AKKA_VERSION,
  "com.typesafe.akka"       %% "akka-cluster-metrics"            % AKKA_VERSION,
  "com.typesafe.akka"       %% "akka-cluster-tools"              % AKKA_VERSION,
  "com.typesafe.akka"       %% "akka-cluster-sharding"           % AKKA_VERSION,
  "com.typesafe.akka"       %% "akka-slf4j"                      % AKKA_VERSION,
  "ch.qos.logback"           % "logback-classic"                 % "1.2.3",
  "com.github.TanUkkii007"  %% "akka-cluster-custom-downing"     % "0.0.12",
  "nl.tradecloud"           %% "akka-persistence-mongo"          % "1.0.1",
  "com.typesafe.akka"       %% "akka-testkit"                    % AKKA_VERSION    % "test",
  "org.scalatest"           %% "scalatest"                       % "3.0.4"         % "test",
)
