package example

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.coding.Coders.{Gzip, NoCoding}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

class Webservice(implicit system: ActorSystem) extends Directives {

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
      .actorRef[Protocol.Message](65535, OverflowStrategy.dropTail)
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
      .buffer(65535, OverflowStrategy.dropTail)
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
        Sink.actorRef(
          canvasActor,
          Disconnected(outgoingConnActor),
          (_) => Disconnected(outgoingConnActor) // 接続失敗時には接続解除扱いとする
        )
      ) // CanvasActorに対するConnected/DisconnectedはすべてoutgoingConnActorが仲介している

    Flow.fromSinkAndSource(incoming, outgoing)
  }
}
