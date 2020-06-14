name := "fs2-fast-md5"

version := "0.1"

scalaVersion := "2.13.2"
resolvers +=
  "jcenter.bintray.com" at "http://jcenter.bintray.com"

//For fast-md5
resolvers += "Spring Plugins" at "https://repo.spring.io/plugins-release"

libraryDependencies ++= Seq(
  "com.github.fs2-blobstore" %% "core" % "0.6.2",
  // available for 2.12, 2.13
  "co.fs2" %% "fs2-core" % "2.4.0", // For cats 2 and cats-effect 2
  "co.fs2" %% "fs2-io" % "2.4.0", // optional I/O library
  "com.twmacinta" % "fast-md5" % "2.7.1"
)
