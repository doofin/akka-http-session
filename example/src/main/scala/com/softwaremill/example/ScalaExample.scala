package com.softwaremill.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.{Directive0, Directive1}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.softwaremill.example.ScalaExample.MyScalaSession.MyScalaSessionData
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.softwaremill.session._
import com.typesafe.scalalogging.StrictLogging

import scala.util.Try

object ScalaExample extends App with StrictLogging {
  object MyScalaSession {
    case class MyScalaSessionData(username: String)
    implicit def serializer: SessionSerializer[MyScalaSessionData, String] =
      new SingleValueSessionSerializer[MyScalaSessionData, String](_.username, { un: String =>
        Try {
          MyScalaSessionData(un)
        }
      })(SessionSerializer.stringToStringSessionSerializer)
  }

  implicit val system = ActorSystem("example")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val sessionConfig = SessionConfig.default(
    "c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe"
  )

  implicit val sessionManager = new SessionManager[MyScalaSessionData](sessionConfig)
  implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[MyScalaSessionData] {
    def log(msg: String) = logger.info(msg)
  }

//  private val myRefs1 = refreshable
  private val myRefs = refreshable(sessionManager, refreshTokenStorage, dispatcher)
  val setSessionDirec: MyScalaSessionData => Directive0 = setSession(myRefs, usingCookies, _)
  val sessDirec: Directive1[MyScalaSessionData] = requiredSession(myRefs, usingCookies)
  val invalidateSessDirec = invalidateSession(myRefs, usingCookies)

  val loginR = path("do_login") {
    post {
      entity(as[String]) { body =>
        logger.info(s"Logging in $body")

        setSessionDirec(MyScalaSession.MyScalaSessionData(body)) {
          setNewCsrfToken(checkHeader) {
            complete("ok")
          }
        }
      }
    }
  }
  val logoutR = path("do_logout") {
    post {
      sessDirec { session =>
        invalidateSessDirec {
          logger.info(s"Logging out $session")
          complete("ok")
        }
      }
    }
  }
  val loggedInR = path("current_login") {
    get {
      sessDirec { session =>
        logger.info("Current session: " + session)
        complete(session.username)
      }
    }
  }

  val routes =
    path("") {
      redirect("/site/index.html", Found)
    } ~
      randomTokenCsrfProtection(checkHeader) {
        pathPrefix("api") {
          loginR ~
            // This should be protected and accessible only when logged in
            logoutR ~
            // This should be protected and accessible only when logged in
            loggedInR
        } ~
          pathPrefix("site") {
            getFromResourceDirectory("")
          }
      }

}
