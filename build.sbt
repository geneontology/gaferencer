enablePlugins(JavaAppPackaging)

organization  := "org.geneontology"

name          := "gaffer"

version       := "0.1-SNAPSHOT"

scalaVersion  := "2.12.6"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

scalacOptions in Test ++= Seq("-Yrangepos")

mainClass in Compile := Some("org.geneontology.gaffer.cli.Main")

javaOptions += "-Xmx10G"

scriptClasspath := Seq("*")

libraryDependencies ++= {
    Seq(
      "net.sourceforge.owlapi"     %  "owlapi-distribution"    % "4.5.4",
      "org.phenoscape"             %% "scowl"                  % "1.3.1",
      "org.semanticweb.elk"        %  "elk-owlapi"             % "0.4.3" exclude("org.slf4j", "slf4j-log4j12"),
      "org.obolibrary.robot"       %  "robot-core"             % "1.1.0" exclude("org.slf4j", "slf4j-log4j12"),
      "com.github.pathikrit"       %% "better-files"           % "3.5.0",
      "com.github.tototoshi"       %% "scala-csv"              % "1.3.5",
      "com.github.scopt"           %% "scopt"                  % "3.7.0",
      "com.typesafe.scala-logging" %% "scala-logging"          % "3.9.0",
//      "ch.qos.logback"             %  "logback-classic"        % "1.2.3",
//      "org.codehaus.groovy"        %  "groovy-all"             % "2.5.2"
    )
}
