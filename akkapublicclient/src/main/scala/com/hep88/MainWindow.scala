package com.hep88

import scalafxml.core.{FXMLLoader, NoDependencyResolver}

object MainWindow {

  val resource = getClass.getResource("view/MainWindow.fxml")
  val loader = new FXMLLoader(resource, NoDependencyResolver)
  loader.load()
  val roots = loader.getRoot[javafx.scene.layout.BorderPane]
  Client.border.setCenter(roots)
  val control = loader.getController[com.hep88.view.MainWindowController#Controller]()
  control.chatClientRef = Option(Client.userRef)

}

