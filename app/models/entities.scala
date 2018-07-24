package models

case class InboundMessage(to: String, body: String) {
  override def toString: String = to + ":" + body
}

case class OutboundMessage(from: String, body: String) {
  override def toString: String = from + ":" + body
}