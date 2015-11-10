package my.lab.raft

import akka.actor.{ActorRef, Actor}

trait State

case object Initialization extends State {
  override def toString(): String = "Initialization"
}

case object Leader extends State {
  override def toString(): String = "Leader"
}

case object Follower extends State {
  override def toString(): String = "Follower"
}

case object Candidate extends State {
  override def toString(): String = "Candidate"
}

class Server extends Actor {

  
  def receive: Receive = initialization
  
  def initialization: Receive = {
    case _ =>
  }
}