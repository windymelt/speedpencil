package example

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.{Source, Flow, Sink}
import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.actor.Props
import akka.stream.OverflowStrategy
import akka.actor.PoisonPill
import akka.http.scaladsl.coding.NoCoding
import akka.http.scaladsl.coding.Gzip

class Webservice(implicit system: ActorSystem) extends Directives {
  import system.dispatcher

  val canvasActor = system.actorOf(Props[CanvasActor]())

  val route: Route = get {
    pathSingleSlash {
        getFromResource("web/index.html")
      }
  } ~
  path("frontend-launcher.js")(getFromResource("frontend-launcher.js")) ~
  path("frontend-fastopt.js")(getFromResource("frontend-fastopt.js")) ~
  path("chat") {
    encodeResponseWith(NoCoding, Gzip) {
      handleWebSocketMessages(webSocketChatFlow())
    }
  }

  def webSocketChatFlow(): Flow[Message, Message, Any] = {
    import shared.Protocol

    val outgoing = Source.actorRef[Protocol.Message](65535, OverflowStrategy.dropNew).mapMaterializedValue { a =>
      canvasActor ! Connected(a)
      ()
    }
      .map {
        case msg: Protocol.PushBlock => {
          import Protocol._
          import io.circe.syntax._
          TextMessage.Strict(msg.asJson.noSpaces.toString())
        }
      }

    val incoming = Flow[Message]
      .collect {
        case m @ TextMessage.Strict(msg) => {
          import io.circe.parser._
          import Protocol._
          decode[PushBlock](msg)
        }
      }
      .collect {
        case Right(x) => x
      }
      .to(Sink.actorRef(canvasActor, ())) // TODO: proxy as actor to notify death of connection


    Flow.fromSinkAndSource(incoming, outgoing)
  }
}
