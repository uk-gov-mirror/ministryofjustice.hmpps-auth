package uk.gov.justice.digital.hmpps.oauth2server.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.oauth2server.auth.model.UserToken.TokenType
import uk.gov.justice.digital.hmpps.oauth2server.security.UserService
import uk.gov.justice.digital.hmpps.oauth2server.utils.IpAddressHelper
import uk.gov.justice.digital.hmpps.oauth2server.verify.TokenService
import uk.gov.service.notify.NotificationClientApi
import java.util.*

@Service
open class MfaService(@Value("\${application.authentication.mfa.whitelist}") whitelist: Set<String>,
                      @Value("\${application.authentication.mfa.roles}") private val mfaRoles: Set<String>,
                      @Value("\${application.notify.mfa.template}") private val mfaTemplateId: String,
                      private val tokenService: TokenService,
                      private val userService: UserService,
                      private val notificationClient: NotificationClientApi) {

  private val ipMatchers: List<IpAddressMatcher>

  init {
    ipMatchers = whitelist.map { ip -> IpAddressMatcher(ip) }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  open fun needsMfa(authorities: Collection<GrantedAuthority>): Boolean {
    // if they're whitelisted then no mfa
    val ip = IpAddressHelper.retrieveIpFromRequest()
    return if (ipMatchers.any { it.matches(ip) }) {
      false
      // otherwise check that they have a role that requires mfa
    } else authorities.stream().map { it.authority }.anyMatch { r -> mfaRoles.contains(r) }
  }

  @Transactional(transactionManager = "authTransactionManager")
  open fun createTokenAndSendEmail(username: String): Pair<String, String> {
    log.info("Creating token and sending email for {}", username)
    val user = userService.getOrCreateUser(username)

    val token = tokenService.createToken(TokenType.MFA, username)
    val code = tokenService.createToken(TokenType.MFA_CODE, username)

    val firstName = userService.findMasterUserPersonDetails(username).map { it.firstName }.orElseThrow()

    notificationClient.sendEmail(mfaTemplateId, user.email, mapOf("firstName" to firstName, "code" to code), null)

    return Pair(token, code)
  }

  open fun validateMfaCode(code: String?): Optional<String> {
    if (code.isNullOrBlank()) return Optional.of("missingcode");
    // 1. look up mfa code
    // 2. fail if expired or invalid - incorrect password attempts?
    // 3. return empty if okay
    return Optional.empty()
  }

  open fun resendMfaCode(token: String) {
    // 1. look up mfa token and find user
    // 2. Find mfa code for user
    // 3. send email to user again with token and code
  }
}
