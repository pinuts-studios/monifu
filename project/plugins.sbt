resolvers ++= Seq(
  Classpaths.sbtPluginReleases,

  Resolver.url("scala-js-releases",
    url("http://dl.bintray.com/content/scala-js/scala-js-releases"))(
      Resolver.ivyStylePatterns)
)

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8.2")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8.5")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.0.1")

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.2")
