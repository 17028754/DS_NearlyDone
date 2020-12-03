package com.hep88.view
import akka.actor.typed.ActorRef
import com.hep88.Client.stage
import com.hep88.ClientRef
import scalafxml.core.macros.sfxml
import scalafx.event.ActionEvent
import scalafx.scene.control.{Alert, Button, ButtonType, Label, ListView}
import com.hep88.{GameClient, Client, GameBoard, User}
import scalafx.collections.ObservableBuffer
import scalafx.Includes._
import scalafx.scene.control.Alert.AlertType

@sfxml
class MainWindowController(private var clientRef: ActorRef[GameClient.Command], private var clientName: String,
                           private val lblStatus: Label, private val listUser: ListView[User], private val joinLobby: Button,
                           private val listGameRoom: ListView[User],
                           private val rule1Label: Label,
                           private val rule2Label: Label,
                           private val rule3Label: Label,
                           private val rule4Label: Label,
                           private val rule5Label: Label) {

    var joinedServer: Boolean = false

    var invGame: Boolean = true

    var playerInvStatus: Boolean = true

    var checkStatus: Boolean = false

    var startGame: Boolean = false

    var chatClientRef: Option[ActorRef[GameClient.Command]] = None

    val receivedText: ObservableBuffer[String] =  new ObservableBuffer[String]()

    val membersInGame = new ObservableBuffer[User]()

    val userName:String = ClientRef.ownName

    joinLobby.text = ("Join Lobby as " + userName)

    showRules()

    if (ClientRef.listUser != None){
      updateList(ClientRef.listUser)
    }

    def showRules(): Unit ={
      rule1Label.text = "This goal of this game is to get as many tetris blocks on the tree branch.\n"
      rule2Label.text = "The tree branch is brown and its located in the middle, lower part of the entire board.\n"
      rule3Label.text = "Avoid placing the tetris blocks at the red box, otherwise the game will be over.\n"
      rule4Label.text = "Special blocks are tetris blocks that are made of only 2 boxes, this blocks can be placed anywhere, including in the red boxes.\n"
      rule5Label.text = "So keep stacking the leaves, plant more tress on ground using the special blocks, and get as high as you can!\n"
    }

    def howToStart(): Unit = {
      new Alert(AlertType.Information) {
        initOwner(stage)
        title = "Information Dialog"
        headerText = "The following are the steps to start the game."
        contentText = "NOTE: Make sure you joined the server by pressing the join server as <enteredName> button at the top\n\n" +
          "1. Invite a player connected in the same lobby to game.\n2. Wait for invited player's response.\n3. If invitation is accepted, you can now start the game."
      }.showAndWait()
    }

    def handleJoin(action: ActionEvent): Unit = {
      if (joinedServer == false) {
          chatClientRef map (_ ! GameClient.StartJoin(userName))
      }else{
        new Alert(AlertType.Warning) {
          initOwner(stage)
          title = "Warning Dialog"
          headerText = "You already joined the server!"
          contentText = "You can join the server once, please check for your name in the list on the left."
        }.showAndWait()
      }
    }

    def displayStatus(text: String, result: Boolean): Unit = {
        lblStatus.text = text
        if(result == true){
          joinedServer = true
        }
    }
  def updateList(x: Iterable[User]): Unit ={
    listUser.items = new ObservableBuffer[User]() ++= x
    ClientRef.listUser = x
  }

  def displayLeaveGameRoom(): Unit = {
    invGame = true
    startGame = false
    new Alert(AlertType.Information) {
      initOwner(stage)
      title = "Information Dialog"
      headerText = "A player has left the game room!"
      contentText = "Please invite a player to start the game after your invitation is accepted."
    }.showAndWait()
  }

  def handleLeaveGameRoom(action: ActionEvent): Unit = {
    if (invGame == true){
      new Alert(AlertType.Warning) {
        initOwner(stage)
        title = "Warning Dialog"
        headerText = "You are not in a game room!"
        contentText = "Leave button only allows you to leave the game room\nthat you have joined after accepting the invitation."
      }.showAndWait()
    }
    else{
      invGame = true
      startGame = false
      membersInGame.clear()
      Client.userRef ! GameClient.LeaveGameRoomList(clientRef, membersInGame.toList)
    }
  }

  def updateSuddenleave(x: Iterable[User]): Unit = {
    listGameRoom.items = new ObservableBuffer[User]() ++= x
    invGame = true
    startGame = false
    membersInGame.clear()
  }


  def checkPlayerInvitation(status: Boolean): Unit = {
    if (status == true){
      checkStatus = true
      new Alert(AlertType.Information) {
        initOwner(stage)
        title = "Warning Dialog"
        headerText = "Player can be invited!"
        contentText = "Please proceed to invite player into game."
      }.showAndWait()
    }else{
      new Alert(AlertType.Warning) {
        initOwner(stage)
        title = "Warning Dialog"
        headerText = "Player you want to invite is already in a game room!"
        contentText = "Please invite another player.\nMax participant per game room: 2"
      }.showAndWait()
    }
  }

  def checkPlayerStatus(actionEvent: ActionEvent): Unit = {
    if (listUser.selectionModel().selectedIndex.value < 0
      || listUser.selectionModel().selectedItem.value.ref == Client.userRef) {

      // warning dialog
      new Alert(AlertType.Warning) {
        initOwner(stage)
        title = "Warning Dialog"
        headerText = "Unable to check player's status!"
        contentText = "Please choose other player's username!"
      }.showAndWait()
    }
    else{
      listUser.selectionModel().selectedItem.value.ref ! GameClient.SendInvitationCheck(Client.userRef)
      clientRef = listUser.selectionModel().selectedItem.value.ref
    }
  }


  def handleCreateGame(actionEvent: ActionEvent): Unit = {
    // Don't let user create/invite another player when user is already in a game room with another player
    if (invGame == false) {
      new Alert(AlertType.Warning) {
        initOwner(stage)
        title = "Warning Dialog"
        headerText = "You are already in a game room!"
        contentText = "Please leave current game room to invite the player you want to play with!\nMax participant per game room: 2"
      }.showAndWait()
    }
    else if (checkStatus == false){
      // warning dialog
      new Alert(AlertType.Warning) {
        initOwner(stage)
        title = "Warning Dialog"
        headerText = "Please check if player can be invited by using Check Status!"
        contentText = "1. Please click on other player's username.\n2. Press Check Status.\n" +
          "3. If player can be invited then you can proceed to invite the player. Otherwise, please choose another player."
      }.showAndWait()
    }else {
      if (listUser.selectionModel().selectedItem.value.ref != clientRef) {

        // warning dialog
        new Alert(AlertType.Warning) {
          initOwner(stage)
          title = "Warning Dialog"
          headerText = "Unable to Invite to Game!"
          contentText = "Please choose the player's username that you have checked the status for!"
        }.showAndWait()
      }
      else {
          // Store information for omission during game, and to notify client in cluster about game invitation
          clientRef = listUser.selectionModel().selectedItem.value.ref
          clientName = listUser.selectionModel().selectedItem.value.name
          // Send invitation to client in cluster
          Client.userRef ! GameClient.SendInvitation(clientRef, userName)
          // Need to notify all the clients, call the function in ChatClient object that updates all the clients about a new game created
          // At the moment, notify one selected client

          // notification dialog
          new Alert(AlertType.Information) {
            initOwner(stage)
            title = "Information Dialog"
            headerText = "Invitation sent!"
            contentText = "Please wait for " + clientName + " to accept or reject your invite, you will be notified accordingly."
          }.showAndWait()

      }
    }
  }

  // Receive invitation
  def receiveInvitation(name: String, actorRef: ActorRef[GameClient.Command]): Unit = {

    val alert = new Alert(AlertType.Confirmation){
      initOwner(stage)
      title = "Confirmation Dialog"
      headerText = "Invitation received from " + name + "!"
      contentText = "Do you want to join the game?"
    }

    // display the confirmation dialog box
    val result = alert.showAndWait()

    // Match the receiver's choice
    result match {
        // Accept invitation
      case Some(ButtonType.OK) => ClientRef.clientRef = ClientRef.toOption(actorRef)
                                  Client.userRef ! GameClient.AcceptInvitation(actorRef)
                                  clientRef = actorRef
                                  clientName = name
                                  invGame = false
                                  ClientRef.canBeInvited = false

        // Reject invitation
      case _ => Client.userRef ! GameClient.RejectInvitation(actorRef)
    }
  }

  def displayInvitationResult(result: Boolean): Unit = {
    if(result == true){
      ClientRef.canBeInvited = false
      invGame = false
      startGame = true
      membersInGame += User(clientName, clientRef)
      membersInGame += User(userName, Client.userRef)
      Client.userRef ! GameClient.UpdateGameRoomList(clientRef, membersInGame.toList)
      new Alert(AlertType.Information){
        initOwner(stage)
        title = "Information Dialog"
        headerText = "Invitation accepted!"
        contentText = "Press Start Game to begin the game."
      }.showAndWait()
    }else{
      checkStatus = false
      new Alert(AlertType.Information){
        initOwner(stage)
        title = "Information Dialog"
        headerText = "Invitation rejected."
        contentText = "Please invite another player to start the game."
      }.showAndWait()
    }
  }

  def updateGameRoom(x: Iterable[User]): Unit ={
    listGameRoom.items = new ObservableBuffer[User]() ++= x
  }


  // Initiate game to launch for both clients
  def startGame(actionEvent: ActionEvent): Unit = {
    if (startGame == false) {
      new Alert(AlertType.Warning) {
        initOwner(stage)
        title = "Warning Dialog"
        headerText = "Unable to Start Game!"
        contentText = "Please invite other player to be eligible to start game!\nNote: Only people who send invitation can start the game."
      }.showAndWait()
    }else{
      checkStatus = false
      chatClientRef map (_ ! GameClient.GameOmission(clientName, clientRef, userName, Client.userRef))
      Client.userRef ! GameClient.StartGame(clientRef)
    }
  }

  // Load the game fxml and store the appropriate information for game omission/communication
  def loadGame(): Unit = {
    // Store opponent client ref in object for game controller to reference
    // Store additional component for game omission in the server
    ClientRef.clientRef = ClientRef.toOption(clientRef)
    ClientRef.clientName = clientName
    ClientRef.ownRef = ClientRef.toOption(Client.userRef)
    ClientRef.ownName = userName
    ClientRef.serverRef = chatClientRef
    if (ClientRef.fxmlCounter == 0){
      ClientRef.fxmlCounter+= 1
      GameBoard
    }
    else{
      GameBoard.test()
    }
  }

}