enablePlugins(JavaAppPackaging)

organization  := "org.geneontology"

name          := "gaferencer"

version       := "0.4.1"

scalaVersion  := "2.12.11"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

scalacOptions in Test ++= Seq("-Yrangepos")

mainClass in Compile := Some("org.geneontology.gaferencer.Main")

javaOptions += "-Xmx10G"

testFrameworks += new TestFramework("utest.runner.Framework")

libraryDependencies ++= {
    Seq(
      "net.sourceforge.owlapi"     %  "owlapi-distribution"    % "4.5.16",
      "org.phenoscape"             %% "scowl"                  % "1.3.4",
      "org.semanticweb.elk"        %  "elk-owlapi"             % "0.4.3" exclude("org.slf4j", "slf4j-log4j12"),
      "org.obolibrary.robot"       %  "robot-core"             % "1.6.0" exclude("org.slf4j", "slf4j-log4j12"),
      "org.prefixcommons"          %  "curie-util"             % "0.0.2",
      "org.scalaz"                 %% "scalaz-core"            % "7.3.1",
      "com.github.scopt"           %% "scopt"                  % "3.7.1",
      "io.circe"                   %% "circe-core"             % "0.11.2",
      "io.circe"                   %% "circe-generic"          % "0.11.2",
      "com.outr"                   %% "scribe-slf4j"           % "2.7.12",
      "com.lihaoyi"                %% "utest"                  % "0.7.4"     % Test
    )
}
