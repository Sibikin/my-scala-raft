package my.lab.raft.messages


import akka.actor.ActorRef
import my.lab.raft.Command

object ServiceMessages {

  case class Test()  
  case class GetState()
  case class InitializeMembers(val members: Map[Int, ActorRef])
  case class InitializeMembersResponse()
  case class InitialElection()
  case class FollowerTimeout()
  case class CandidateTimeout()
  case class LeaderTimeout()
  
  case class Get(val key: String) extends Command
  // leaderId - in case client sent message not to the leader
  case class GetResult(val v: String, val leaderId: Int)
  case class Update(val key: String, val value: String) extends Command
  // leaderId - in case client sent message not to the leader (boolean will be false)
  case class UpdateResult(val v: Boolean, val leaderId: Int)
  
  //TODO messages to stop servers and to start them again (for testing to emulate failure of servers, after Suspend maybe convert to some new state, where all messages except Resume
  //are discarded), maybe messages to check state of logs or commited state are also needed
  case class Suspend()
  case class Resume()
  
  
}