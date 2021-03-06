package org.biobank.controllers.users

import javax.inject.{Inject, Singleton}
import org.biobank.domain.user._
import org.biobank.controllers._
import org.biobank.infrastructure.command.UserCommands._
import org.biobank.service.studies.StudiesService
import org.biobank.service.users.UsersService
import org.biobank.service.{AuthToken, PagedResults}
import play.api.Logger
import play.api.cache.CacheApi
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc.{Action, Cookie, DiscardingCookie}
import play.api.{Environment, Logger}
import scala.concurrent.{ExecutionContext, Future}
import scalaz.Scalaz._
import scalaz.Validation.FlatMap._
import scalaz._

@Singleton
@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class UsersController @Inject() (val action:         BbwebAction,
                                 val env:            Environment,
                                 val cacheApi:       CacheApi,
                                 val authToken:      AuthToken,
                                 val usersService:   UsersService,
                                 val studiesService: StudiesService)
                             (implicit ec: ExecutionContext)
    extends CommandController
    with JsonController {

  val log: Logger = Logger(this.getClass)

  private val PageSizeMax = 20

  /** Used for obtaining the email and password from the HTTP login request */
  case class LoginCredentials(email: String, password: String)

  /** JSON reader for [[LoginCredentials]]. */
  implicit val loginCredentialsReads: Reads[LoginCredentials] = Json.reads[LoginCredentials]

  /**
   * Log-in a user. Expects the credentials in the body in JSON format.
   *
   * Set the cookie [[AuthTokenCookieKey]] to have AngularJS set the X-XSRF-TOKEN in the HTTP
   * header.
   *
   * @return The token needed for subsequent requests
   */
  def login(): Action[JsValue] = Action(parse.json) { implicit request =>
      request.body.validate[LoginCredentials].fold(
        errors => {
          BadRequest(JsError.toJson(errors))
        },
        loginCredentials => {
          val v = for {
              user  <- usersService.validatePassword(loginCredentials.email, loginCredentials.password)
              valid <- usersService.allowLogin(user)
            } yield user

          // FIXME: what if user attempts multiple failed logins? lock the account after 3 attempts?
          // how long to lock the account?
          v.fold(
            err => Unauthorized,
            user => {
              val token = authToken.newToken(user.id)
              log.debug(s"user logged in: ${user.email}, token: $token")
              Ok(user).withCookies(Cookie(AuthTokenCookieKey, token, None, httpOnly = false))
            }
          )
        }
      )
    }

  /**
   * Retrieves the user associated with the token, if it is valid.
   */
  def authenticateUser(): Action[Unit] = action(parse.empty) { implicit request =>
      usersService.getUser(request.authInfo.userId).fold(
        err  => Unauthorized,
        user => Ok(user)
      )
    }

  /**
   * Log-out a user. Invalidates the authentication token.
   *
   * Discard the cookie [[AuthTokenCookieKey]] to have AngularJS no longer set the
   * X-XSRF-TOKEN in HTTP header.
   */
  def logout(): Action[Unit] = action(parse.empty) { implicit request =>
      cacheApi.remove(request.authInfo.token)
      Ok("user has been logged out")
        .discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
        .withNewSession
    }

  def userCounts(): Action[Unit] =
    action(parse.empty) { implicit request =>
      Ok(usersService.getCountsByStatus)
    }

  def list: Action[Unit] =
    action.async(parse.empty) { implicit request =>
      validationReply(
        Future {
          for {
            pagedQuery <- PagedQuery.create(request.rawQueryString, PageSizeMax)
            users      <- usersService.getUsers(pagedQuery.filter, pagedQuery.sort)
            validPage  <- pagedQuery.validPage(users.size)
            results    <- PagedResults.create(users, pagedQuery.page, pagedQuery.limit)
          } yield results
        }
      )
    }

  /** Retrieves the user for the given id as JSON */
  def user(id: UserId): Action[Unit] = action(parse.empty) { implicit request =>
      validationReply(usersService.getUser(id))
    }

  def registerUser(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[RegisterUserCmd].fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      cmd => {
        Logger.debug(s"registerUser: cmd: $cmd")
        val future = usersService.register(cmd)
        future.map { validation =>
          validation.fold(
            err   => {
              val errs = err.list.toList.mkString(", ")
              if (errs.contains("exists")) {
                Forbidden("email already registered")
              } else {
                BadRequest(errs)
              }
            },
            user => Ok(user)
          )
        }
      }
    )
  }

  /** Resets the user's password.
   */
  def passwordReset(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[ResetUserPasswordCmd].fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      cmd => {
        val future = usersService.resetPassword(cmd)
        future.map { validation =>
          validation.fold(
            err   => Unauthorized,
            event => Ok("password has been reset")
          )
        }
      }
    )
  }

  def updateName(id: UserId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) { cmd: UpdateUserNameCmd =>
      processCommand(cmd)
    }

  def updateEmail(id: UserId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) { cmd: UpdateUserEmailCmd =>
      processCommand(cmd)
    }

  def updatePassword(id: UserId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) { cmd: UpdateUserPasswordCmd =>
      processCommand(cmd)
    }

  def updateAvatarUrl(id: UserId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) { cmd: UpdateUserAvatarUrlCmd =>
      processCommand(cmd)
    }

  def activateUser(id: UserId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) { cmd: ActivateUserCmd =>
      processCommand(cmd)
    }

  def lockUser(id: UserId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) { cmd: LockUserCmd =>
      processCommand(cmd)
    }

  def unlockUser(id: UserId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) { cmd: UnlockUserCmd =>
      processCommand(cmd)
    }

  def userStudies(id: UserId, query: Option[String], sort: Option[String], order: Option[String])
      : Action[Unit] =
    action(parse.empty) { implicit request =>
      // FIXME this should return only the studies this user has access to
      //
      // This this for now, but fix once user groups have been implemented
      val studies = studiesService.getStudyCount
      Ok(studies)
    }


  private def processCommand(cmd: UserCommand) = {
    validationReply(usersService.processCommand(cmd))
  }
}
