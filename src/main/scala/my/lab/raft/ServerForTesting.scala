package my.lab.raft

import akka.actor.ActorRef

class ServerForTesting(override val id: Int, val tester: ActorRef, val setFollowerTimeout: Int) extends Server(id) {

  
  import my.lab.raft.messages.TestMessages._
  
  override val followerTimeout = setFollowerTimeout
  
  override def postAction(s: String) = {
    //println("overriden postAction")
    tester ! s
  }
  
  override def postAction(s: TestMessage) = {
    tester ! s
  }
}