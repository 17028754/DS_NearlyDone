package com.hep88

import akka.actor.Address
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.ClusterEvent.{ReachabilityEvent, ReachableMember, UnreachableMember}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.cluster.typed._
import com.typesafe.config.ConfigFactory
import com.hep88.protocol.JsonSerializable
import scalafx.collections.ObservableHashSet

import scala.collection.mutable.ListBuffer

object ChatServer {
  sealed trait Command extends JsonSerializable
  case class JoinChat(name: String, from: ActorRef[GameClient.Command]) extends Command
  case class Leave(name: String, from: ActorRef[GameClient.Command]) extends Command
  case class JoinGame(name1: String, from1: ActorRef[GameClient.Command], name2: String, from2: ActorRef[GameClient.Command]) extends Command
  case class GameCompleted(name1: String, from1: ActorRef[GameClient.Command], name2: String, from2: ActorRef[GameClient.Command]) extends Command
  private final case class ReachabilityChange(reachabilityEvent: ReachabilityEvent) extends Command
  val ServerKey: ServiceKey[ChatServer.Command] = ServiceKey("ChatServer")
  val members = new ObservableHashSet[User]()

  val unreachables = new ObservableHashSet[Address]()
  unreachables.onChange{(ns, _) =>
    for (unreachable <- ns){
      for(member <- members){
        if(member.ref.path.address == unreachable){
          members-= member
        }
      }
    }
  }

  members.onChange{(ns, _) =>
    for(member <- ns){
      member.ref ! GameClient.MemberList(members.toList)
    }
  }

  var gameRooms = new ListBuffer[User]()



  def apply(): Behavior[ChatServer.Command] = Behaviors.setup { context =>

    context.system.receptionist ! Receptionist.Register(ServerKey, context.self)

    val reachabilityAdapter = context.messageAdapter(ReachabilityChange)
    Cluster(context.system).subscriptions ! Subscribe(reachabilityAdapter, classOf[ReachabilityEvent])

    Upnp.bindPort(context)

    Behaviors.receiveMessage { message =>
      message match {
        // When a client becomes unreachable
        case ReachabilityChange(reachabilityEvent) =>
          reachabilityEvent match {
            case UnreachableMember(member) =>
              unreachables += member.address
              Behaviors.same
            case ReachableMember(member) =>
              unreachables -= member.address
              Behaviors.same
          }

        case JoinChat(name, from) =>
          members += User(name, from)
          from ! GameClient.Joined(members.toList, true)
          Behaviors.same
        case Leave(name, from) =>
          members -= User(name, from)
          // To check for omission during game
          val checkGameList = gameRooms.toList.map(_.toString)
          if (checkGameList.contains(name)){
            val player = User(name, from)
            var getIndex = gameRooms.indexOf(player)
            if (getIndex % 2 == 0){
              getIndex+= 1
            }
            else if (getIndex % 2 == 1){
              getIndex-= 1
            }
            val otherPlayer = gameRooms(getIndex)
            otherPlayer.ref ! GameClient.TerminateGame()
            gameRooms-= (player, otherPlayer)
          }
          Behaviors.same
        case JoinGame(name1, ref1, name2, ref2) =>
          val player1 = User(name1, ref1)
          val player2 = User(name2, ref2)
          gameRooms++= List(player1, player2)
//          println("\n\nDEBUG JOIN GAME: " + gameRooms + "\n\n")
          Behaviors.same
        case GameCompleted(name1, ref1, name2, ref2) =>
          val player1 = User(name1, ref1)
          val player2 = User(name2, ref2)
          gameRooms-= (player1, player2)
//          println("\n\nDEBUG COMPLETED/LEAVE GAME: " + gameRooms + "\n\n")
          Behaviors.same
      }
    }
  }
}

object Server extends App {

  val config = ConfigFactory.load()
  val mainSystem = akka.actor.ActorSystem("HelloSystem", MyConfiguration.askForConfig().withFallback(config)) //classic
  val typedSystem: ActorSystem[Nothing] = mainSystem.toTyped
  val cluster = Cluster(typedSystem)
  cluster.manager ! Join(cluster.selfMember.address)
  AkkaManagement(mainSystem).start()
  //val serviceDiscovery = Discovery(mainSystem).discovery
  ClusterBootstrap(mainSystem).start()
  //val greeterMain: ActorSystem[ChatServer.Command] = ActorSystem(ChatServer(), "HelloSystem")
  mainSystem.spawn(ChatServer(), "ChatServer")
}
