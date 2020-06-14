import java.io.File
import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.{Executors, TimeUnit}

import blobstore.Path
import blobstore.fs.FileStore
import cats.effect.{Blocker, ExitCode, IO, IOApp}

import scala.concurrent.ExecutionContext

object Main extends IOApp {
  //MD5 of file.txt is 4da530696e0597b580e65b83afa2015c
  // Docker.dmg md5 is 5bcf301c60482045dfba5fc6524fd00a
  def time[A](counter: Int)(f: => IO[A]): IO[A] = {
    for {
      start <- IO(Instant.now)
      _ <- IO(println(s"Starting computation number $counter..."))
      result <- f
      end <- IO(Instant.now)
      difference <- IO(end.toEpochMilli - start.toEpochMilli)
      _ <- IO(println(TimeUnit.MILLISECONDS.toSeconds(difference)))
    } yield result
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val file = Path("file.txt")

//    val blocker = Blocker.liftExecutionContext(Implicits.global)
//
//      .as(ExitCode.Success)

    val executionContext =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
    val blocker = Blocker.liftExecutionContext(executionContext)
    val localStore = new FileStore[IO](Paths.get(""), blocker)

    import cats.implicits._
    for {
      _ <- (0 to 10).toList.traverse { counter =>
        time(counter)(new Fs2FastHash(localStore).go(file))
          .map { hash =>
            println(s"MD5 is $hash\n\n\n")
          }
      }
      _ <- (0 to 10).toList.traverse { counter =>
        time(counter)(
          FastHash.hash(
            new File("/Users/robertbutacu/pet-projects/fs2-fast-md5/file.txt")
          )(blocker)
        ).flatMap { result =>
          IO(println(s"MD5 outcome is $result"))
        }
      }
    } yield ExitCode.Success
  }
}
