package example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import scala.util.{Success, Failure}
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App {
  implicit val system = ActorSystem()
  import system.dispatcher

  println("hello, world")

  val config = system.settings.config
  val interface = config.getString("app.interface")
  val port = config.getInt("app.port")

  val service = new Webservice()

  val binding = Http().newServerAt(interface, port).bind(service.route)
  binding.onComplete {
    case Success(binding) =>
      val localAddress = binding.localAddress
      println(s"Server is listening on ${localAddress.getHostName}:${localAddress.getPort}")
    case Failure(e) =>
      println(s"Binding failed with ${e.getMessage}")
      system.terminate()
  }
}

