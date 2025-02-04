package uk.gov.justice.digital.hmpps.oauth2server.delius.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.oauth2server.config.DeliusRoleMappings
import uk.gov.justice.digital.hmpps.oauth2server.delius.model.DeliusUserPersonDetails
import uk.gov.justice.digital.hmpps.oauth2server.delius.model.UserDetails
import uk.gov.justice.digital.hmpps.oauth2server.delius.model.UserRole
import uk.gov.justice.digital.hmpps.oauth2server.security.DeliusAuthenticationServiceException
import java.util.Optional

class DeliusUserList : MutableList<UserDetails> by ArrayList()

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@Service
class DeliusUserService(
  @Qualifier("deliusWebClient") private val webClient: WebClient,
  @Value("\${delius.enabled:false}") private val deliusEnabled: Boolean,
  deliusRoleMappings: DeliusRoleMappings,
) {
  private val mappings: Map<String, List<String>> =
    deliusRoleMappings.mappings.mapKeys { it.key.toUpperCase().replace('.', '_') }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun getDeliusUsersByEmail(email: String): List<DeliusUserPersonDetails> {
    if (!deliusEnabled) {
      log.debug("Delius integration disabled; unable to proceed for user with email {}", email)
      return emptyList()
    }

    val users = webClient.get().uri("/users/search/email/{email}/details", email)
      .retrieve()
      .bodyToMono(DeliusUserList::class.java)
      .onErrorResume(
        { it is WebClientResponseException && it.statusCode.is5xxServerError },
        {
          log.warn("Unable to retrieve details from delius for user with email {} due to delius error", email, it)
          throw DeliusAuthenticationServiceException(email)
        }
      )
      .onErrorResume(
        { it is WebClientResponseException && it.statusCode.is4xxClientError },
        {
          log.warn(
            "Unable to retrieve details from delius for user with email {} due to http error [{}]",
            email,
            (it as WebClientResponseException).statusCode,
            it
          )
          Mono.empty()
        }
      )
      .onErrorResume(WebClientResponseException::class.java) {
        log.warn("Unable to retrieve details from delius for user with email {} due to unknown error", email, it)
        Mono.empty()
      }
      .onErrorResume(
        { it !is DeliusAuthenticationServiceException },
        {
          log.warn("Unable to retrieve details from Delius for user with email {} due to", email, it)
          Mono.error(DeliusAuthenticationServiceException(email))
        }
      )
      .block()

    return users?.map { mapUserDetailsToDeliusUser(it) } ?: emptyList()
  }

  fun getDeliusUserByUsername(username: String): Optional<DeliusUserPersonDetails> {
    if (!deliusEnabled) {
      log.debug("Delius integration disabled, returning empty for {}", username)
      return Optional.empty()
    }

    val userDetails = webClient.get().uri("/users/{username}/details", username)
      .retrieve()
      .bodyToMono(UserDetails::class.java)
      .onErrorResume(
        WebClientResponseException.NotFound::class.java
      ) {
        log.debug("User not found in delius due to {}", it.message)
        Mono.empty()
      }
      .onErrorResume(
        { it is WebClientResponseException && it.statusCode.is4xxClientError },
        {
          log.warn(
            "Unable to get delius user details for user {} due to {}",
            username,
            (it as WebClientResponseException).statusCode,
            it
          )
          Mono.empty()
        }
      )
      .onErrorResume(WebClientResponseException::class.java) {
        log.warn(
          "Unable to retrieve details from Delius for user {} due to",
          username,
          (it as WebClientResponseException).statusCode
        )
        Mono.error(DeliusAuthenticationServiceException(username))
      }
      .onErrorResume(WebClientRequestException::class.java) {
        log.warn("Unable to retrieve details from Delius for user {} due to", username, it)
        Mono.error(DeliusAuthenticationServiceException(username))
      }
      .onErrorResume(
        { it !is DeliusAuthenticationServiceException },
        {
          log.warn("Unable to retrieve details from Delius for user {} due to", username, it)
          Mono.empty()
        }
      )
      .block()

    return Optional.ofNullable(userDetails).map { u -> mapUserDetailsToDeliusUser(u) }
  }

  fun authenticateUser(username: String, password: String): Boolean {
    if (!deliusEnabled) {
      log.debug("Delius integration disabled, returning empty for {}", username)
      return false
    }

    return webClient.post().uri("/authenticate")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(AuthUser(username, password))
      .retrieve()
      .bodyToMono(Boolean::class.java)
      .defaultIfEmpty(true)
      .onErrorResume(WebClientResponseException.Unauthorized::class.java) {
        log.debug("User not found in delius due to {}", it.message)
        Mono.just(false)
      }
      .onErrorResume(WebClientResponseException::class.java) {
        log.warn("Unable to authenticate user {}", username, it)
        Mono.just(false)
      }
      .onErrorResume(Exception::class.java) {
        log.warn("Unable to authenticate user for user {}", username, it)
        Mono.just(false)
      }
      .block()!!
  }

  private fun mapUserDetailsToDeliusUser(userDetails: UserDetails): DeliusUserPersonDetails =
    DeliusUserPersonDetails(
      username = userDetails.username.toUpperCase(),
      userId = userDetails.userId,
      firstName = userDetails.firstName,
      surname = userDetails.surname,
      email = userDetails.email.toLowerCase(),
      enabled = userDetails.enabled,
      roles = mapUserRolesToAuthorities(userDetails.roles)
    )

  private fun mapUserRolesToAuthorities(userRoles: List<UserRole>): Collection<GrantedAuthority> =
    userRoles.mapNotNull { (name) -> mappings[name] }
      .flatMap { r -> r.map(::SimpleGrantedAuthority) }
      .toSet()

  fun changePassword(username: String, password: String) {
    if (!deliusEnabled) {
      log.debug("Delius integration disabled, returning empty for {}", username)
      return
    }
    webClient.post().uri("/users/{username}/password", username)
      .bodyValue(AuthPassword(password))
      .retrieve()
      .toBodilessEntity()
      .block()
  }

  data class AuthUser(val username: String, val password: String)

  data class AuthPassword(val password: String)
}
