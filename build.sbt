enablePlugins(JavaAppPackaging)

organization := "org.geneontology"

name := "gaferencer"

version := "0.5"

scalaVersion := "2.13.11"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

mainClass in Compile := Some("org.geneontology.gaferencer.Main")

javaOptions += "-Xmx10G"

testFrameworks += new TestFramework("utest.runner.Framework")

libraryDependencies ++= {
  Seq(
    "net.sourceforge.owlapi"      % "owlapi-distribution" % "4.5.26",
    "org.phenoscape"             %% "scowl"               % "1.4.1",
    "org.semanticweb.elk"         % "elk-owlapi"          % "0.4.3" exclude ("org.slf4j", "slf4j-log4j12"),
    "org.obolibrary.robot"        % "robot-core"          % "1.7.0" exclude ("org.slf4j", "slf4j-log4j12"),
    "org.prefixcommons"           % "curie-util"          % "0.0.2",
    "org.scalaz"                 %% "scalaz-core"         % "7.3.7",
    "io.circe"                   %% "circe-core"          % "0.14.6",
    "io.circe"                   %% "circe-generic"       % "0.14.6",
    "com.outr"                   %% "scribe-slf4j"        % "3.5.5",
    "com.github.alexarchambault" %% "case-app"            % "2.0.6",
    "com.lihaoyi"                %% "utest"               % "0.7.11" % Test
  )
}
