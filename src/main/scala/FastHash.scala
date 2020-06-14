import java.io.File

import cats.effect.{Blocker, ContextShift, IO}
import com.twmacinta.util.{MD5 => FastMD5}

object FastHash {
  def hash(file: File)(blocker: Blocker): IO[MD5Result] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(blocker.blockingContext)
    blocker.delay[IO, MD5Result](MD5Result(FastMD5.asHex(FastMD5.getHash(file))))
  }
}

case class MD5Result(value: String)
