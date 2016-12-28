package my.lab.raft


trait Command

case class LogEntry(val command: Command, val term: Long)