package com.hep88
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist.{Receptionist}
import akka.cluster.typed._
import com.hep88.protocol.JsonSerializable
import scalafx.collections.ObservableHashSet
import scalafx.application.Platform
import akka.cluster.ClusterEvent.ReachabilityEvent
import akka.cluster.ClusterEvent.ReachableMember
import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.ClusterEvent.MemberEvent
import akka.actor.Address


object GameClient {
  sealed trait Command extends JsonSerializable
  //internal protocol
  case object start extends Command
  case class StartJoin(name: String) extends Command

  final case object FindTheServer extends Command
  private case class ListingResponse(listing: Receptionist.Listing) extends Command
  private final case class MemberChange(event: MemberEvent) extends Command
  private final case class ReachabilityChange(reachabilityEvent: ReachabilityEvent) extends Command

  val unreachables = new ObservableHashSet[Address]()
    unreachables.onChange{(ns, _) =>
        Platform.runLater {
            MainWindow.control.updateList(members.toList.filter(y => ! unreachables.exists (x => x == y.ref.path.address)))
            MainWindow.control.updateSuddenleave(membersInGameRoom.toList.filter(y => ! unreachables.exists(x => x == y.ref.path.address)))
        }
    }

  val members = new ObservableHashSet[User]()
  members.onChange{(ns, _) =>
    Platform.runLater {
        MainWindow.control.updateList(ns.toList)
        MainWindow.control.updateSuddenleave(membersInGameRoom.toList.filter(y => members.exists(x => x == y)))
    }
  }

  val membersInGameRoom = new ObservableHashSet[User]()
  membersInGameRoom.onChange{(ns, _) =>
    Platform.runLater {
      MainWindow.control.updateGameRoom(ns.toList)
    }
  }

//client's interaction protocol
  final case class MemberList(list: Iterable[User]) extends Command
  final case class Joined(list: Iterable[User], result: Boolean) extends Command
  // Help start to check for omission during game
  case class GameOmission(name1: String, target1: ActorRef[GameClient.Command], name2: String, target2: ActorRef[GameClient.Command]) extends Command

  // Handling invitation error test case (e.g. inv player that is already in game)
  final case class SendInvitationCheck(target: ActorRef[GameClient.Command]) extends Command
  final case class ReceiveInvitationCheck(status: Boolean) extends Command

  // Sending and receive invitation
  final case class SendInvitation(target: ActorRef[GameClient.Command], name: String) extends Command
  final case class ReceiveInvitation(name: String, from: ActorRef[GameClient.Command]) extends Command
  // Accept and rejecting invitation
  final case class AcceptInvitation(target: ActorRef[GameClient.Command]) extends Command
  final case class RejectInvitation(target: ActorRef[GameClient.Command]) extends Command
  // Display accepted/rejected invitation
  final case class DisplayInvitationResult(result: Boolean) extends Command
  // Update list to show player's name in the game room
  final case class UpdateGameRoomList(target: ActorRef[GameClient.Command], list: Iterable[User]) extends Command
  final case class ReceiveUpdateGameRoomList(list: Iterable[User]) extends Command
  // Update game room list when a player choose to leave game room
  final case class LeaveGameRoomList(target: ActorRef[GameClient.Command], list: Iterable[User]) extends Command
  final case class ReceiveLeaveGameRoomList(list: Iterable[User]) extends Command
  // Starting game
  final case class StartGame(target: ActorRef[GameClient.Command]) extends Command
  final case class ClientStartR(target: ActorRef[GameClient.Command]) extends Command



  // Pausing game
  final case class OwnPause(target: ActorRef[GameClient.Command]) extends Command
  final case class OpponentPause(target: ActorRef[GameClient.Command]) extends Command
  // Unpause game
  final case class OwnUnpause(target: ActorRef[GameClient.Command]) extends Command
  final case class OpponentUnpause(target: ActorRef[GameClient.Command]) extends Command

  // Game over 1st part
  final case class SendGameOver(target: ActorRef[GameClient.Command], gameOver: Boolean, score: Int) extends Command
  final case class ReceiveGameOver(score: Int, gameOver: Boolean, target: ActorRef[GameClient.Command]) extends Command
  // Game over 2nd part
  final case class SendGameOverLast(target: ActorRef[GameClient.Command], score: Int) extends Command
  final case class ReceiveGameOverLast(score: Int, target: ActorRef[GameClient.Command]) extends Command

  // Update score
  final case class PutScore(target: ActorRef[GameClient.Command], score: String) extends Command
  final case class TakeScore(score: String, target: ActorRef[GameClient.Command]) extends Command

  // Notify client about an omission occurrence during game
  final case class TerminateGame() extends Command

  // Remove omission list in server
  final case class GameCompleted(name1: String, target1: ActorRef[GameClient.Command], name2: String, target2: ActorRef[GameClient.Command]) extends Command

  // Handle when a player quits game by returning to lobby and not closing the game
  final case class SendPlayerQuit(target: ActorRef[GameClient.Command]) extends  Command
  final case class ReceivePlayerQuit() extends  Command

  // Animation for next piece - tell other client (part 1)
  final case class TellNextPiece(target: ActorRef[GameClient.Command], nextPiece: List[List[Array[Int]]]) extends Command
  final case class ReceiveNextPiece(nextPiece: List[List[Array[Int]]]) extends Command

  // Animation for next piece - tell other client (part 2)
  final case class ClearNextPiece(target: ActorRef[GameClient.Command]) extends Command
  final case class ReceiveClearNextPiece() extends Command

  // Animation for board - tell other client
  final case class CurrentBoardPiece(target: ActorRef[GameClient.Command], currentPiece: List[Array[Int]], currentX: Int, currentY: Int) extends Command
  final case class ReceiveCurrentBoardPiece(currentPiece: List[Array[Int]], currentX: Int, currentY: Int) extends Command

  // Animation for board - tell other client refresh board
  final case class RefreshEnemyBoard(target: ActorRef[GameClient.Command], board: Array[Array[Int]]) extends Command
  final case class ReceiveRefreshEnemyBoard(board: Array[Array[Int]]) extends Command

  // Animation for board - tell other client to paint piece to board
  final case class PaintEnemyBoardPiece(target: ActorRef[GameClient.Command], currentPiece: List[Array[Int]], currentX: Int, currentY: Int) extends Command
  final case class ReceiveEnemyBoardPiece(currentPiece: List[Array[Int]], currentX: Int, currentY: Int) extends Command

  // Animation for board - tell other client to clear piece from board
  final case class ClearEnemyBoardPiece(target: ActorRef[GameClient.Command], currentPiece: List[Array[Int]], currentX: Int, currentY: Int, board: Array[Array[Int]]) extends Command
  final case class ReceiveClearEnemyBoardPiece(currentPiece: List[Array[Int]], currentX: Int, currentY: Int, board: Array[Array[Int]]) extends Command


  var defaultBehavior: Option[Behavior[GameClient.Command]] = None
  var remoteOpt: Option[ActorRef[ChatServer.Command]] = None 
  var nameOpt: Option[String] = None

    def messageStarted(): Behavior[GameClient.Command] = Behaviors.receive[GameClient.Command] { (context, message) =>
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

          // Updating the list of players connected in the game lobby
            case MemberList(list: Iterable[User]) =>
              members.clear()
              members ++= list
              Behaviors.same
            // Check if player can be invited
            case SendInvitationCheck(target) =>
              target ! ReceiveInvitationCheck(ClientRef.canBeInvited)
              Behaviors.same
            case ReceiveInvitationCheck(status) =>
              Platform.runLater{
                MainWindow.control.checkPlayerInvitation(status)
              }
              Behaviors.same
            // Sending invitation
            case SendInvitation(target, name) =>
                target ! ReceiveInvitation(name, context.self)
                Behaviors.same
            // Receiving invitation
            case ReceiveInvitation(name, from) =>
                Platform.runLater {
                    MainWindow.control.receiveInvitation(name, from)
                }
                Behaviors.same
            // Accepting invitation
            case AcceptInvitation(target) =>
              target ! DisplayInvitationResult(true)
              Behaviors.same
             // Rejecting invitation
            case RejectInvitation(target) =>
              target ! DisplayInvitationResult(false)
              Behaviors.same
            // Display invitation result
            case DisplayInvitationResult(result) =>
              Platform.runLater{
                MainWindow.control.displayInvitationResult(result)
              }
              Behaviors.same
             // Update game room list when player accept the invitation
            case UpdateGameRoomList(target, list) =>
              target ! ReceiveUpdateGameRoomList(list)
              membersInGameRoom.clear()
              membersInGameRoom ++= list
              Behaviors.same
            case ReceiveUpdateGameRoomList(list) =>
              membersInGameRoom.clear()
              membersInGameRoom ++= list
              Behaviors.same
             // Update game room list when a player leaves the game room
            case LeaveGameRoomList(target, list) =>
              target ! ReceiveLeaveGameRoomList(list)
              membersInGameRoom.clear()
              membersInGameRoom ++= list
            Behaviors.same
            case ReceiveLeaveGameRoomList(list) =>
              membersInGameRoom.clear()
              membersInGameRoom ++= list
              Platform.runLater{
                MainWindow.control.displayLeaveGameRoom()
              }
              Behaviors.same
            // Starting game
            case StartGame(target) =>
              target ! ClientStartR(context.self)
              Platform.runLater(
                MainWindow.control.loadGame()
              )
              Behaviors.same
            // Receive command to start game also
            case ClientStartR(from)=>
              Platform.runLater(
                MainWindow.control.loadGame()
              )
              Behaviors.same



         // Client's behaviour during a game
            // Pause game
            case OwnPause(target) =>
              target ! OpponentPause(context.self)
              Behaviors.same
            case OpponentPause(target) =>
              Platform.runLater{
                  GameBoard.control.pauseFromOther()
              }
            Behaviors.same

            // Unpause game
            case OwnUnpause(target) =>
              target ! OpponentUnpause(context.self)
              Behaviors.same
            case OpponentUnpause(target) =>
              Platform.runLater{
                GameBoard.control.unpauseFromOther()
              }
              Behaviors.same

            // Updating score behaviour
            case PutScore(target, score) =>
              target ! TakeScore(score, context.self)
              Behaviors.same
            case TakeScore(score, target) =>
              Platform.runLater{
                  GameBoard.control.addScore(score)
              }
              Behaviors.same

            // Game over behaviour
            case SendGameOver(target, gameOver, score) =>
            target ! ReceiveGameOver(score, gameOver, target)
            Behaviors.same
            case ReceiveGameOver(score, gameOver, target) =>
            Platform.runLater{
                  GameBoard.control.updateGameStatus(score, gameOver)
            }
            Behaviors.same
            case SendGameOverLast(target, score) =>
              target ! ReceiveGameOverLast(score, target)
              Behaviors.same
            case ReceiveGameOverLast(score, target) =>
              Platform.runLater{
                  GameBoard.control.gameOverFinal(score)
              }
              Behaviors.same

              // Omission during game
            case GameOmission(name1, target1, name2, target2) =>
              remoteOpt.map ( _! ChatServer.JoinGame(name1, target1, name2, target2))
              Behaviors.same
            case TerminateGame() =>
              Platform.runLater{
                  GameBoard.control.omissionOccured()
              }
              Behaviors.same
            case GameCompleted(name1, target1, name2, target2) =>
              remoteOpt.map ( _! ChatServer.GameCompleted(name1, target1, name2, target2))
              Behaviors.same

              // Handle client when a player quits
            case SendPlayerQuit(target) =>
              target ! ReceivePlayerQuit()
              Behaviors.same
            case ReceivePlayerQuit() =>
              Platform.runLater{
                GameBoard.control.playerQuit()
              }
              Behaviors.same

              // Animation - tell next piece
            case TellNextPiece(target, nextPiece)  =>
              target ! ReceiveNextPiece(nextPiece)
              Behaviors.same
            case ReceiveNextPiece(nextPiece) =>
              Platform.runLater{
                GameBoard.control.receiveEnemyNextPiece(nextPiece)
              }
              Behaviors.same
            case ClearNextPiece(target) =>
              target ! ReceiveClearNextPiece()
              Behaviors.same
            case ReceiveClearNextPiece() =>
              Platform.runLater{
                GameBoard.control.clearEnemyNextPiece()
              }
              Behaviors.same

             // Animation - tell current piece on board (first piece painted on the very top)
            case CurrentBoardPiece(target, current, x, y) =>
              target ! ReceiveCurrentBoardPiece(current,x ,y)
              Behaviors.same
            case ReceiveCurrentBoardPiece(current,x ,y) =>
              Platform.runLater{
                GameBoard.control.printEnemyCurrentPiece1(current,x ,y)
              }
              Behaviors.same

              // Animation - tell client to refresh board
            case RefreshEnemyBoard(target, board) =>
              target ! ReceiveRefreshEnemyBoard(board)
              Behaviors.same
            case ReceiveRefreshEnemyBoard(board) =>
              Platform.runLater{
                GameBoard.control.refreshEnemyBoard(board)
              }
              Behaviors.same

              // Animation - tell client to paint piece to board
            case PaintEnemyBoardPiece(target, currentPiece, currentX, currentY) =>
              target ! ReceiveEnemyBoardPiece(currentPiece, currentX, currentY)
              Behaviors.same
            case ReceiveEnemyBoardPiece(currentPiece, x, y) =>
              Platform.runLater{
                GameBoard.control.paintEnemyPiece(currentPiece, x, y)
              }
              Behaviors.same

             // Animation - tell client to clear piece from board
            case ClearEnemyBoardPiece(target, currentPiece, currentX, currentY, board) =>
              target ! ReceiveClearEnemyBoardPiece(currentPiece, currentX, currentY, board)
              Behaviors.same
            case ReceiveClearEnemyBoardPiece(currentPiece, currentX, currentY, board) =>
              Platform.runLater{
                GameBoard.control.clearEnemyPiece(currentPiece, currentX, currentY, board)
              }
              Behaviors.same
        }
    }.receiveSignal {
        case (context, PostStop) =>
            for (name <- nameOpt;
                remote <- remoteOpt){
            remote ! ChatServer.Leave(name, context.self)
            }
            defaultBehavior.getOrElse(Behaviors.same)
    }

    def apply(): Behavior[GameClient.Command] =
        Behaviors.setup { context =>
        // (1) a ServiceKey is a unique identifier for this actor

        Upnp.bindPort(context)
        
          
    val reachabilityAdapter = context.messageAdapter(ReachabilityChange)
    Cluster(context.system).subscriptions ! Subscribe(reachabilityAdapter, classOf[ReachabilityEvent])

       // (2) create an ActorRef that can be thought of as a Receptionist
        // Listing “adapter.” this will be used in the next line of code.
        // the ChatClient.ListingResponse(listing) part of the code tells the
        // Receptionist how to get back in touch with us after we contact
        // it in Step 4 below.
        // also, this line of code is long, so i wrapped it onto two lines
        val listingAdapter: ActorRef[Receptionist.Listing] =
            context.messageAdapter { listing =>
                println(s"listingAdapter:listing: ${listing.toString}")
                GameClient.ListingResponse(listing)
            }
        //(3) send a message to the Receptionist saying that we want
        // to subscribe to events related to ServerHello.ServerKey, which
        // represents the ChatClient actor.
        context.system.receptionist ! Receptionist.Subscribe(ChatServer.ServerKey, listingAdapter)
        //context.actorOf(RemoteRouterConfig(RoundRobinPool(5), addresses).props(Props[ChatClient.TestActorClassic]()), "testA")
        defaultBehavior = Some(Behaviors.receiveMessage { message =>
            message match {
                case GameClient.start =>

                    context.self ! FindTheServer 
                    Behaviors.same
                // (4) send a Find message to the Receptionist, saying
                    // that we want to find any/all listings related to
                    // Mouth.MouthKey, i.e., the Mouth actor.
                case FindTheServer =>
                    println(s"Client Hello: got a FindTheServer message")
                    context.system.receptionist !
                        Receptionist.Find(ChatServer.ServerKey, listingAdapter)

                    Behaviors.same
                    // (5) after Step 4, the Receptionist sends us this
                    // ListingResponse message. the `listings` variable is
                    // a Set of ActorRef of type ServerHello.Command, which
                    // you can interpret as “a set of ServerHello ActorRefs.” for
                    // this example i know that there will be at most one
                    // ServerHello actor, but in other cases there may be more
                    // than one actor in this set.
                case ListingResponse(ChatServer.ServerKey.Listing(listings)) =>
                    val xs: Set[ActorRef[ChatServer.Command]] = listings
                    for (x <- xs) {
                        remoteOpt = Some(x)
                    }
                    Behaviors.same
                case StartJoin(name) =>
                    nameOpt = Option(name)
                    remoteOpt.map ( _ ! ChatServer.JoinChat(name, context.self))
                     Behaviors.same
                case GameClient.Joined(x, result) =>
                    Platform.runLater {
                        MainWindow.control.displayStatus("Joined Server", result)
                    }
                    members.clear()
                    members ++= x
                    messageStarted()
                case _=>
                    Behaviors.unhandled
                
            }
        })
        defaultBehavior.get
    }
}
