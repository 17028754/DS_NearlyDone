package com.hep88
import akka.actor.typed.ActorRef

object ClientRef {

  def toOption(value: ActorRef[GameClient.Command]): Option[ActorRef[GameClient.Command]] = {
    Option(value)
  }

  var clientRef: Option[ActorRef[GameClient.Command]] = None
  var clientName: String = ""

  var ownRef: Option[ActorRef[GameClient.Command]] = None
  var ownName: String = ""

  var serverRef: Option[ActorRef[GameClient.Command]] = None

  var listUser: Iterable[User] = None

  var fxmlCounter: Int = 0

}
