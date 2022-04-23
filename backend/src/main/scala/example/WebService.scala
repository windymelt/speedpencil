package example

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.{Source, Flow, Sink}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
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

    val outgoingConnActor = system.actorOf(Props[ConnectionActor]())

    val outgoing = Source
      .actorRef[Protocol.Message](65535, OverflowStrategy.dropNew)
      .mapMaterializedValue { a =>
        outgoingConnActor ! Connected(
          a
        ) // 外のスコープにあるoutgoingConnActorを介することで接続解除などに対応できるようにする
        canvasActor ! Connected(outgoingConnActor)
        ()
      }
      .map {
        case msg: Protocol.PushBlock => {
          import Protocol._
          import io.circe.syntax._
          TextMessage.Strict(msg.asJson.noSpaces.toString())
        }
        case msg: Protocol.Clear => {
          import Protocol._
          import io.circe.syntax._
          TextMessage.Strict(msg.asJson.noSpaces.toString())
        }
      }

    import io.circe.parser._
    val incoming = Flow[Message]
      .buffer(65535, OverflowStrategy.dropNew)
      .collect {
        case m @ TextMessage.Strict(msg) => {
          import Protocol._
          (decode[PushBlock](msg), decode[Clear](msg), decode[Sync](msg))
        }
      }
      .collect {
        case (Right(pb), _, _)    => pb
        case (_, Right(clear), _) => clear
        case (_, _, Right(sync))  => SyncToActor(outgoingConnActor)
      }
      .to(
        Sink.actorRef(canvasActor, Disconnected(outgoingConnActor))
      ) // CanvasActorに対するConnected/DisconnectedはすべてoutgoingConnActorが仲介している

    Flow.fromSinkAndSource(incoming, outgoing)
  }
}
