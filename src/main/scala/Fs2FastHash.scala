import cats.effect.{Concurrent, IO}
import com.twmacinta.util.MD5
import blobstore.{Path, Store}
import cats.effect.concurrent.Ref
import fs2.Chunk

class Fs2FastHash(store: Store[IO])(implicit concurrent: Concurrent[IO]) {
  val md5: MD5 = new MD5()

  def go(file: Path): IO[MD5Result] = {
    store
      .get(file, 8086)
      .chunks
      .broadcastTo(
        md5Update,
        progressBar(Ref.of[IO, Option[Long]](file.size).unsafeRunSync())
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

  def md5Update: fs2.Pipe[IO, Chunk[Byte], Unit] = chunk => {
    chunk.flatMap { bytes =>
      fs2.Stream.eval(IO(md5.Update(bytes.toArray)))
    }
  }

  def progressBar: Ref[IO, Option[Long]] => fs2.Pipe[IO, Chunk[Byte], Unit] =
    fileSize =>
      chunk => {
        for {
          bytes <- chunk
          size <- fs2.Stream.eval(fileSize.get)
          _ <- fs2.Stream.eval(IO(println(s"Got ${bytes.size} out of $size")))
        } yield ()
    }
}
