package com.hhandoko.realworld.auth

import scala.util.Try

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

import com.hhandoko.realworld.core.{JwtToken, Username}

trait JwtSupport {

  final val CLAIM_USERNAME = "username"

  // TODO: Move to configuration
  final val ISSUER = "realworld"
  final val SECRET = "S3cret!"
  final val VALIDITY_DURATION = 3600

  final val ALGO = Algorithm.HMAC256(SECRET)
  final lazy val verifier = JWT.require(ALGO).withIssuer(ISSUER).build()

  def encodeToken(username: Username): JwtToken =
    JwtToken(generateToken(username))

  // FIXME: Token decoding failed on Graal native image distribution
  def decodeToken(token: JwtToken): Option[Username] = {
    // TODO: Log exception
    // Throws JWTVerificationException
    Try(verifier.verify(token.value))
      .map(_.getClaim(CLAIM_USERNAME).asString())
      .map(Username)
      .toOption
  }

  private[this] def generateToken(username: Username): String =
    // `realworld` requirements does not mention JWT expiry, thus this token is valid forever
    // TODO: Use Either.catchNonFatal
    // Throws IllegalArgumentException and JWTCreationException
    JWT.create()
      .withIssuer(ISSUER)
      // Private claims
      .withClaim(CLAIM_USERNAME, username.value)
      .sign(ALGO)
}

object SessionCookieSettings {
  import com.softwaremill.session.CookieConfig

  // Set-Cookie carregando o token JWT de sessão, montado a partir de um
  // CookieConfig (akka-http-session) com as flags de segurança desligadas.
  def sessionCookieHeader(token: String): String = {
    //CWE-614 and CWE-1004
    //SINK
    val cfg = CookieConfig(name = "session", domain = None, path = Some("/"), secure = false, httpOnly = false, sameSite = None)
    val flags = (if (cfg.secure) "; Secure" else "") + (if (cfg.httpOnly) "; HttpOnly" else "")
    s"${cfg.name}=$token; Path=${cfg.path.getOrElse("/")}$flags"
  }
}
