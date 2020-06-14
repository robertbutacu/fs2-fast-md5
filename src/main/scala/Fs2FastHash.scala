import java.time.Instant

import cats.effect.{Concurrent, IO}
import com.twmacinta.util.MD5
import blobstore.{Path, Store}
import cats.effect.concurrent.Ref
import fs2.Chunk

class Fs2FastHash(store: Store[IO])(implicit concurrent: Concurrent[IO]) {
  val md5: MD5 = new MD5()

  def go(file: Path): IO[MD5Result] = {
    store
      .get(file, 4 * 1024 * 1024)
      .chunks
      .broadcastTo(
        md5Update.andThen(
          logProgress("hashed")(
            Ref
              .of[IO, (Option[Long], Long, Long)]((file.size, 0L, 0L))
              .unsafeRunSync()
          )
        ),
        logProgress("read")(
          Ref
            .of[IO, (Option[Long], Long, Long)]((file.size, 0L, 0L))
            .unsafeRunSync()
        )
      )
      .compile
      .drain
      .flatMap { _ =>
        IO {
          val result = MD5Result(md5.asHex())
          md5.Init()

          result
        }
      }
  }

  type TotalHashed = Long
  type Total = Option[Long]
  type Accumulated = Long
  type ProgressLogger = (Total, TotalHashed, Accumulated)

  import cats.implicits._
  def md5Update: fs2.Pipe[IO, Chunk[Byte], Chunk[Byte]] = chunk => {
    chunk.flatTap { bytes =>
      fs2.Stream.eval(IO(md5.Update(bytes.toArray)))
    }
  }

  def logProgress(action: String): Ref[IO, ProgressLogger] => fs2.Pipe[IO, Chunk[Byte], Unit] = {
    progressCounter => chunk =>
      {
        for {
          bytes <- chunk
          _ <- updateProgressBar(progressCounter)(bytes.size)
          updatedProgressBar <- fs2.Stream.eval(progressCounter.get)
          (total, newTotalHashed, unadjustedHashed) = updatedProgressBar
          adjustedAccumulated = unadjustedHashed % WHEN_TO_LOG(total.get)
          progressPercentage = newTotalHashed.toDouble / total.get.toDouble * 100.0
          _ <- fs2.Stream.eval(
            if (unadjustedHashed > WHEN_TO_LOG(total.get))
              IO(
                println(
                  s"[$action][${Instant.now}]Made progress to ${progressPercentage.toInt}: $action ${newTotalHashed * Math
                    .pow(10, -6)} MB out of ${total.get * Math.pow(10, -6)} MB"
                )
              )
            else IO.pure(())
          )
          _ <- fs2.Stream.eval(
            progressCounter.set((total, newTotalHashed, adjustedAccumulated))
          )
        } yield ()
      }
  }

  private def updateProgressBar(
    ref: Ref[IO, ProgressLogger]
  )(bytes: Long): fs2.Stream[IO, Unit] = {
    for {
      progress <- fs2.Stream.eval(ref.get)
      (total, totalHashed, accumulated) = progress
      newAccumulated = accumulated + bytes
      newTotalHashed = totalHashed + bytes
      _ <- fs2.Stream.eval(ref.set((total, newTotalHashed, newAccumulated)))
    } yield ()
  }

  private def WHEN_TO_LOG(totalSize: Long): Long = (totalSize / 100.0).toLong
}
