package example

import akka.actor.{Actor, ActorRef}

class ConnectionActor extends Actor {
  var wsOutgoingActor: Option[ActorRef] = None
  def receive = {
    case Connected(a) => wsOutgoingActor = Some(a)
    case msg          => wsOutgoingActor foreach (_ ! msg)
  }
}
