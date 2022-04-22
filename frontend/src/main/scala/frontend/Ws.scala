package frontend

import org.scalajs.dom
import org.scalajs.dom.raw._

object Ws {
  def newWebSocket() = new WebSocket(getWebsocketUri(dom.document))
  private def getWebsocketUri(document: Document): String = {
    val wsProtocol =
      if (dom.document.location.protocol == "https:") "wss" else "ws"

    s"$wsProtocol://${dom.document.location.host}/chat"
  }
}
