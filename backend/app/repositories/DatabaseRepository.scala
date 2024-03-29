package repositories

import javax.inject._
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.ExecutionContext
import play.api.db.slick.HasDatabaseConfigProvider
import slick.jdbc.JdbcProfile
import models.RedisDatabase
import java.util.UUID
import java.sql.Timestamp
import slick.sql.SqlProfile
import scala.concurrent.Future

@Singleton
class DatabaseRepository @Inject() (
    protected val dbConfigProvider: DatabaseConfigProvider
)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._
  private val databases = TableQuery[DatabaseTable]

  
  def getDatabasesByUserId(userId: UUID): Future[Seq[RedisDatabase]] =
    dbConfig.db.run(
      databases.filter(_.userId === userId).sortBy(_.createdAt.desc).result
    )


  def getDatabaseByIdsIn(
      databaseIds: Seq[UUID],
      userId: UUID
  ): Future[Seq[RedisDatabase]] =
    dbConfig.db.run(
      databases
        .filter(db => db.id.inSet(databaseIds) && db.userId === userId)
        .result
    )

  def getPort(databaseId: UUID): Future[Option[Int]] = {
    dbConfig.db
      .run(
        databases.filter(_.id === databaseId).map(_.port).result.headOption
      )
      .map(_.flatten)
  }

def updateDatabasePort(databaseId: UUID, port: Option[Int]): Future[Int] = {
  val query = for {
    db <- databases if db.id === databaseId
  } yield db.port

  dbConfig.db.run(query.update(port))
}


  def getAllActiveDatabases: Future[Seq[RedisDatabase]] = {
    dbConfig.db.run(
      databases.filter(_.port.isDefined).result
    )
  }

  def getDatabaseByNameAndUserId(
      name: String,
      userId: UUID
  ): Future[Option[RedisDatabase]] =
    dbConfig.db.run(
      databases
        .filter(db => db.name === name && db.userId === userId)
        .result
        .headOption
    )

  def getDatabaseById(databaseId: UUID): Future[Option[RedisDatabase]] = {
    dbConfig.db.run(
      databases.filter(_.id === databaseId).result.headOption
    )
  }

  def existsByNameAndUserId(name: String, userId: UUID): Future[Boolean] = {
    dbConfig.db.run(
      databases
        .filter(db => db.name === name && db.userId === userId)
        .exists
        .result
    )
  }
  def getDatabaseByIdAndUserId(
      databaseId: UUID,
      userId: UUID
  ): Future[Option[RedisDatabase]] = {
    dbConfig.db.run(
      databases
        .filter(db => db.id === databaseId && db.userId === userId)
        .result
        .headOption
    )
  }


  def addDatabase(database: RedisDatabase): Future[UUID] =
    dbConfig.db.run(
      databases.returning(databases.map(_.id)) += database
    )


  def deleteDatabaseByIdsIn(databaseIds: Seq[UUID], userId: UUID): Future[Int] =
    dbConfig.db.run(
      databases
        .filter(db => db.id.inSet(databaseIds) && db.userId === userId)
        .delete
    )

  private class DatabaseTable(tag: Tag)
      extends Table[RedisDatabase](tag, "databases") {

    def id = column[UUID]("id", O.PrimaryKey)
    def userId = column[UUID]("user_id")
    def name = column[String]("name")

    def createdAt = column[Timestamp](
      "created_at",
      SqlProfile.ColumnOption.SqlType(
        "timestamp not null default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP"
      )
    )
    def port = column[Option[Int]]("port")
    def * = (
      id,
      userId,
      name,
      createdAt,
      port
    ) <> ((RedisDatabase.apply _).tupled, RedisDatabase.unapply)
  }
}
