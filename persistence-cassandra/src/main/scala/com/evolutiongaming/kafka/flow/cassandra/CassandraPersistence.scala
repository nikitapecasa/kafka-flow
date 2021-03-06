package com.evolutiongaming.kafka.flow.cassandra

import cats.Monad
import cats.MonadThrow
import cats.arrow.FunctionK
import cats.effect.Clock
import cats.syntax.all._
import com.evolutiongaming.cassandra.sync.CassandraSync
import com.evolutiongaming.kafka.flow.journal.CassandraJournals
import com.evolutiongaming.kafka.flow.key.CassandraKeys
import com.evolutiongaming.kafka.flow.persistence.PersistenceModule
import com.evolutiongaming.kafka.flow.snapshot.CassandraSnapshots
import com.evolutiongaming.kafka.journal.FromBytes
import com.evolutiongaming.kafka.journal.ToBytes
import com.evolutiongaming.kafka.journal.eventual.cassandra.CassandraSession
import scala.util.Try

trait CassandraPersistence[F[_], S] extends PersistenceModule[F, S]
object CassandraPersistence {

  /** Creates schema in Cassandra if not there yet */
  def withSchemaF[F[_]: MonadThrow: Clock, S](
    session: CassandraSession[F],
    sync: CassandraSync[F]
  )(implicit
    fromBytes: FromBytes[F, S],
    toBytes: ToBytes[F, S]
  ): F[PersistenceModule[F, S]] = for {
    _keys      <- CassandraKeys.withSchema(session, sync)
    _journals  <- CassandraJournals.withSchema(session, sync)
    _snapshots <- CassandraSnapshots.withSchema[F, S](session, sync)
  } yield new CassandraPersistence[F, S] {
    def keys = _keys
    def journals = _journals
    def snapshots = _snapshots
  }

  /** Creates schema in Cassandra if not there yet
    *
    * This method uses the same `JsonCodec[Try]` as `JournalParser` does to
    * simplify defining the basic application.
    */
  def withSchema[F[_]: MonadThrow: Clock, S](
    session: CassandraSession[F],
    sync: CassandraSync[F]
  )(implicit
    fromBytes: FromBytes[Try, S],
    toBytes: ToBytes[Try, S]
  ): F[PersistenceModule[F, S]] = {
    val fromTry = FunctionK.liftFunction[Try, F](MonadThrow[F].fromTry)
    implicit val _fromBytes = fromBytes mapK fromTry
    implicit val _toBytes = toBytes mapK fromTry
    withSchemaF(session, sync)
  }

  /** Deletes all data in Cassandra */
  def truncate[F[_]: Monad](
    session: CassandraSession[F],
    sync: CassandraSync[F]
  ): F[Unit] =
    CassandraKeys.truncate(session, sync) *>
    CassandraJournals.truncate(session, sync) *>
    CassandraSnapshots.truncate(session, sync)

}
