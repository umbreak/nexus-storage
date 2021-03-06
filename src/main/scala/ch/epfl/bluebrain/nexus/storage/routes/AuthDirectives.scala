package ch.epfl.bluebrain.nexus.storage.routes

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onComplete
import ch.epfl.bluebrain.nexus.iam.client.types._
import ch.epfl.bluebrain.nexus.iam.client.{IamClient, IamClientError}
import ch.epfl.bluebrain.nexus.storage.StorageError._
import com.typesafe.scalalogging.Logger
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

import scala.util.{Failure, Success}

object AuthDirectives {

  private val logger = Logger[this.type]

  /**
    * Extracts the credentials from the HTTP Authorization Header and builds the [[AuthToken]]
    */
  def extractToken: Directive1[Option[AuthToken]] =
    extractCredentials.flatMap {
      case Some(OAuth2BearerToken(value)) => provide(Some(AuthToken(value)))
      case Some(_)                        => failWith(AuthenticationFailed)
      case _                              => provide(None)
    }

  /**
    * Authenticates the requested with the provided ''token'' and returns the ''caller''
    */
  def extractCaller(implicit iam: IamClient[Task], token: Option[AuthToken]): Directive1[Caller] =
    onComplete(iam.identities.runToFuture).flatMap {
      case Success(caller)                         => provide(caller)
      case Failure(_: IamClientError.Unauthorized) => failWith(AuthenticationFailed)
      case Failure(_: IamClientError.Forbidden)    => failWith(AuthorizationFailed)
      case Failure(err) =>
        val message = "Error when trying to extract the subject"
        logger.error(message, err)
        failWith(InternalError(message))
    }
}
