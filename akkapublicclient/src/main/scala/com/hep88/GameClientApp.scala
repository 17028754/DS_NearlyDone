package com.hep88
import java.net.URL

import akka.cluster.typed._
import akka.discovery.{Discovery, Lookup, ServiceDiscovery}
import akka.discovery.ServiceDiscovery.Resolved
import akka.actor.typed.{ ActorSystem}
import akka.actor.typed.scaladsl.adapter._
import com.typesafe.config.ConfigFactory
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafxml.core.{FXMLLoader, NoDependencyResolver}
import scalafx.Includes._
import scalafx.scene.image.Image

import scala.concurrent.Future
import scala.concurrent.duration._


object Client extends JFXApp {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  val config = ConfigFactory.load()
  val mainSystem = akka.actor.ActorSystem("HelloSystem", MyConfiguration.askDevConfig().withFallback(config))
  val greeterMain: ActorSystem[Nothing] = mainSystem.toTyped

  val cluster = Cluster(greeterMain)

  val discovery: ServiceDiscovery = Discovery(mainSystem).discovery

  val userRef = mainSystem.spawn(GameClient(), "ChatClient")

  // To join internet
// def joinPublicSeedNode(): Unit = {
//    val lookup: Future[Resolved] =
//     discovery.lookup(Lookup("wm.hep88.com").withPortName("hellosystem").withProtocol("tcp"), 1.second)
//
//    lookup.foreach (x => {
//        val result = x.addresses
//        result map { x =>
//            val address = akka.actor.Address("akka", "HelloSystem", x.host, x.port.get)
//            cluster.manager ! JoinSeedNodes(List(address))
//        }
//    })
// }

  def joinPublicSeedNode(): Unit = {
    // TODO without DNS Server implementation
    val address = akka.actor.Address("akka", "HelloSystem", "42.191.53.17", 25520)
    cluster.manager ! JoinSeedNodes(List(address))
  }

  // To join local network
 def joinLocalSeedNode(): Unit = {
    val address = akka.actor.Address("akka", "HelloSystem", MyConfiguration.localAddress.get.getHostAddress, 2222)
    cluster.manager ! JoinSeedNodes(List(address))
 }
  joinPublicSeedNode()


  val rootResource: URL = getClass.getResource("view/RootLayout.fxml")
  val loader: FXMLLoader = new FXMLLoader(rootResource, NoDependencyResolver)
  loader.load()
  val border = loader.getRoot[javafx.scene.layout.BorderPane]

  stage = new PrimaryStage() {
    title = "Tetris"
    icons += new Image(getClass.getResource("img/favicon.png").toURI.toString)
    scene = new Scene(){
      root = border
    }
  }

  stage.onCloseRequest = handle( {
    mainSystem.terminate
  })

  stage.setResizable(false)
  Landing

}