package org.biobank.controllers

import org.biobank.domain._
import org.biobank.domain.user._
import org.biobank.fixture.{ ControllerFixture, NameGenerator }
import org.biobank.infrastructure.command.UserCommands._
import org.biobank.domain.JsonHelper._
import org.biobank.service.PasswordHasherComponentImpl

import com.typesafe.plugin._
import org.joda.time.DateTime
import org.scalatest.Tag
import org.slf4j.LoggerFactory
import play.api.Play.current
import play.api.libs.json._
import play.api.mvc.Cookie
import play.api.test.Helpers._
import play.api.test._

/**
  * Tests the REST API for [[User]].
  */
class UsersControllerSpec extends ControllerFixture with PasswordHasherComponentImpl {

  val log = LoggerFactory.getLogger(this.getClass)

  val nameGenerator = new NameGenerator(this.getClass)

  def createUserInRepository(plainPassword: String): RegisteredUser = {
    val salt = use[BbwebPlugin].passwordHasher.generateSalt

    val user = factory.createRegisteredUser.copy(
      salt = salt,
      password = use[BbwebPlugin].passwordHasher.encrypt(plainPassword, salt)
    )
    use[BbwebPlugin].userRepository.put(user)
    user
  }

  "User REST API" must {

    "GET /users" must {
      "list the default user in the test environment" in new App(fakeApp) {
        doLogin
        val json = makeRequest(GET, "/users")
        val jsonList = (json \ "data").as[List[JsObject]]
        jsonList must have size 1
        val jsonDefaultUser = jsonList(0)
          (jsonDefaultUser \ "email").as[String] mustBe ("admin@admin.com")
      }

      "list a new user" in new App(fakeApp) {
        doLogin
        val user = factory.createRegisteredUser
        use[BbwebPlugin].userRepository.put(user)

        val json = makeRequest(GET, "/users")
        val jsonList = (json \ "data").as[List[JsObject]]
        jsonList must have length 2
        compareObj(jsonList(1), user)
      }
    }

    "GET /users" must {
      "list multiple users" in new App(fakeApp) {
        doLogin
        val users = List(factory.createRegisteredUser, factory.createRegisteredUser)
        users.map(user => use[BbwebPlugin].userRepository.put(user))

        val json = makeRequest(GET, "/users")
        val jsonList = (json \ "data").as[List[JsObject]].filterNot { u =>
          (u \ "id").as[String].equals("admin@admin.com")
        }

        jsonList must have size users.size

        (jsonList zip users).map { item => compareObj(item._1, item._2) }
      }
    }

    "POST /users" must {
      "add a user" in new App(fakeApp) {
        doLogin
        val user = factory.createRegisteredUser
        val cmdJson = Json.obj(
          "name"      -> user.name,
          "email"     -> user.email,
          "password"  -> "testpassword",
          "avatarUrl" -> user.avatarUrl)
        val json = makeRequest(POST, "/users", json = cmdJson)

        (json \ "status").as[String] must include ("success")
      }
    }

    "PUT /users/:id/name" must {
      "update a user's name" in new App(fakeApp) {
        doLogin
        val user = factory.createRegisteredUser.activate | fail
        use[BbwebPlugin].userRepository.put(user)

        val cmdJson = Json.obj(
          "id"              -> user.id.id,
          "expectedVersion" -> Some(user.version),
          "name"            -> user.name)
        val json = makeRequest(PUT, s"/users/${user.id.id}/name", json = cmdJson)

        (json \ "status").as[String] must include ("success")
      }
    }

    "PUT /users/:id/email" must {
      "update a user's email" in new App(fakeApp) {
        doLogin
        val user = factory.createRegisteredUser.activate | fail
        use[BbwebPlugin].userRepository.put(user)

        val cmdJson = Json.obj(
          "id"              -> user.id.id,
          "expectedVersion" -> Some(user.version),
          "email"            -> user.email)
        val json = makeRequest(PUT, s"/users/${user.id.id}/email", json = cmdJson)

        (json \ "status").as[String] must include ("success")
      }
    }

    "PUT /users/:id/password" must {
      "update a user's email" in new App(fakeApp) {
        doLogin
        val plainPassword = nameGenerator.next[User]
        val newPassword = nameGenerator.next[User]
        val salt =  use[BbwebPlugin].passwordHasher.generateSalt
        val encryptedPassword = use[BbwebPlugin].passwordHasher.encrypt(plainPassword, salt)
        val user = factory.createActiveUser.copy(password = encryptedPassword, salt = salt)
        use[BbwebPlugin].userRepository.put(user)

        val cmdJson = Json.obj(
          "id"              -> user.id.id,
          "expectedVersion" -> Some(user.version),
          "oldPassword"     -> plainPassword,
          "newPassword"     -> newPassword)
        val json = makeRequest(PUT, s"/users/${user.id.id}/password", json = cmdJson)

        (json \ "status").as[String] must include ("success")
        (json \ "data" \ "password").as[String] must not be (newPassword)
      }
    }

    "GET /users/:id" must {
      "return a user" in new App(fakeApp) {
        doLogin
        val user = factory.createRegisteredUser.activate | fail
        use[BbwebPlugin].userRepository.put(user)
        val json = makeRequest(GET, s"/users/${user.id.id}")
        val jsonObj = (json \ "data").as[JsObject]
        compareObj(jsonObj, user)
      }
    }

    "PUT /users/activate" must {
      "activate a user" in new App(fakeApp) {
        doLogin

        val user = factory.createRegisteredUser
        use[BbwebPlugin].userRepository.put(user)

        val cmdJson = Json.obj(
          "expectedVersion" -> Some(user.version),
          "id"           -> user.id.id)
        val json = makeRequest(POST, s"/users/activate", json = cmdJson)

        (json \ "status").as[String] must include ("success")
      }
    }

    "PUT /users/lock" must {
      "lock a user" in new App(fakeApp) {
        doLogin

        val user = factory.createRegisteredUser.activate | fail
        use[BbwebPlugin].userRepository.put(user)

        val cmdJson = Json.obj(
          "expectedVersion" -> Some(user.version),
          "id"           -> user.id.id)
        val json = makeRequest(POST, s"/users/lock", json = cmdJson)

        (json \ "status").as[String] must include ("success")
      }
    }

    "PUT /users/unlock" must {
      "must unlock a user" in new App(fakeApp) {
        doLogin
        val user = factory.createRegisteredUser.activate | fail
        val lockedUser = user.lock | fail
        use[BbwebPlugin].userRepository.put(lockedUser)

        val cmdJson = Json.obj(
          "expectedVersion" -> Some(lockedUser.version),
          "id"           -> lockedUser.id.id)
        val json = makeRequest(POST, s"/users/unlock", json = cmdJson)

        (json \ "status").as[String] must include ("success")
      }
    }

    "POST /login" must {
      "allow a user to log in" in new App(fakeApp) {
        val plainPassword = nameGenerator.next[String]
        val user = createUserInRepository(plainPassword)

        val cmdJson = Json.obj(
          "email"     -> user.email,
          "password"  -> plainPassword)
        val json = makeRequest(POST, "/login", json = cmdJson)

        (json \ "data").as[String].length must be > 0
      }

      "prevent an invalid user from logging in" in new App(fakeApp) {
        val invalidUser = nameGenerator.nextEmail[String]
        val cmdJson = Json.obj(
          "email"     -> invalidUser,
          "password"  -> nameGenerator.next[String])
        val json = makeRequest(POST, "/login", FORBIDDEN, json = cmdJson)

        (json \ "status").as[String] must include ("error")
          (json \ "message").as[String] must include ("invalid email or password")
      }

      "prevent a user logging in with bad password" in new App(fakeApp) {
        val user = createUserInRepository(nameGenerator.next[String])
        val invalidPassword = nameGenerator.next[String]
        val cmdJson = Json.obj(
          "email"     -> user.email,
          "password"  -> invalidPassword)
        val json = makeRequest(POST, "/login", FORBIDDEN, json = cmdJson)

        (json \ "status").as[String] must include ("error")
          (json \ "message").as[String] must include ("invalid email or password")
      }

      "not allow a locked user to log in" in new App(fakeApp) {
        val plainPassword = nameGenerator.next[String]
        val activeUser = createUserInRepository(plainPassword).activate | fail
        val lockedUser = activeUser.lock | fail
        use[BbwebPlugin].userRepository.put(lockedUser)

        val cmdJson = Json.obj(
          "email"     -> lockedUser.email,
          "password"  -> plainPassword)
        val json = makeRequest(POST, "/login", FORBIDDEN, json = cmdJson)

        (json \ "status").as[String] must include ("error")
          (json \ "message").as[String] must include ("the user is locked")
      }

      "not allow a request with an invalid token" in new App(fakeApp) {
        doLogin

        val badToken = nameGenerator.next[String]

        // this request is valid since user is logged in
        val resp = route(FakeRequest(GET, "/users")
          .withHeaders("X-XSRF-TOKEN" -> badToken)
          .withCookies(Cookie("XSRF-TOKEN", badToken)))
        resp must not be (None)
        resp.map{ result =>
          status(result) mustBe(UNAUTHORIZED)
          contentType(result) mustBe(Some("application/json"))
          val json = Json.parse(contentAsString(result))
            (json \ "status").as[String] must include ("error")
            (json \ "message").as[String] must include ("invalid token")
        }
      }

      "not allow mismatched tokens in request" in new App(fakeApp) {
        val validToken = doLogin
        val badToken = nameGenerator.next[String]

        // this request is valid since user is logged in
        val resp = route(FakeRequest(GET, "/users")
          .withHeaders("X-XSRF-TOKEN" -> validToken)
          .withCookies(Cookie("XSRF-TOKEN", badToken)))
        resp must not be (None)
        resp.map { result =>
          status(result) mustBe(UNAUTHORIZED)
          contentType(result) mustBe(Some("application/json"))
          val json = Json.parse(contentAsString(result))
            (json \ "status").as[String] must include ("error")
            (json \ "message").as[String] must include ("Token mismatch")
        }
      }

      "not allow requests missing XSRF-TOKEN cookie" in new App(fakeApp) {
        doLogin

        val resp = route(FakeRequest(GET, "/users"))
        resp must not be (None)
        resp.map { result =>
          status(result) mustBe(UNAUTHORIZED)
          contentType(result) mustBe(Some("application/json"))
          val json = Json.parse(contentAsString(result))
            (json \ "status").as[String] must include ("error")
            (json \ "message").as[String] must include ("Invalid XSRF Token cookie")
        }
      }

      "not allow requests missing X-XSRF-TOKEN in header" in new App(fakeApp) {
        val token = doLogin

        val resp = route(FakeRequest(GET, "/users").withCookies(Cookie("XSRF-TOKEN", token)))
        resp must not be (None)
        resp.map { result =>
          status(result) mustBe(UNAUTHORIZED)
          contentType(result) mustBe(Some("application/json"))
          val json = Json.parse(contentAsString(result))
            (json \ "status").as[String] must include ("error")
            (json \ "message").as[String] must include ("No token")
        }
      }
    }


    "POST /logout" must {

      "disallow access to logged out users" in new App(fakeApp) {
        doLogin

        // this request is valid since user is logged in
        var json = makeRequest(GET, "/users")
        val jsonList = (json \ "data").as[List[JsObject]]
        jsonList must have size 1

        // the user is now logged out
        json = makeRequest(POST, "/logout")
          (json \ "status").as[String] must include ("success")

        // the following request must fail
        json = makeRequest(GET, "/users", UNAUTHORIZED)

        (json \ "status").as[String] must include ("error")
          (json \ "message").as[String] must include ("invalid token")
      }
    }

    "POST /passreset" must {

      "allow an active user to reset his/her password" taggedAs(Tag("1")) in new App(fakeApp) {
        val user = createUserInRepository(nameGenerator.next[String])
        val activeUser = user.activate | fail
        use[BbwebPlugin].userRepository.put(activeUser)

        val cmdJson = Json.obj("email" -> activeUser.email)
        val json = makeRequest(POST, "/passreset", json = cmdJson)
          (json \ "status").as[String] must include ("success")
      }

      "not allow a registered user to reset his/her password" in new App(fakeApp) {
        val user = createUserInRepository(nameGenerator.next[String])
        val cmdJson = Json.obj("email" -> user.email)
        val json = makeRequest(POST, "/passreset", FORBIDDEN, json = cmdJson)
          (json \ "status").as[String] must include ("error")
          (json \ "message").as[String] must include ("user is not active")
      }

      "not allow a locked user to reset his/her password" in new App(fakeApp) {
        val lockedUser = factory.createLockedUser
        use[BbwebPlugin].userRepository.put(lockedUser)

        val cmdJson = Json.obj("email" -> lockedUser.email)
        val json = makeRequest(POST, "/passreset", FORBIDDEN, json = cmdJson)
          (json \ "status").as[String] must include ("error")
          (json \ "message").as[String] must include ("user is not active")
      }

      "not allow a password reset on an invalid email address" in new App(fakeApp) {

        val cmdJson = Json.obj("email" -> nameGenerator.nextEmail[User])
        val json = makeRequest(POST, "/passreset", NOT_FOUND, json = cmdJson)
          (json \ "status").as[String] must include ("error")
          (json \ "message").as[String] must include ("email address not registered")
      }

    }

  }
}
