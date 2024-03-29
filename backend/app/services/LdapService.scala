package services

import scala.concurrent.Future
import models.User
import javax.inject.Inject
import play.api.Configuration
import scala.concurrent.ExecutionContext
import org.apache.directory.ldap.client.api.LdapConnectionConfig

import LdapService.Exceptions._
import org.apache.directory.ldap.client.api.LdapNetworkConnection
import org.apache.directory.api.ldap.model.exception.LdapException
import org.apache.directory.api.ldap.model.cursor.CursorException
import org.apache.directory.api.ldap.model.message.SearchScope
import org.apache.directory.api.ldap.model.entry.Entry
import java.util.UUID
import play.api.ConfigLoader
import scala.util.Try
import play.api.Logger
import javax.inject.Singleton

@Singleton
class LdapService @Inject() (config: Configuration)(implicit
    ec: ExecutionContext
) {

  val logger = Logger(this.getClass)

  implicit val stringLoader: ConfigLoader[String] = ConfigLoader.stringLoader
  implicit val intLoader: ConfigLoader[Int] = ConfigLoader.intLoader
  implicit val boolLoader: ConfigLoader[Boolean] = ConfigLoader.booleanLoader

   private def getConfigValue[T](
      key: String
  )(implicit loader: ConfigLoader[T]): Future[T] = {
    Future.fromTry(
      Try(
        config
          .getOptional[T](key)
          .getOrElse(throw ConfigurationError(s"Configuration key not found: $key"))
      )
    )
  }

  def authenticate(username: String, password: String): Future[User] = {
    for {
      ldapConnectionConfig <- getConnectionConfig(username, password)
      ldapConnection <- getLdapConnection(ldapConnectionConfig)
      entry <- searchForEntry(ldapConnection, username)
      user <- getUserFromEntry(entry, username)
    } yield user
  }

  def getConnectionConfig(
      username: String,
      password: String
  ): Future[LdapConnectionConfig] = {
    for {
      ldapHost <- getConfigValue[String]("ldap.host")
      ldapPort <- getConfigValue[Int]("ldap.port")
      ldapStartTls <- getConfigValue[Boolean]("ldap.startTls")
      ldapUseSsl <- getConfigValue[Boolean]("ldap.useSsl")
      ldapUserRoot <- getConfigValue[String]("ldap.userRoot")
      ldapName <- getConfigValue[String]("ldap.name")
    } yield {
      val ldapConnectionConfig = new LdapConnectionConfig()
      ldapConnectionConfig.setLdapHost(ldapHost)
      ldapConnectionConfig.setLdapPort(ldapPort)
      ldapConnectionConfig.setUseTls(ldapStartTls)
      ldapConnectionConfig.setUseSsl(ldapUseSsl)
      ldapConnectionConfig.setName(
        ldapName
          .replace("%USER%", username)
          .replace("%USER_ROOT%", ldapUserRoot)
      )
      ldapConnectionConfig.setCredentials(password)
      ldapConnectionConfig
    }
  }

  def getLdapConnection(
      ldapConnectionConfig: LdapConnectionConfig
  ): Future[LdapNetworkConnection] =
    Future {
      val ldapConnection = new LdapNetworkConnection(ldapConnectionConfig)
      ldapConnection.bind()
      ldapConnection

    }.recoverWith {
      case _: LdapException => Future.failed(AccessError())
      case t: Throwable     => internalError(t.getMessage)
    }

  def searchForEntry(
      ldapNetworkConnection: LdapNetworkConnection,
      username: String
  ): Future[Entry] =
    Future {
      val ldapUserRoot = config.get[String]("ldap.userRoot")
      val filter = s"(cn=$username)"
      val entryCursor = ldapNetworkConnection.search(
        ldapUserRoot,
        filter,
        SearchScope.ONELEVEL,
        "*"
      )
      if (entryCursor.next()) entryCursor.get()
      else throw AccessError(s"No LDAP entry found for user: $username")
    }.recoverWith {
      case e: LdapException   => Future.failed(AccessError(e.getMessage))
      case e: CursorException => Future.failed(AccessError(e.getMessage))
      case t: Throwable       => internalError(t.getMessage)
    }

  def getUserFromEntry(entry: Entry, username: String): Future[User] =
    Future {
      val firstName = getStringWithFallBack(entry, "givenName", "Undefined")
      val lastName = getStringWithFallBack(entry, "sn", "Undefined")
      val mail = getStringWithFallBack(entry, "mail", "")
      val employeeType =
        getStringWithFallBack(entry, "employeetype", "Uncategorized")
      User(null, username, firstName, lastName, mail, employeeType)
    }

  private def getStringWithFallBack(
      entry: Entry,
      key: String,
      fallBack: String
  ): String =
    Option(entry.get(key)).map(_.getString).getOrElse(fallBack)

  private def internalError(errorMessage: String): Future[Nothing] = {
    logger.error(errorMessage)
    Future.failed(InternalError(errorMessage))
  }

}

object LdapService {
  object Exceptions {
    sealed abstract class LdapServiceException(message: String) extends RuntimeException(message)

    final case class ConfigurationError(message: String) extends LdapServiceException(message)
    final case class AccessError(message: String = "Access denied") extends LdapServiceException(message)
    final case class InternalError(message: String = "Internal error") extends LdapServiceException(message)
  }
}
