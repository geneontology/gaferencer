enablePlugins(JavaAppPackaging)

organization := "org.geneontology"

name := "gaferencer"

version := "0.5"

scalaVersion := "2.13.5"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

scalacOptions in Test ++= Seq("-Yrangepos")

mainClass in Compile := Some("org.geneontology.gaferencer.Main")

javaOptions += "-Xmx10G"

testFrameworks += new TestFramework("utest.runner.Framework")

libraryDependencies ++= {
  Seq(
    "net.sourceforge.owlapi"      % "owlapi-distribution" % "4.5.18",
    "org.phenoscape"             %% "scowl"               % "1.3.4",
    "org.semanticweb.elk"         % "elk-owlapi"          % "0.4.3" exclude ("org.slf4j", "slf4j-log4j12"),
    "org.obolibrary.robot"        % "robot-core"          % "1.7.0" exclude ("org.slf4j", "slf4j-log4j12"),
    "org.prefixcommons"           % "curie-util"          % "0.0.2",
    "org.scalaz"                 %% "scalaz-core"         % "7.3.3",
    "io.circe"                   %% "circe-core"          % "0.14.2",
    "io.circe"                   %% "circe-generic"       % "0.14.2",
    "com.outr"                   %% "scribe-slf4j"        % "3.5.1",
    "com.github.alexarchambault" %% "case-app"            % "2.0.4",
    "com.lihaoyi"                %% "utest"               % "0.7.8" % Test
  )
}
