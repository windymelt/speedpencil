package shared

import io.circe._, io.circe.generic.semiauto._
import java.util.zip.{Deflater, Inflater}
import java.nio.ByteBuffer

object Protocol {
  val blockSize = 8
  val canvasWidth = 1920
  val canvasHeight = 1080
  sealed trait Message {
    val messageType: String
  }
  case class PushBlock(n: Int, m: Int, block: String) extends Message {
    val messageType = "pushBlock"
  }
  case class Clear(clear: String = "clear") extends Message {
    val messageType = "clear"
  }
  case class Sync(sync: String = "sync") extends Message {
    val messageType = "sync"
  }

  implicit val PushBlockEncoder: Encoder[PushBlock] = deriveEncoder
  implicit val PushBlockDecoder: Decoder[PushBlock] = deriveDecoder
  implicit val ClearEncoder: Encoder[Clear] = deriveEncoder
  implicit val ClearDecoder: Decoder[Clear] = deriveDecoder
  implicit val SyncEncoder: Encoder[Sync] = deriveEncoder
  implicit val SyncDecoder: Decoder[Sync] = deriveDecoder
}
