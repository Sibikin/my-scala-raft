package my.lab.raft.messages

import my.lab.raft.LogEntry


object RaftMessages {

   /** 
   *  <pre>
   *  {@code
   *  val term: Int - leader’s term
   *  val leaderId: Int - so follower can redirect clients
   *  val prevLogIndex: Int - index of log entry immediately preceding new ones
   *  val prevLogTerm: Int - term of prevLogIndex entry
   *  val entries: List[LogEntry] - log entries to store (empty for heartbeat; may send more than one for efficiency)
   *  val leaderCommit: Int - leader’s commitIndex
   * }
   * </pre>
   */
  case class AppendEntries(val term: Long, val leaderId: Int, val prevLogIndex: Int, val prevLogTerm: Int, val entries: List[LogEntry], val leaderCommit: Int)
  
  /** 
   *  <pre>
   *  {@code
   *  val term: Int - currentTerm, for leader to update itself
   *  val success: Boolean - true if follower contained entry matching prevLogIndex and prevLogTerm
   *  }
   * </pre>
   */
  case class AppendEntriesResponse(val term: Long, val success: Boolean)
  
    /** 
   *  <pre>
   *  {@code
   *  val term: Int - candidate’s term
   *  val candidateId: Int - candidate requesting vote
   *  val lastLogIndex: Int - index of candidate’s last log entry
   *  val lastLogTerm: Int - term of candidate’s last log entry
   *     1. Reply false if term < currentTerm (§5.1)
   *     2. If votedFor is null or candidateId, and candidate’s log is at
            least as up-to-date as receiver’s log, grant vote (§5.2, §5.4)
       
   *  }
   * </pre>
   */
  case class RequestVote(val term: Long, val candidateId: Int, val lastLogIndex: Int, val lastLogTerm: Int)
  
    /** 
   *  <pre>
   *  {@code
   *  val term: Int - currentTerm, for candidate to update itself
   *  val voteGranted: Boolean - true means candidate received vote
   *  }
   * </pre>
   */
  case class RequestVoteResponse(val term: Long, val voteGranted: Boolean)
}