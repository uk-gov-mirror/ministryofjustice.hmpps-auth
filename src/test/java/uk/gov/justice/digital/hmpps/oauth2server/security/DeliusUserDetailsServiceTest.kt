package uk.gov.justice.digital.hmpps.oauth2server.security

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.security.core.userdetails.UsernameNotFoundException
import uk.gov.justice.digital.hmpps.oauth2server.auth.model.User
import uk.gov.justice.digital.hmpps.oauth2server.delius.model.DeliusUserPersonDetails
import uk.gov.justice.digital.hmpps.oauth2server.delius.service.DeliusUserService
import java.util.*

@RunWith(MockitoJUnitRunner::class)
class DeliusUserDetailsServiceTest {
  private val deliusUserService: DeliusUserService = mock()
  private val userService: UserService = mock()
  private lateinit var service: DeliusUserDetailsService
  @Before
  fun setup() {
    service = DeliusUserDetailsService(deliusUserService, userService)
  }

  @Test
  fun `happy user path`() {
    val user = buildStandardUser()
    whenever(deliusUserService.getDeliusUserByUsername(anyString())).thenReturn(Optional.of(user))
    val itagUser = service.loadUserByUsername(user.username)
    assertThat(itagUser).isNotNull()
    assertThat(itagUser.isAccountNonExpired).isTrue()
    assertThat(itagUser.isAccountNonLocked).isTrue()
    assertThat(itagUser.isCredentialsNonExpired).isTrue()
    assertThat(itagUser.isEnabled).isFalse()
    assertThat((itagUser as UserPersonDetails).name).isEqualTo("Itag User")
  }

  @Test
  fun `user locked in auth`() {
    val user = buildStandardUser()
    whenever(deliusUserService.getDeliusUserByUsername(anyString())).thenReturn(Optional.of(user))
    whenever(userService.findUser(anyString())).thenReturn(Optional.of(User.builder().locked(true).build()))
    val itagUser = service.loadUserByUsername(user.username)
    assertThat(itagUser).isNotNull()
    assertThat(itagUser.isAccountNonExpired).isTrue()
    assertThat(itagUser.isAccountNonLocked).isFalse()
    assertThat(itagUser.isCredentialsNonExpired).isTrue()
    assertThat(itagUser.isEnabled).isFalse()
    assertThat((itagUser as UserPersonDetails).name).isEqualTo("Itag User")
  }

  @Test
  fun `user not found`() {
    whenever(deliusUserService.getDeliusUserByUsername(anyString())).thenReturn(Optional.empty())
    assertThatThrownBy { service.loadUserByUsername("user") }.isInstanceOf(UsernameNotFoundException::class.java)
  }

  private fun buildStandardUser(): DeliusUserPersonDetails =
      DeliusUserPersonDetails(username = "ITAG_USER", userId = "12345", firstName = "Itag", surname = "User", email = "a@b.com")
}