package pl.project13.scala.akka.raft

import akka.actor.ActorRef

case class ReplicatedLog[Command <: AnyRef](
  entries: Vector[Entry[Command]],
  commitedIndex: Int,
  lastApplied: Int
) {

  def commands = entries.map(_.command)

  /**
   * Performs the "consistency check", which checks if the data that we just got from the
   */
  def containsMatchingEntry(otherPrevTerm: Term, otherPrevIndex: Int): Boolean =
    (otherPrevIndex == 0 && entries.isEmpty) || (lastTerm == otherPrevTerm && lastIndex == otherPrevIndex)

  // log state
  def lastTerm  = entries.lastOption map { _.term } getOrElse Term(0)
  def lastIndex = entries.length - 1

  def prevIndex = lastIndex - 1
  def prevTerm  = if (entries.size < 2) Term(0) else entries.dropRight(1).last.term

  // log actions
  def commit(n: Int): ReplicatedLog[Command] =
    copy(commitedIndex = n) // todo persist too, right?

  def append(newEntry: Entry[Command]): ReplicatedLog[Command] =
    copy(entries = entries :+ newEntry)

  def +(newEntry: Entry[Command]): ReplicatedLog[Command] =
    append(newEntry)

//  def append(newEntries: Seq[Entry[Command]]): ReplicatedLog[Command] =
//    copy(entries = entries ++ newEntries)

  def putWithDroppingInconsistent(replicatedEntry: Entry[Command]): ReplicatedLog[Command] = {
    val replicatedIndex = replicatedEntry.index
    if (entries.isDefinedAt(replicatedIndex)) {
      val localEntry = entries(replicatedIndex)

      if (localEntry == replicatedEntry)
        this // we're consistent with the replicated log
      else
        copy(entries = entries.slice(0, replicatedIndex) :+ replicatedEntry) // dropping everything until the entry that does not match
    } else {
      // nothing to drop
      this
    }
  }

  // log views

  def apply(idx: Int): Entry[Command] = entries(idx)

  /** @param fromExcluding index from which to start the slice (excluding the entry at that index) */
  def entriesBatchFrom(fromExcluding: Int, howMany: Int = 5): Vector[Entry[Command]] =
    entries.slice(fromExcluding + 1, fromExcluding + 1 + howMany)
  
  def commandsBatchFrom(fromExcluding: Int, howMany: Int = 5): Vector[Command] =
      entriesBatchFrom(fromExcluding, howMany).map(_.command)

  def firstIndexInTerm(term: Term): Int =
    entries.zipWithIndex find { case (e, i) => e.term == term } map { _._2 } getOrElse 0

  def termAt(index: Int) =
    if (index < 0) Term(0)
    else entries(index).term

  def committedEntries = entries.slice(0, commitedIndex)

  def notCommittedEntries = entries.slice(commitedIndex + 1, entries.length)
}

class EmptyReplicatedLog[T <: AnyRef] extends ReplicatedLog[T](Vector.empty, 0, 0) { // todo lastapplied?
  override def lastTerm = Term(0)
  override def lastIndex = 0
}

object ReplicatedLog {
  def empty[T <: AnyRef]: ReplicatedLog[T] = new EmptyReplicatedLog[T]
}

case class Entry[T](
  command: T,
  term: Term,
  index: Int,
  client: Option[ActorRef] = None
)