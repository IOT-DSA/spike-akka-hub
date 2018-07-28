package client

import akka.actor.Props
import akka.persistence.{PersistentActor, SnapshotOffer}

/**
  * Generates sequential unique ids.
  */
class IdGenerator extends PersistentActor {

  import IdGenerator._

  val persistenceId: String = "IdGenerator"

  val snapshotInterval = 100

  private var lastId = 0

  def receiveCommand: Receive = {
    case GenerateIds(count) =>
      persist(IdsGenerated((lastId + 1) to (lastId + count))) {
        case evt: IdsGenerated =>
          updateLastId(evt)
          if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0)
            saveSnapshot(lastId)
          sender ! evt
      }
  }

  override def receiveRecover: Receive = {
    case evt: IdsGenerated                => updateLastId(evt)
    case SnapshotOffer(_, lastSaved: Int) => lastId = lastSaved
  }

  private def updateLastId(evt: IdsGenerated) = lastId = evt.ids.max
}

/**
  * Factory for [[IdGenerator]] instances.
  */
object IdGenerator {

  case class GenerateIds(count: Int)

  case class IdsGenerated(ids: Iterable[Int])

  def props = Props(new IdGenerator)
}