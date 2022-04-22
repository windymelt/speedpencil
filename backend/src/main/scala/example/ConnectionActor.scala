package example

import akka.actor.Actor
import akka.actor.ActorRef

class ConnectionActor extends Actor {
  var wsOutgoingActor: Option[ActorRef] = None
  def receive = {
    case Connected(a) => wsOutgoingActor = Some(a)
    case msg          => wsOutgoingActor foreach (_ ! msg)
  }
}
