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
  
  //TODO messages to stop leader (or any replica) and to start them again (some flag in Server needed), maybe messages to check state of logs or commited state
  case class Suspend()
  case class Resume()
  
  
}