package example

import shared.Protocol
import akka.actor.{Actor, ActorRef, Props}
import akka.actor.SupervisorStrategy
import akka.actor.OneForOneStrategy

class CanvasActor extends Actor {
  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy()(_ => SupervisorStrategy.Restart)

  val canvas = new InternalCanvas()
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
      this.context.system.log.info("Preparing for sync...")
      val syncer = this.context.system.actorOf(Props[SyncerActor]())
      syncer ! a
      this.canvas.foreachCell { (n, m, block) =>
        if (block.exists(_ != 255)) {
          syncer ! Protocol.PushBlock(n, m, block2B64Block(block))
        }
      }
      syncer ! SyncerGoAhead
    }
    case Protocol.PushBlock(n, m, block)
        if n >= 0 && m >= 0 && n < InternalCanvas.bufferWidthCount && m < InternalCanvas.bufferHeightCount => {
      val resultBuf = canvas.multiplyBlock(n, m, b64Block2Block(block))
      this.tellToAllSubscribers(
        Protocol.PushBlock(n, m, block2B64Block(resultBuf))
      )
    }
    case Protocol.Clear(_) => {
      this.context.system.log.info("clearing canvas")
      this.canvas.clear()
      this.tellToAllSubscribers(Protocol.Clear())
    }
    case _ => // ignore
  }

  private def tellToAllSubscribers(msg: Any): Unit = {
    this.subscribers foreach (_ ! msg)
  }

  private def b64Block2Block(b64Block: String): Array[Int] = {
    val buf = b64decoder.decode(b64Block)
    buf.map(java.lang.Byte.toUnsignedInt)
  }
  private def block2B64Block(block: Array[Int]): String = {
    val byteBuffer = block.map(_.toByte)
    b64encoder.encodeToString(byteBuffer)
  }
}

case class Connected(actor: ActorRef)
case class Disconnected(actor: ActorRef)
case class SyncToActor(actor: ActorRef)
