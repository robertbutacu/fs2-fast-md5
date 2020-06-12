import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

import cats.effect.{Blocker, ExitCode, IO, IOApp}

import scala.concurrent.ExecutionContext.Implicits

object Main extends IOApp {
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
    val blocker = Blocker.liftExecutionContext(Implicits.global)
    time(
      FastHash.hash(
        new File("/Users/robertbutacu/pet-projects/fs2-fast-md5/Docker.dmg")
      )(blocker)
    ).flatMap { result =>
        IO(println(s"MD5 outcome is $result"))
      }
      .as(ExitCode.Success)
  }
}
