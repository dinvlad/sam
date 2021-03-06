import sbt._

object Dependencies {
  val akkaV = "2.5.16"
  val akkaHttpV = "10.1.3"
  val jacksonV = "2.9.5"
  val scalaLoggingV = "3.5.0"
  val scalaTestV    = "3.0.5"
  val scalaCheckV    = "1.14.0"

  val workbenchUtilV   = "0.5-6942040"
  val workbenchModelV  = "0.13-7e86fba"
  val workbenchGoogleV = "0.18-6942040"
  val workbenchNotificationsV = "0.1-f2a0020"
  val monocleVersion = "1.5.1-cats"
  val newRelicVersion = "4.6.0"

  val excludeAkkaActor =        ExclusionRule(organization = "com.typesafe.akka", name = "akka-actor_2.12")
  val excludeWorkbenchUtil =    ExclusionRule(organization = "org.broadinstitute.dsde.workbench", name = "workbench-util_2.12")
  val excludeWorkbenchModel =   ExclusionRule(organization = "org.broadinstitute.dsde.workbench", name = "workbench-model_2.12")
  val excludeWorkbenchMetrics = ExclusionRule(organization = "org.broadinstitute.dsde.workbench", name = "workbench-metrics_2.12")
  val excludeWorkbenchGoogle =  ExclusionRule(organization = "org.broadinstitute.dsde.workbench", name = "workbench-google_2.12")

  val newRelic: ModuleID = "com.newrelic.agent.java" % "newrelic-api" % newRelicVersion
  val jacksonAnnotations: ModuleID = "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonV
  val jacksonDatabind: ModuleID =    "com.fasterxml.jackson.core" % "jackson-databind"    % jacksonV
  val jacksonCore: ModuleID =        "com.fasterxml.jackson.core" % "jackson-core"        % jacksonV

  val logbackClassic: ModuleID = "ch.qos.logback"             %  "logback-classic" % "1.2.2"
  val ravenLogback: ModuleID =   "com.getsentry.raven"        %  "raven-logback"   % "7.8.6"
  val scalaLogging: ModuleID =   "com.typesafe.scala-logging" %% "scala-logging"   % scalaLoggingV
  val swaggerUi: ModuleID =      "org.webjars"                %  "swagger-ui"      % "2.2.5"
  val ficus: ModuleID =          "com.iheart"                 %% "ficus"           % "1.4.0"

  val akkaActor: ModuleID =         "com.typesafe.akka"   %%  "akka-actor"           % akkaV
  val akkaContrib: ModuleID =       "com.typesafe.akka"   %%  "akka-contrib"         % akkaV
  val akkaSlf4j: ModuleID =         "com.typesafe.akka"   %%  "akka-slf4j"           % akkaV
  val akkaHttp: ModuleID =          "com.typesafe.akka"   %%  "akka-http"            % akkaHttpV           excludeAll(excludeAkkaActor)
  val akkaHttpSprayJson: ModuleID = "com.typesafe.akka"   %%  "akka-http-spray-json" % akkaHttpV
  val akkaTestKit: ModuleID =       "com.typesafe.akka"   %%  "akka-testkit"         % akkaV     % "test"
  val akkaHttpTestKit: ModuleID =   "com.typesafe.akka"   %%  "akka-http-testkit"    % akkaHttpV % "test"
  val scalaCheck: ModuleID =        "org.scalacheck"      %%  "scalacheck"           % scalaCheckV % "test"

  val googleOAuth2: ModuleID = "com.google.auth" % "google-auth-library-oauth2-http" % "0.9.0"

  val monocle: ModuleID = "com.github.julien-truffaut" %%  "monocle-core"  % monocleVersion
  val monocleMacro: ModuleID = "com.github.julien-truffaut" %%  "monocle-macro" % monocleVersion

  val scalaTest: ModuleID =       "org.scalatest" %% "scalatest"    % scalaTestV % "test"
  val mockito: ModuleID =         "org.mockito"    % "mockito-core" % "2.7.22"   % "test"

  val unboundid: ModuleID = "com.unboundid" % "unboundid-ldapsdk" % "4.0.6"

  // All of workbench-libs pull in Akka; exclude it since we provide our own Akka dependency.
  // workbench-google pulls in workbench-{util, model, metrics}; exclude them so we can control the library versions individually.
  val workbenchUtil: ModuleID =      "org.broadinstitute.dsde.workbench" %% "workbench-util"   % workbenchUtilV
  val workbenchModel: ModuleID =     "org.broadinstitute.dsde.workbench" %% "workbench-model"  % workbenchModelV
  val workbenchGoogle: ModuleID =    "org.broadinstitute.dsde.workbench" %% "workbench-google" % workbenchGoogleV
  val workbenchNotifications: ModuleID =  "org.broadinstitute.dsde.workbench" %% "workbench-notifications" % workbenchNotificationsV excludeAll(excludeWorkbenchGoogle, excludeWorkbenchModel)
  val workbenchGoogleTests: ModuleID =    "org.broadinstitute.dsde.workbench" %% "workbench-google" % workbenchGoogleV % "test" classifier "tests" excludeAll(excludeWorkbenchUtil, excludeWorkbenchModel)

  val rootDependencies = Seq(
    // proactively pull in latest versions of Jackson libs, instead of relying on the versions
    // specified as transitive dependencies, due to OWASP DependencyCheck warnings for earlier versions.
    logbackClassic,
    ravenLogback,
    scalaLogging,
    swaggerUi,
    ficus,

    akkaActor,
    akkaContrib,
    akkaSlf4j,
    akkaHttp,
    akkaHttpSprayJson,
    akkaTestKit,
    akkaHttpTestKit,

    googleOAuth2,

    monocle,
    monocleMacro,
    newRelic,

    scalaTest,
    mockito,
    scalaCheck,

    workbenchUtil,
    workbenchModel,
    workbenchGoogle,
    workbenchNotifications,
    workbenchGoogleTests,

    unboundid
  )
}
