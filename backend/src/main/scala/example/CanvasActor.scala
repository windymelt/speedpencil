package example

import akka.actor.Actor
import shared.Protocol
import collection.mutable.{Map, Seq}
import akka.actor.ActorRef

class CanvasActor extends Actor {
  val bufferSize = shared.Protocol.blockSize * shared.Protocol.blockSize * 4
  def genCell() = Array.fill(bufferSize)(255)
  var buffers = Map[Int, Map[Int, Array[Int]]]()
  var subscribers = Seq[ActorRef]()
  var checkBuffer = genCell()
  val b64decoder = java.util.Base64.getDecoder()
  val b64encoder = java.util.Base64.getEncoder()
  def receive = {
    case Connected(a) => subscribers ++= Seq(a)
    // case Disconnected(a) => subscribers = subscribers.filterNot(_ == a)
    case Protocol.PushBlock(n, m, block) => {
      if (! buffers.isDefinedAt(n)) {
        buffers(n) = Map()
      }
      if (! buffers(n).isDefinedAt(m)) {
        buffers(n)(m) = genCell()
      }
      buffers(n)(m) = buffers(n)(m) multiply b64decoder.decode(block).map(java.lang.Byte.toUnsignedInt)
      if (! buffers(n)(m).isEqual(checkBuffer)) {
        subscribers foreach { a =>
          val byteBuffer = buffers(n)(m).map(_.toByte)
          a ! Protocol.PushBlock(n, m, b64encoder.encodeToString(byteBuffer))
          buffers(n)(m).copyToArray(checkBuffer)
        }
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
