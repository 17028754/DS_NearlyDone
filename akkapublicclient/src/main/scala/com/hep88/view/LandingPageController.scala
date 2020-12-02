package com.hep88.view

import akka.actor.typed.ActorRef
import com.hep88.{ClientRef, GameClient, MainWindow}
import scalafx.event.ActionEvent
import scalafx.scene.control.TextField
import scalafxml.core.macros.sfxml

@sfxml
class LandingPageController(private val txtName: TextField){

  var chatClientRef: Option[ActorRef[GameClient.Command]] = None


  def handleJoin(action: ActionEvent): Unit = {
    if(txtName != null) {
      ClientRef.ownName = txtName.text()
      chatClientRef map (_ ! GameClient.StartJoin(txtName.text()))
      MainWindow
    }
  }

}
