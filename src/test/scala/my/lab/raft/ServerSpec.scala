package my.lab.raft

import akka.testkit.TestKit
import akka.actor.ActorSystem
import org.scalatest.FunSuiteLike
import org.scalatest.BeforeAndAfterAll
import akka.testkit.TestProbe
import akka.actor.Props
import my.lab.raft.messages.RaftMessages._
import my.lab.raft.messages.ServiceMessages._
import my.lab.raft.messages.TestMessages._
import akka.actor.ActorRef
import scala.concurrent.duration._
import akka.actor.PoisonPill
import akka.testkit.TestActorRef

class ServerSpec extends TestKit(ActorSystem("ServerSpec")) 
  with FunSuiteLike 
  with BeforeAndAfterAll {

  
  override def afterAll(): Unit = {
    system.shutdown()
  }
  
  
  test("server should start in initialize state") {
    val init = TestProbe()
    val testServer = system.actorOf(Props(new ServerForTesting(0, init.ref, 500)), "testServer" + 0)
    init.send(testServer, GetState)
    init.expectMsg("Initialization")
    testServer ! PoisonPill
  }
  
  
  test("server should be properly initialized") {
    val init = TestProbe()
    var members = Map.empty[Int, ActorRef]
    for(i <- 1 to 1) {
      val testServer = system.actorOf(Props(new ServerForTesting(i, init.ref, 500)), "testServer" + 0)
    	members += i -> testServer
    }
    for(server <- members.values) {
      init.send(server, InitializeMembers(members))
    }
    init.expectMsg(InitializeMembersResponse)
    init.expectMsg(ChangedToFollower(1))
    //init.expectNoMsg(400.millisecond)
    init.send(members.head._2, GetState)
    init.expectMsg("Follower")
    //init.expectNoMsg(500.millisecond)
    //members.head._2 ! PoisonPill
    init.send(members.head._2, PoisonPill)
  }
  
  
  test("follower without leader (no leader after start) should receive timeout and start election") {
    val init = TestProbe()
    var members = Map.empty[Int, ActorRef]
    for(i <- 2 to 2) {
      val testServer = system.actorOf(Props(new ServerForTesting(i, init.ref, 500)), "testServer" + i)
      members += i -> testServer
    }
    for(server <- members.values) {
      init.send(server, InitializeMembers(members))
    }
    init.expectMsg(InitializeMembersResponse)
    init.expectMsg(ChangedToFollower(2))
    init.expectNoMsg(400.millisecond)
    init.expectMsg(ReceivedFollowerTimeout(2))
    init.expectMsg(ChangedToCandidate(2))
    init.expectMsg(ElectionStarted(2, 1L))
    
    //init.expectNoMsg(500.millisecond)
    //init.expectNoMsg(500.millisecond)
    //init.send(members.head._2, PoisonPill)
    for(server <- members.values) {
      init.send(server, PoisonPill)
    }
  }
  
  
  
  
  test("follower with shortest timeout value should start and win election") {
    val init = TestProbe()
    var members = Map.empty[Int, ActorRef]
    for(i <- 1 to 3) {
      //set timeout value as a parameter (i*200)
      val testServer = system.actorOf(Props(new ServerForTesting(i, init.ref, i*200)), "testServer" + i)
      members += i -> testServer
    }
    for(server <- members.values) {
      init.send(server, InitializeMembers(members))
    }
    
    val seq = init.receiveN(6, 100.milli)
    assert(seq.contains(ChangedToFollower(1)))
    assert(seq.contains(ChangedToFollower(2)))
    assert(seq.contains(ChangedToFollower(3)))
    init.expectMsg(ReceivedFollowerTimeout(1))
    init.expectMsg(ChangedToCandidate(1))
    init.expectMsg(ElectionStarted(1, 1L))
    val seq2 = init.receiveN(5)
    assert(seq2.contains(RequestVoteReceived(2,1,1,0,0,true)))
    assert(seq2.contains(RequestVoteReceived(3,1,1,0,0,true)))
    assert(seq2.contains(ChangedToLeader(1)))
    assert(seq2.contains(AppendEntriesReceived(3,1,1,0,0,List(),0)))
    assert(seq2.contains(AppendEntriesReceived(2,1,1,0,0,List(),0)))
    for(server <- members.values) {
      init.send(server, PoisonPill)
    }
    
  }
  
  
  test("leader must replicate messages") {
    val init = TestProbe()
    var members = Map.empty[Int, ActorRef]
    for(i <- 1 to 3) {
      //set timeout value as a parameter (i*200)
      val testServer = system.actorOf(Props(new ServerForTesting(i, init.ref, i*200)), "testServer" + i)
      members += i -> testServer
    }
    for(server <- members.values) {
      init.send(server, InitializeMembers(members))
    }
    
    val seq = init.receiveN(14, 600.milli)
    assert(seq.contains(ChangedToLeader(1)))
    assert(seq.contains(AppendEntriesReceived(3,1,1,0,0,List(),0)))
    assert(seq.contains(AppendEntriesReceived(2,1,1,0,0,List(),0)))
    init.send(members.head._2, GetState)
    init.expectMsg(Leader.toString)
    
        
    for(server <- members.values) {
      init.send(server, PoisonPill)
    }
    
  }
  
  
  /*
  // testing can be done that way, but many limitations, see http://doc.akka.io/docs/akka/snapshot/scala/testing.html 
  test("test using TestActorRef") {
    
    val actorRef = TestActorRef(new Server(1))
    val actor = actorRef.underlyingActor
    assert(actor.state == Initialization)
    actor.convertToCandidate
    assert(actor.state == Candidate)
    
  }
  */
  
}