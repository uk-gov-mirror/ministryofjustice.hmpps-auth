package uk.gov.justice.digital.hmpps.oauth2server.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import uk.gov.justice.digital.hmpps.oauth2server.config.CookieRequestCache
import uk.gov.justice.digital.hmpps.oauth2server.verify.VerifyEmailService
import java.io.IOException
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@Component
class JwtAuthenticationSuccessHandler(
  private val jwtCookieHelper: JwtCookieHelper,
  private val jwtAuthenticationHelper: JwtAuthenticationHelper,
  cookieRequestCache: CookieRequestCache,
  private val verifyEmailService: VerifyEmailService,
  @Qualifier("tokenVerificationApiRestTemplate") private val restTemplate: RestTemplate,
  @Value("\${tokenverification.enabled:false}") private val tokenVerificationEnabled: Boolean
) : SavedRequestAwareAuthenticationSuccessHandler() {

  init {
    @Suppress("LeakingThis")
    setRequestCache(cookieRequestCache)
    defaultTargetUrl = "/"
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Throws(IOException::class, ServletException::class)
  override fun onAuthenticationSuccess(
    request: HttpServletRequest,
    response: HttpServletResponse,
    authentication: Authentication
  ) {
    addAuthenticationToRequest(request, response, authentication)

    // we have successfully authenticated and added the cookie.  Now need to check that they have a validated email address
    if (verifyEmailService.isNotVerified(authentication.name)) {
      redirectStrategy.sendRedirect(request, response, "/verify-email")
      return
    }
    proceed(request, response, authentication)
  }

  private fun addAuthenticationToRequest(
    request: HttpServletRequest,
    response: HttpServletResponse,
    authentication: Authentication,
  ) {
    val optionalAuth = jwtCookieHelper.readValueFromCookie(request)
      .flatMap { jwtAuthenticationHelper.readUserDetailsFromJwt(it) }
    optionalAuth.ifPresent { udi: UserDetailsImpl ->
      log.info(
        "Found existing cookie for user {} with jwt of {}",
        udi.username,
        udi.jwtId
      )
      if (tokenVerificationEnabled) restTemplate.delete("/token?authJwtId={authJwtId}", udi.jwtId)
    }
    val jwt = jwtAuthenticationHelper.createJwt(authentication)
    jwtCookieHelper.addCookieToResponse(request, response, jwt)
  }

  fun updateAuthenticationInRequest(
    request: HttpServletRequest,
    response: HttpServletResponse,
    authentication: Authentication,
  ) {
    val optionalAuth = jwtCookieHelper.readValueFromCookie(request)
      .flatMap { jwtAuthenticationHelper.readUserDetailsFromJwt(it) }
    val passedMfa = optionalAuth.map { it.passedMfa }.orElse(false)
    val jwt = optionalAuth.map { jwtAuthenticationHelper.createJwtWithId(authentication, it.jwtId, passedMfa) }
      .orElseGet { jwtAuthenticationHelper.createJwt(authentication) }

    jwtCookieHelper.addCookieToResponse(request, response, jwt)
  }

  @Throws(ServletException::class, IOException::class)
  fun proceed(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {
    super.onAuthenticationSuccess(request, response, authentication)
  }

  fun updateMfaInRequest(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {
    val user = authentication.principal as UserDetailsImpl
    val jwt = jwtAuthenticationHelper.createJwtWithId(authentication, user.jwtId, true)
    jwtCookieHelper.addCookieToResponse(request, response, jwt)

    // also need to update current authentication in security context
    jwtAuthenticationHelper.readAuthenticationFromJwt(jwt)
      .ifPresent { SecurityContextHolder.getContext().authentication = it }
  }
}
