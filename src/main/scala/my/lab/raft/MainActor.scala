package my.lab.raft

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef


class MainActor extends Actor {
  
  import my.lab.raft.messages.RaftMessages._
  import my.lab.raft.messages.ServiceMessages._
  
  var members = Map.empty[Int, ActorRef]
  var membersSet = Set.empty[ActorRef]
  println("MainActor")
  for(i <- 1 to 3) {
    //println(i)
    val testServer = context.actorOf(Props(new Server(i)), "testServer" + i)
    members += i -> testServer
    membersSet += testServer
  }
  
  for(server <- members.values) {
    //server ! Test
    server ! InitializeMembers(members)
  }
  
  //val testServer = context.actorOf(Props(new Server(1)), "testServer")
  //testServer ! Test
  
  def receive: Receive = {
    case InitializeMembersResponse => {
	    membersSet -= sender
			  if(membersSet.size==0)  {
				  members.last._2 ! InitialElection
			  }
    }    
    case x => {
      println("MainActor received: " + x) 
      //context.stop(self) 
      //context.stop(sender)
      //context.system.shutdown()
    }
    
  }
  
}