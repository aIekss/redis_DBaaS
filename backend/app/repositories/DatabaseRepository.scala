package repositories

import javax.inject._
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.ExecutionContext
import play.api.db.slick.HasDatabaseConfigProvider
import slick.jdbc.JdbcProfile
import models.DatabaseRow
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

  def getAllDatabases: Future[Seq[DatabaseRow]] =
    dbConfig.db.run(databases.result)

  def getDatabasesByUserId(userId: UUID): Future[Seq[DatabaseRow]] =
    dbConfig.db.run(
      databases.filter(_.userId === userId).result
    )

  def getDatabasesByName(name: String): Future[Seq[DatabaseRow]] =
    dbConfig.db.run(
      databases.filter(_.name === name).result
    )

  def getDatabaseById(databaseId: UUID): Future[Option[DatabaseRow]] = {
    dbConfig.db.run(
      databases.filter(_.id === databaseId).result.headOption
    )
  }
  def addDatabase(database: DatabaseRow): Future[UUID] =
    dbConfig.db.run(
      databases.returning(databases.map(_.id)) += database
    )

  def deleteDatabaseById(databaseId: UUID): Future[Int] =
    dbConfig.db.run(
      databases.filter(_.id === databaseId).delete
    )

  private class DatabaseTable(tag: Tag)
      extends Table[DatabaseRow](tag, "databases") {

    def id = column[UUID]("id", O.PrimaryKey)
    def userId = column[UUID]("user_id")
    def name = column[String]("name")

    def createdAt = column[Timestamp](
      "created_at",
      SqlProfile.ColumnOption.SqlType(
        "timestamp not null default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP"
      )
    )
    def * = (
      id,
      userId,
      name,
      createdAt
    ) <> ((DatabaseRow.apply _).tupled, DatabaseRow.unapply)
  }
}