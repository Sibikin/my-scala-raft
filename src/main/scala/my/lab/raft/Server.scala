package my.lab.raft

import akka.actor.{ActorRef, Actor}
import akka.actor.Cancellable
import scala.concurrent.duration._
import scala.util.Random

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


class Server(val id: Int) extends Actor {
  import my.lab.raft.messages.RaftMessages._
  import my.lab.raft.messages.ServiceMessages._
  import my.lab.raft.messages.TestMessages._
  import context.dispatcher
  
  var members = Map.empty[Int, ActorRef]
  // majority is used during election and confirmation of event logging, but question, what if some of
  //servers fail, should majority change (and how to change it, because servers can restart silently)
  var majority: Int = 0
  // number of votes in election for candidate
  var numOfVotes = 0
  var state: State = Initialization

  
  /**
   * Persistent state on all servers:
   */
  //latest term server has seen (initialized to 0 on first boot, increases monotonically)
  var currentTerm = 0L                
  //candidateId that received vote in current term (or null if none) 
  var votedFor: Int = 0    
  //log entries; each entry contains command for state machine, and term when entry was received by leader (first index is 1)
  var log = Vector.empty[LogEntry]  // maybe var log = List.empty[LogEntry]
  //map itself, which stores data (maybe it should be placed in some other object)
  var map = Map.empty[String,  String]
  
  /**
   * Volatile state on all servers:
   */
  //index of highest log entry known to be committed (initialized to 0, increases monotonically)
  var commitIndex: Int = 0
  //index of highest log entry applied to state machine (initialized to 0, increases monotonically)
  var lastApplied: Int = 0
  
  /**
   * Volatile state on leaders: (Reinitialized after election)
   */
  //for each server, index of the next log entry to send to that server (initialized to leader last log index + 1)
  var nextIndex = Vector.empty[Int]
  //for each server, index of highest log entry known to be replicated on server (initialized to 0, increases monotonically)
  var matchIndex = Vector.empty[Int]
 
  
  var followerCancell: Cancellable = null
  var candidateCancell: Cancellable = null
  var leaderCancell: Cancellable = null
  val candidateTimeoutRandom = new Random
  val candidateTimeoutFixed = 100
  val followerTimeout = 500
  val heartbeatTimeout = 300
  
  println("server " + id + " is started")
  def receive: Receive = initialization
  
  
  def initialization: Receive = {
    case Test => println("Test message for server " + id)
    case GetState => sender ! state.toString
    case InitializeMembers(m) => {
      println("InitializeMembers in " + id + ". Members size=" + m.size)
      members = m
      val membersSize = m.size
      if(membersSize % 2 == 0) {
        majority = membersSize / 2 + 1
      } else {
        majority = (membersSize + 1) / 2
      }
      sender ! InitializeMembersResponse
      convertToFollower
      //postAction("Initialization completed")
    }
  }
  
  
  def leader: Receive = {
    case GetState => sender ! state.toString
    case RequestVote(requesterTerm, candidateId, lastLogIndex, lastLogTerm) => { //maybe check requesterTerm > currentTerm ?
      cancell(leaderCancell) // stoping heartbeat
      convertToFollower
      processRequestVote(RequestVote(requesterTerm, candidateId, lastLogIndex, lastLogTerm), sender)
    }
    case LeaderTimeout => {
      println("LeaderTimeout - sending heartbeats")
      // instead of scheduleOnce and recreating Cancellable - using schedule with no recreation
      //cancell(leaderCancell)
      //leaderCancell = context.system.scheduler.scheduleOnce(heartbeatTimeout.millisecond)(self ! LeaderTimeout)
      members.foreach(entry => if(entry._1!=id) entry._2 ! AppendEntries(currentTerm, id, 0, 0, List.empty[LogEntry], 0))
    }
    case Get(key) =>
    case Update(key, value) =>
    case Suspend =>
    case _ =>
  }

  
  def candidate: Receive = {
    case GetState => sender ! state.toString
    
    /* let's try without initial election, at start all nodes are in follower state and after timeout start election (but
     * there can be problems with too many elections at once, check it)
    case InitialElection => {
      
    }
    */
    case RequestVoteResponse(responseTerm, voteGranted) => {
      //If RPC request or response contains term T > currentTerm: set currentTerm = T, convert to follower (§5.1) - Rules for Servers. All Servers
      if(responseTerm > currentTerm) {
        currentTerm = responseTerm
        println("candidate " + id + " received greater term and converting to follower")
        cancell(candidateCancell)
        convertToFollower
      } else {
        if(voteGranted) {
          numOfVotes += 1
          //If votes received from majority of servers: become leader
          if(numOfVotes >= majority) {
            cancell(candidateCancell)
            convertToLeader            
          }
        }
      }
    }
    //If AppendEntries RPC received from new leader: convert to follower (Rules for Servers. Candidates (§5.2))
    case AppendEntries(term,  leaderId,  prevLogIndex,  prevLogTerm,  entries,  leaderCommit) => {
      if(term >= currentTerm) {
        currentTerm = term
        votedFor = leaderId
        println("candidate " + id + " received AppendEntries from another leader and is converting to follower")
        cancell(candidateCancell)
        postAction(AppendEntriesReceived(id, term, leaderId,  prevLogIndex,  prevLogTerm,  entries,  leaderCommit ))
        convertToFollower
        //TODO there must be AppendEntriesResponse
      }
    }
    
    case RequestVote(requesterTerm, candidateId, lastLogIndex, lastLogTerm) => {
      processRequestVote(RequestVote(requesterTerm, candidateId, lastLogIndex, lastLogTerm), sender)
    }
    
    case CandidateTimeout => {
      cancell(candidateCancell)
      candidateCancell = context.system.scheduler.scheduleOnce((candidateTimeoutFixed + candidateTimeoutRandom.nextInt(50)).millisecond)(self ! CandidateTimeout)
      startElection
    }
    case Get(key) =>
    case Update(key, value) =>
    case Suspend =>
    case _ =>
  }
  
  
  def follower: Receive = {
    case GetState => sender ! state.toString
    case FollowerTimeout => { 
      println("Follower timeout in " + id)
      cancell(followerCancell)
      postAction(ReceivedFollowerTimeout(id))
      convertToCandidate
    }
    //TODO i think follower should be converted to candidate (at least if it responds with a false) so it can be elected too
    case RequestVote(requesterTerm, candidateId, lastLogIndex, lastLogTerm) => {
      //i think we need to reset timeout when receiving RequestVotes (because leader has fallen)
      cancell(followerCancell)
      followerCancell = context.system.scheduler.scheduleOnce(followerTimeout.millisecond)(self ! FollowerTimeout)
      processRequestVote(RequestVote(requesterTerm, candidateId, lastLogIndex, lastLogTerm), sender)
    } 
    case AppendEntries(term,  leaderId,  prevLogIndex,  prevLogTerm,  entries,  leaderCommit) => {
      cancell(followerCancell)
      followerCancell = context.system.scheduler.scheduleOnce(followerTimeout.millisecond)(self ! FollowerTimeout)
      if(term >= currentTerm) {
        currentTerm = term
      }
      if(votedFor!=leaderId) { //is it possible to get such situation not during election (i think it's not) and what to do if it happens
        println("AppendEntries in follower " + id + " from new leader " + leaderId)
        votedFor = leaderId
      }
      postAction(AppendEntriesReceived(id, term, leaderId,  prevLogIndex,  prevLogTerm,  entries,  leaderCommit ))
      //TODO there must be AppendEntriesResponse
    }
    case Suspend => {
      println("suspending " + id)
      cancell(followerCancell)
      context.become(suspend)
    }
    case Get(key) => sender ! GetResult(null, votedFor)
    case Update(key, value) => sender ! UpdateResult(false, votedFor)
    case _ => {
      cancell(followerCancell)
      followerCancell = context.system.scheduler.scheduleOnce(followerTimeout.millisecond)(self ! FollowerTimeout)
      
    }
  }
  
  
  def suspend(): Receive = {
    case Resume =>
    case _ =>
  }
  
  
  def convertToFollower = {
    println(id + " is converted to follower")
    //postAction(ChangedToFollower(id))
    followerCancell = context.system.scheduler.scheduleOnce(followerTimeout.millisecond)(self ! FollowerTimeout)
    state = Follower
    context.become(follower)
    //postAction(id + " is converted to follower")
    postAction(ChangedToFollower(id))
  }
  
  
  def convertToCandidate = {
    println(id + " is converted to candidate")
    context.become(candidate)
    postAction(ChangedToCandidate(id))
    state = Candidate
    startElection
    candidateCancell = context.system.scheduler.scheduleOnce((candidateTimeoutFixed + candidateTimeoutRandom.nextInt(50)).millisecond)(self ! CandidateTimeout)    
  }
  
  /**
   • Upon election: send initial empty AppendEntries RPCs (heartbeat) to each server; repeat during idle periods to
      prevent election timeouts (§5.2)
   */
  def convertToLeader = {
    println(id + " becomes a new leader")
    context.become(leader)
    postAction(ChangedToLeader(id))
    state = Leader
    members.foreach(entry => if(entry._1!=id) entry._2 ! AppendEntries(currentTerm, id, 0, 0, List.empty[LogEntry], 0))  //TODO maybe real values in place of 0?
    // instead of scheduleOnce and recreating Cancellable - using schedule with no recreation (but
    // if there are a lot of client requests and leader needs to sent AppendEntries frequently maybe it's better
    // to use scheduleOnce)
    //leaderCancell = context.system.scheduler.scheduleOnce(heartbeatTimeout.millisecond)(self ! LeaderTimeout)
    leaderCancell = context.system.scheduler.schedule(heartbeatTimeout.millisecond, heartbeatTimeout.millisecond)(self ! LeaderTimeout)
  }
  
  
  def startElection() = {
    currentTerm += 1
    println(id + " has started election for new term " + currentTerm)
    postAction(ElectionStarted(id, currentTerm))
    votedFor = id
    numOfVotes = 1
    val lastLogIndex = log.size // ? log.size - 1 ?
    val lastLogTerm = if(log.size > 0) log.last.term else 0
    members.foreach(entry => if(entry._1!=id) entry._2 ! RequestVote(currentTerm, id, lastLogIndex, lastLogTerm))
    
  }
  
  
  def processRequestVote(r: RequestVote, s: ActorRef) = r match {
    case RequestVote(requesterTerm, candidateId, lastLogIndex, lastLogTerm) => {
      if(requesterTerm < currentTerm) {
        s ! RequestVoteResponse(currentTerm, false)
        postAction(RequestVoteReceived(id, requesterTerm, candidateId, lastLogIndex, lastLogTerm, false))
        println("RequestVote in " + id + " from " + candidateId + ", term=" + requesterTerm + ", voteGranted=" + false)
      }
      else {
        val lastLogIndexCur = log.size // ? log.size - 1 ?
        val lastLogTermCur = if(log.size > 0) log.last.term else 0
        if(lastLogIndex >= lastLogIndexCur && lastLogTerm >= lastLogTermCur) {
          if(requesterTerm > currentTerm) {
            currentTerm = requesterTerm
            votedFor = candidateId
            s ! RequestVoteResponse(currentTerm, true)
            postAction(RequestVoteReceived(id, requesterTerm, candidateId, lastLogIndex, lastLogTerm, true))
            println("RequestVote in " + id + " from " + candidateId + ", term=" + requesterTerm + ", voteGranted=" + true)
          } else {
            //terms are equal - at least 2 candidates, need to check if this server has already received any RequestVote's (if 
            //it received and voted positively votedFor won't be 0, if negatively - it'll be 0 - so this server can vote)
            if(votedFor==0 || votedFor==candidateId) {
              s ! RequestVoteResponse(currentTerm, true)
              postAction(RequestVoteReceived(id, requesterTerm, candidateId, lastLogIndex, lastLogTerm, true))
              println("RequestVote in " + id + " from " + candidateId + ", term=" + requesterTerm + ", voteGranted=" + true)
              votedFor = candidateId
            }
            else {
              s ! RequestVoteResponse(currentTerm, false)
              postAction(RequestVoteReceived(id, requesterTerm, candidateId, lastLogIndex, lastLogTerm, false))
              println("RequestVote in " + id + " from " + candidateId + ", term=" + requesterTerm + ", voteGranted=" + false)
            }
          }
        } else {  
          currentTerm = requesterTerm
          s ! RequestVoteResponse(currentTerm, false)
          postAction(RequestVoteReceived(id, requesterTerm, candidateId, lastLogIndex, lastLogTerm, false))
          println("RequestVote in " + id + " from " + candidateId + ", term=" + requesterTerm + ", voteGranted=" + false)
          votedFor = 0  //set votedFor for 0 in order to subsequent requests in this term's election can win it (because this server hasn't voted yet in this term)
        } 
      }            
    }
  }
  
  def cancell(c: Cancellable) {
    if(c!=null) {
      c.cancel()
    }
  }
  
  def postAction(s: String) = {
    
  }
 
  def postAction(s: TestMessage) = {
    
  }
}