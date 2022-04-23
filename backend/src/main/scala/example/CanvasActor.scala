package example

import akka.actor.Actor
import shared.Protocol
import akka.actor.{ActorRef, Props}

class CanvasActor extends Actor {
  val cellSize = shared.Protocol.blockSize * shared.Protocol.blockSize * 4
  def genCell() = Array.fill(cellSize)(255)
  def getCellIdx(n: Int, m: Int): Int = n * bufferHeightCount + m
  val bufferWidthCount = (Protocol.canvasWidth / Protocol.blockSize)
  val bufferHeightCount = (Protocol.canvasHeight / Protocol.blockSize)
  var buffers = Array.fill(bufferWidthCount * bufferHeightCount)(genCell())
  var subscribers = Seq[ActorRef]()
  val b64decoder = java.util.Base64.getDecoder()
  val b64encoder = java.util.Base64.getEncoder()
  def receive: PartialFunction[Any, Unit] = {
    case Connected(a) => {
      subscribers ++= Seq(a)
      this.context.system.log.info(s"Connected ${a.path}")
    }
    case Disconnected(a) => {
      subscribers = subscribers.filterNot(_ == a)
      this.context.system.log.info(s"Disconnected ${a.path}")
    }
    case SyncToActor(a) => {
      val syncer = this.context.system.actorOf(Props[SyncerActor]())
      syncer ! a
      for (idx <- 0 until buffers.size) {
        val n = idx / bufferHeightCount
        val m = idx % bufferHeightCount
        if (buffers(idx).exists(_ != 255)) {
          val b64Buf =
            b64encoder.encodeToString(buffers(idx).map(_.toByte))
          syncer ! Protocol.PushBlock(n, m, b64Buf)
        }
      }
      syncer ! SyncerGoAhead
    }
    case Protocol.PushBlock(n, m, block)
        if n >= 0 && m >= 0 && n < bufferWidthCount && m < bufferHeightCount => {
      val idx = getCellIdx(n, m)
      val buf = b64decoder.decode(block)
      (buffers(idx) multiply
        buf.map(java.lang.Byte.toUnsignedInt)) copyToArray buffers(idx)

      val byteBuffer = buffers(idx).map(_.toByte)
      val b64Buf = b64encoder.encodeToString(byteBuffer)
      subscribers foreach { a =>
        a ! Protocol.PushBlock(n, m, b64Buf)
      }
    }
    case Protocol.Clear(_) => {
      this.context.system.log.info("clearing canvas")
      val emptyCell = genCell()
      for (cell <- buffers) {
        emptyCell copyToArray cell
      }
      subscribers foreach { a =>
        a ! Protocol.Clear()
      }
    }
    case _ => // ignore
  }
  var multiBuffer = genCell()
  implicit class ArrayToMultiplication(arr: Array[Int]) {
    def multiply(arr2: Array[Int]): Array[Int] = {
      arr.copyToArray(multiBuffer)
      for (i <- 0 until multiBuffer.size) {
        multiBuffer(i) *= arr2(i)
      }
      for (i <- 0 until multiBuffer.size) {
        multiBuffer(i) /= 255
      }
      multiBuffer
    }
  }
}

case class Connected(actor: ActorRef)
case class Disconnected(actor: ActorRef)
case class SyncToActor(actor: ActorRef)
