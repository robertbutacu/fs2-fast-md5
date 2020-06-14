import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.{Executors, TimeUnit}

import blobstore.Path
import blobstore.fs.FileStore
import cats.effect.{Blocker, ExitCode, IO, IOApp}

import scala.concurrent.ExecutionContext

object Main extends IOApp {
  //MD5 of file.txt is 4da530696e0597b580e65b83afa2015c
  def time[A](f: => IO[A]): IO[A] = {
    for {
      start      <- IO(Instant.now)
      _          <- IO(println("Starting computation..."))
      result     <- f
      end        <- IO(Instant.now)
      difference <- IO(end.toEpochMilli - start.toEpochMilli)
      _          <- IO(println(TimeUnit.MILLISECONDS.toSeconds(difference)))
    } yield result
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val file = Path("build.sbt")
//    val blocker = Blocker.liftExecutionContext(Implicits.global)
//    time(
//      FastHash.hash(
//        file
//      )(blocker)
//    ).flatMap { result =>
//        IO(println(s"MD5 outcome is $result"))
//      }
//      .as(ExitCode.Success)

    val executionContext =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
    val blocker = Blocker.liftExecutionContext(executionContext)
    val localStore = new FileStore[IO](Paths.get(""), blocker)

    time(new Fs2FastHash(localStore).go(file)).map {
      hash => println(s"MD5 is $hash")
    }.as(ExitCode.Success)
  }
}
