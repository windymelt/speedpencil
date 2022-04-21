package shared

import io.circe._, io.circe.generic.semiauto._

object Protocol {
  val blockSize = 16
  sealed trait Message {
    val messageType: String
  }
  case class PushBlock(n: Int, m: Int, block: String) extends Message { val messageType = "pushBlock" }

  implicit val PushBlockEncoder: Encoder[PushBlock] = deriveEncoder
  implicit val PushBlockDecoder: Decoder[PushBlock] = deriveDecoder
}
