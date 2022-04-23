package example

import akka.actor.Actor
import shared.Protocol
import akka.actor.ActorRef

class SyncerActor() extends Actor {
  var sendTo: ActorRef = null
  var messageQueue = collection.mutable.Queue[Protocol.PushBlock]()
  def receive: Receive = {
    case pb: Protocol.PushBlock => {
      // queueing
      messageQueue.enqueue(pb)
    }
    case a: ActorRef => sendTo = a
    case SyncerGoAhead => {
      for (pb <- messageQueue) {
        sendTo ! pb
      }
      this.context.stop(self)
    }
  }
}

case object SyncerGoAhead
