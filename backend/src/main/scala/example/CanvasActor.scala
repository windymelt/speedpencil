package example

import akka.actor.Actor
import shared.Protocol
import akka.actor.ActorRef

class CanvasActor extends Actor {
  val cellSize = shared.Protocol.blockSize * shared.Protocol.blockSize * 4
  def genCell() = Array.fill(cellSize)(255)
  def getCellIdx(n: Int, m: Int): Int = n * bufferHeightCount + m
  val bufferWidthCount = (Protocol.canvasWidth / Protocol.blockSize)
  val bufferHeightCount = (Protocol.canvasHeight / Protocol.blockSize)
  var buffers = Array.fill(bufferWidthCount * bufferHeightCount)(genCell())
  var subscribers = Seq[ActorRef]()
  var checkBuffer = genCell()
  val b64decoder = java.util.Base64.getDecoder()
  val b64encoder = java.util.Base64.getEncoder()
  def receive: PartialFunction[Any,Unit] = {
    case Connected(a) => subscribers ++= Seq(a)
    // case Disconnected(a) => subscribers = subscribers.filterNot(_ == a)
    case Protocol.PushBlock(n, m, block) if n >= 0 && m >= 0 && n < bufferWidthCount && m < bufferHeightCount => {
      val idx = getCellIdx(n, m)
      buffers(idx) = buffers(idx) multiply b64decoder.decode(block).map(java.lang.Byte.toUnsignedInt)
      if (! buffers(idx).isEqual(checkBuffer)) {
        val byteBuffer = buffers(idx).map(_.toByte)
        subscribers foreach { a =>
          a ! Protocol.PushBlock(n, m, b64encoder.encodeToString(byteBuffer))
        }
        buffers(idx).copyToArray(checkBuffer)
      }
    }
    case _ => // ignore
  }
  implicit class ArrayToMultiplication(arr: Array[Int]) {
    var multiBuffer = genCell()
    def multiply(arr2: Array[Int]): Array[Int] = {
      arr.copyToArray(multiBuffer)
      for (i <- 0 until multiBuffer.size) {
        multiBuffer(i) *= arr2(i)
      }
      for (i <- 0 until multiBuffer.size) {
        multiBuffer(i) /= 255
      }
      multiBuffer.clone()
    }
    def isEqual(arr2: Array[Int]): Boolean = {
      if (arr.size != arr2.size) return false
      for (i <- 0 until arr.size) {
        if (arr(i) != arr2(i)) return false
      }
      return true
    }
  }
}

case class Connected(actor: ActorRef)
case class Disconnected(actor: ActorRef)
