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
        md5Update,
        progressBar(
          Ref.of[IO, (Option[Long], Long, Long)]((file.size, 0L, 0L)).unsafeRunSync()
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

  def md5Update: fs2.Pipe[IO, Chunk[Byte], Unit] = chunk => {
    chunk.flatMap { bytes =>
      fs2.Stream.eval(IO(md5.Update(bytes.toArray)))
    }
  }

  def progressBar
    : Ref[IO, ProgressLogger] => fs2.Pipe[IO, Chunk[Byte], Unit] = {
    progressCounter => chunk =>
      {
        for {
          bytes <- chunk
          progress <- fs2.Stream.eval(progressCounter.get)
          (total, totalHashed, accumulated) = progress
          when_to_log = total.get / 20.0
          newAccumulated = accumulated + bytes.size
          newTotalHashed = if (newAccumulated > when_to_log) totalHashed + newAccumulated else totalHashed
          adjustedAccumulated = newAccumulated % when_to_log
          progressPercentage = newTotalHashed.toDouble / total.get.toDouble * 100.0
          _ <- fs2.Stream.eval(
            if (newAccumulated > when_to_log)
              IO(
                println(
                  s"Made progress to $progressPercentage: hashed $newTotalHashed out of $total"
                )
              )
            else IO.pure(())
          )
          _ <- fs2.Stream.eval(
            progressCounter.set(total, newTotalHashed, adjustedAccumulated.toLong)
          )
        } yield ()
      }
  }
}
