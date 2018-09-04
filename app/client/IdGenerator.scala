package client

import akka.actor.Props
import akka.persistence.{PersistentActor, SnapshotOffer}

/**
  * Generates sequential unique ids. The last id is persisted, so the sequence continues on restart.
  */
class IdGenerator extends PersistentActor {

  import IdGenerator._

  val persistenceId: String = "IdGenerator"

  val snapshotInterval = 100

  private var lastId = 0

  /**
    * Called on incoming command.
    */
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

  /**
    * Called on recovery for each event.
    */
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

  /**
    * A request to generate a series of IDs.
    *
    * @param count
    */
  case class GenerateIds(count: Int)

  /**
    * A response containing a sequence of unique IDs.
    *
    * @param ids
    */
  case class IdsGenerated(ids: Iterable[Int])

  /**
    * Creates a new props for IdGenerator actor.
    *
    * @return
    */
  def props = Props(new IdGenerator)
}