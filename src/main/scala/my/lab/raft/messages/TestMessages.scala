package my.lab.raft.messages

import my.lab.raft.LogEntry


object TestMessages {

  trait TestMessage
  
  case class ChangedToFollower(id: Int) extends TestMessage
  case class ChangedToCandidate(id: Int) extends TestMessage
  case class ChangedToLeader(id: Int) extends TestMessage
  
  case class ReceivedFollowerTimeout(id: Int) extends TestMessage
  case class ElectionStarted(id: Int, term: Long) extends TestMessage
  
  // val id - id of the server which recieved RequestVote
  case class RequestVoteReceived(val id: Int, val term: Long, val candidateId: Int, val lastLogIndex: Int, val lastLogTerm: Int, val response: Boolean) extends TestMessage
  
  case class AppendEntriesReceived(val id: Int, val term: Long, val leaderId: Int, val prevLogIndex: Int, val prevLogTerm: Int, val entries: List[LogEntry], val leaderCommit: Int) extends TestMessage
  
}