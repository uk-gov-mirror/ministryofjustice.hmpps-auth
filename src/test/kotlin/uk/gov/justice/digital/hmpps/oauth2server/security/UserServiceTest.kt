package uk.gov.justice.digital.hmpps.oauth2server.security

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UsernameNotFoundException
import uk.gov.justice.digital.hmpps.oauth2server.auth.model.User
import uk.gov.justice.digital.hmpps.oauth2server.auth.model.UserFilter
import uk.gov.justice.digital.hmpps.oauth2server.auth.model.UserHelper.Companion.createSampleUser
import uk.gov.justice.digital.hmpps.oauth2server.auth.repository.UserRepository
import uk.gov.justice.digital.hmpps.oauth2server.azure.AzureUserPersonDetails
import uk.gov.justice.digital.hmpps.oauth2server.azure.service.AzureUserService
import uk.gov.justice.digital.hmpps.oauth2server.delius.model.DeliusUserPersonDetails
import uk.gov.justice.digital.hmpps.oauth2server.delius.service.DeliusUserService
import uk.gov.justice.digital.hmpps.oauth2server.maintain.AuthUserService
import uk.gov.justice.digital.hmpps.oauth2server.nomis.model.NomisUserPersonDetails
import uk.gov.justice.digital.hmpps.oauth2server.nomis.model.NomisUserPersonDetailsHelper.Companion.createSampleNomisUser
import uk.gov.justice.digital.hmpps.oauth2server.nomis.model.Staff
import uk.gov.justice.digital.hmpps.oauth2server.security.AuthSource.auth
import uk.gov.justice.digital.hmpps.oauth2server.security.AuthSource.azuread
import uk.gov.justice.digital.hmpps.oauth2server.security.AuthSource.delius
import uk.gov.justice.digital.hmpps.oauth2server.security.AuthSource.nomis
import uk.gov.justice.digital.hmpps.oauth2server.security.AuthSource.none
import uk.gov.justice.digital.hmpps.oauth2server.verify.VerifyEmailService
import java.util.Optional

class UserServiceTest {
  private val nomisUserService: NomisUserService = mock()
  private val authUserService: AuthUserService = mock()
  private val deliusUserService: DeliusUserService = mock()
  private val azureUserService: AzureUserService = mock()
  private val userRepository: UserRepository = mock()
  private val verifyEmailService: VerifyEmailService = mock()
  private val userService = UserService(
    nomisUserService,
    authUserService,
    deliusUserService,
    azureUserService,
    userRepository,
    verifyEmailService
  )

  @Nested
  inner class FindMasterUserPersonDetails {
    @Test
    fun `findMasterUserPersonDetails auth user`() {
      whenever(authUserService.getAuthUserByUsername(anyString())).thenReturn(createUser())
      val user = userService.findMasterUserPersonDetails("   bob   ")
      assertThat(user).isPresent.get().extracting { it.username }.isEqualTo("someuser")
    }

    @Test
    fun `findMasterUserPersonDetails nomis user`() {
      whenever(nomisUserService.getNomisUserByUsername(anyString())).thenReturn(staffUserAccountForBob)
      val user = userService.findMasterUserPersonDetails("bob")
      assertThat(user).isPresent.get().extracting { it.username }.isEqualTo("nomisuser")
    }

    @Test
    fun `findMasterUserPersonDetails delius user`() {
      whenever(deliusUserService.getDeliusUserByUsername(anyString())).thenReturn(deliusUserAccountForBob)
      val user = userService.findMasterUserPersonDetails("bob")
      assertThat(user).isPresent.get().extracting { it.username }.isEqualTo("deliusUser")
    }

    @Test
    fun `findMasterUserPersonDetails azure user`() {
      whenever(azureUserService.getAzureUserByUsername(anyString())).thenReturn(azureUserAccount)
      val user = userService.findMasterUserPersonDetails("bob")
      assertThat(user).isPresent.get().extracting { it.username }.isEqualTo("D6165AD0-AED3-4146-9EF7-222876B57549")
    }
  }

  @Nested
  inner class FindEnabledMasterUserPersonDetails {
    @Test
    fun `findEnabledMasterUserPersonDetails auth user`() {
      whenever(authUserService.getAuthUserByUsername(anyString())).thenReturn(
        Optional.of(
          createSampleUser(
            "someuser",
            enabled = true
          )
        )
      )
      val user = userService.findEnabledMasterUserPersonDetails("   bob   ")
      assertThat(user?.username).isEqualTo("someuser")
    }

    @Test
    fun `findEnabledMasterUserPersonDetails nomis user`() {
      whenever(nomisUserService.getNomisUserByUsername(anyString())).thenReturn(staffUserAccountForBob)
      val user = userService.findEnabledMasterUserPersonDetails("bob")
      assertThat(user?.username).isEqualTo("nomisuser")
    }

    @Test
    fun `findEnabledMasterUserPersonDetails delius user`() {
      whenever(deliusUserService.getDeliusUserByUsername(anyString())).thenReturn(deliusUserAccountForBob)
      val user = userService.findEnabledMasterUserPersonDetails("bob")
      assertThat(user?.username).isEqualTo("deliusUser")
    }

    @Test
    fun `findEnabledMasterUserPersonDetails azure user`() {
      whenever(azureUserService.getAzureUserByUsername(anyString())).thenReturn(azureUserAccount)
      val user = userService.findEnabledMasterUserPersonDetails("bob")
      assertThat(user?.username).isEqualTo("D6165AD0-AED3-4146-9EF7-222876B57549")
    }

    @Test
    fun `findEnabledMasterUserPersonDetails delius user auth user disabled`() {
      whenever(authUserService.getAuthUserByUsername(anyString())).thenReturn(createUser())
      whenever(deliusUserService.getDeliusUserByUsername(anyString())).thenReturn(deliusUserAccountForBob)
      val user = userService.findEnabledMasterUserPersonDetails("bob")
      assertThat(user?.username).isEqualTo("deliusUser")
    }
  }

  @Nested
  inner class GetEmailAddressFromNomis {
    @Test
    fun `getEmailAddressFromNomis no email addresses`() {
      whenever(verifyEmailService.getExistingEmailAddressesForUsername(anyString())).thenReturn(listOf())
      val optionalAddress = userService.getEmailAddressFromNomis("joe")
      assertThat(optionalAddress).isEmpty
    }

    @Test
    fun `getEmailAddressFromNomis not a justice email`() {
      whenever(verifyEmailService.getExistingEmailAddressesForUsername(anyString())).thenReturn(listOf("a@b.gov.uk"))
      val optionalAddress = userService.getEmailAddressFromNomis("joe")
      assertThat(optionalAddress).isEmpty
    }

    @Test
    fun `getEmailAddressFromNomis one justice email`() {
      whenever(verifyEmailService.getExistingEmailAddressesForUsername(anyString())).thenReturn(
        listOf(
          "Bob.smith@hmps.gsi.gov.uk",
          "Bob.smith@justice.gov.uk"
        )
      )
      val optionalAddress = userService.getEmailAddressFromNomis("joe")
      assertThat(optionalAddress).hasValue("Bob.smith@justice.gov.uk")
    }

    @Test
    fun `getEmailAddressFromNomis multiple justice emails`() {
      whenever(verifyEmailService.getExistingEmailAddressesForUsername(anyString())).thenReturn(
        listOf(
          "Bob.smith@hmps.gsi.gov.uk",
          "Bob.smith@justice.gov.uk",
          "Bob.smith2@justice.gov.uk"
        )
      )
      val optionalAddress = userService.getEmailAddressFromNomis("joe")
      assertThat(optionalAddress).isEmpty
    }
  }

  @Nested
  inner class GetOrCreateUser {

    @Test
    fun `getOrCreateUser user exists already`() {
      val user = createSampleUser("joe")
      whenever(userRepository.findByUsername("JOE")).thenReturn(Optional.of(user))
      val newUserOpt = userService.getOrCreateUser("joe")
      assertThat(newUserOpt).hasValueSatisfying {
        assertThat(it.username).isEqualTo("joe")
      }
    }

    @Test
    fun `getOrCreateUser migrate from NOMIS`() {
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.empty())
      whenever(authUserService.getAuthUserByUsername(anyString())).thenReturn(Optional.empty())
      whenever(nomisUserService.getNomisUserByUsername("joe")).thenReturn(
        Optional.of(createSampleNomisUser(username = "joe"))
      )
      whenever(verifyEmailService.getExistingEmailAddressesForUsername(anyString())).thenReturn(listOf())
      whenever(userRepository.save<User>(any())).thenAnswer { it.arguments[0] }

      val newUser = userService.getOrCreateUser("joe")
      assertThat(newUser).hasValueSatisfying {
        assertThat(it.username).isEqualTo("joe")
      }
    }

    @Test
    fun `getOrCreateUser migrate from NOMIS with email`() {
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.empty())
      whenever(authUserService.getAuthUserByUsername(anyString())).thenReturn(Optional.empty())
      whenever(nomisUserService.getNomisUserByUsername("joe")).thenReturn(
        Optional.of(createSampleNomisUser(username = "joe"))
      )
      whenever(verifyEmailService.getExistingEmailAddressesForUsername(anyString())).thenReturn(
        listOf("a@b.justice.gov.uk")
      )
      whenever(userRepository.save<User>(any())).thenAnswer { it.arguments[0] }

      val newUser = userService.getOrCreateUser("joe")
      assertThat(newUser).hasValueSatisfying {
        assertThat(it.username).isEqualTo("joe")
        assertThat(it.email).isEqualTo("a@b.justice.gov.uk")
        assertThat(it.verified).isTrue
        assertThat(it.authSource).isEqualTo(nomis.name)
      }
    }
  }

  @Nested
  inner class GetOrCreateUsers {

    @Test
    fun `user exists and other not found`() {
      val user = createSampleUser("joe")
      whenever(userRepository.findByUsername("JOE")).thenReturn(Optional.of(user))
      val newUserOpt = userService.getOrCreateUsers(listOf("joe", "fred"))
      assertThat(newUserOpt).containsExactly(user)
    }

    @Test
    fun `migrate from NOMIS`() {
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.empty())
      whenever(authUserService.getAuthUserByUsername(anyString())).thenReturn(Optional.empty())
      whenever(nomisUserService.getNomisUserByUsername("joe")).thenReturn(
        Optional.of(createSampleNomisUser(username = "joe"))
      )
      whenever(verifyEmailService.getExistingEmailAddressesForUsername(anyString())).thenReturn(listOf())
      whenever(userRepository.save<User>(any())).thenAnswer { it.arguments[0] }

      val newUser = userService.getOrCreateUsers(listOf("joe"))
      assertThat(newUser).hasSize(1)
    }
  }

  @Nested
  inner class FindUser {
    @Test
    fun findUser() {
      val user = createUser()
      whenever(userRepository.findByUsername(anyString())).thenReturn(user)
      val found = userService.findUser("bob")
      assertThat(found).isSameAs(user)
      verify(userRepository).findByUsername("BOB")
    }
  }

  @Nested
  inner class GetUser {
    @Test
    fun `getUser found`() {
      val user = createUser()
      whenever(userRepository.findByUsername(anyString())).thenReturn(user)
      val found = userService.getUser("bob")
      assertThat(found).isSameAs(user.orElseThrow())
      verify(userRepository).findByUsername("BOB")
    }

    @Test
    fun `getUser not found`() {
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.empty())
      assertThatThrownBy { userService.getUser("bob") }.isInstanceOf(UsernameNotFoundException::class.java)
    }
  }

  @Nested
  inner class HasVerifiedMfaMethod {
    @Test
    fun `hasVerifiedMfaMethod success`() {
      val user = createSampleUser(username = "joe", email = "someemail", verified = true)
      assertThat(userService.hasVerifiedMfaMethod(user)).isTrue
    }

    @Test
    fun `hasVerifiedMfaMethod no email`() {
      val user = createSampleUser(username = "joe", verified = true)
      assertThat(userService.hasVerifiedMfaMethod(user)).isFalse
    }

    @Test
    fun `hasVerifiedMfaMethod not verified`() {
      val user = createSampleUser(username = "joe", email = "someemail")
      assertThat(userService.hasVerifiedMfaMethod(user)).isFalse
    }
  }

  @Nested
  inner class IsSameAsCurrentVerifiedMobile {

    @Test
    fun `isSameAsCurrentVerifiedMobile not verified`() {
      val user = createSampleUser(mobile = "07700900001")
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user))
      val returnValue = userService.isSameAsCurrentVerifiedMobile("someuser", "")
      assertThat(returnValue).isFalse
    }

    @Test
    fun `isSameAsCurrentVerifiedMobile new different mobile number`() {
      val user = createSampleUser(mobile = "07700900001", mobileVerified = true)
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user))
      val returnValue = userService.isSameAsCurrentVerifiedMobile("someuser", "07700900000")
      assertThat(returnValue).isFalse
    }

    @Test
    fun `isSameAsCurrentVerifiedMobile new different mobile number with whitespace`() {
      val user = createSampleUser(mobile = "07700900001", mobileVerified = true)
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user))
      val returnValue = userService.isSameAsCurrentVerifiedMobile("someuser", "0770 090 0000")
      assertThat(returnValue).isFalse
    }

    @Test
    fun `isSameAsCurrentVerifiedMobile same mobile number`() {
      val user = createSampleUser(mobile = "07700900000", mobileVerified = true)
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user))
      val returnValue = userService.isSameAsCurrentVerifiedMobile("someuser", "07700900000")
      assertThat(returnValue).isTrue
    }

    @Test
    fun `isSameAsCurrentVerifiedMobile same mobile number with whitespace`() {
      val user = createSampleUser(mobile = "07700900000", mobileVerified = true)
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user))
      val returnValue = userService.isSameAsCurrentVerifiedMobile("someuser", "0770 090 0000")
      assertThat(returnValue).isTrue
    }
  }

  @Nested
  inner class isSameAsCurrentVerifiedEmail {
    @Test
    fun `isSameAsCurrentVerifiedEmail not verified primary email`() {
      val user = createSampleUser(email = "someemail", verified = false)
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user))
      val returnValue = userService.isSameAsCurrentVerifiedEmail("someuser", "someemail", User.EmailType.PRIMARY)
      assertThat(returnValue).isFalse
    }

    @Test
    fun `isSameAsCurrentVerifiedEmail new different email address primary email`() {
      val user = createSampleUser(email = "someemail", verified = true)
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user))
      val returnValue = userService.isSameAsCurrentVerifiedEmail("someuser", "somenewemail", User.EmailType.PRIMARY)
      assertThat(returnValue).isFalse
    }

    @Test
    fun `isSameAsCurrentVerifiedEmail same address primary email`() {
      val user = createSampleUser(email = "someemail", verified = true)
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user))
      val returnValue = userService.isSameAsCurrentVerifiedEmail("someuser", "someemail", User.EmailType.PRIMARY)
      assertThat(returnValue).isTrue
    }

    @Test
    fun `isSameAsCurrentVerifiedEmail not verified secondary email`() {
      val user = createSampleUser(secondaryEmail = "someemail")
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user))
      val returnValue = userService.isSameAsCurrentVerifiedEmail("someuser", "someemail", User.EmailType.SECONDARY)
      assertThat(returnValue).isFalse
    }

    @Test
    fun `isSameAsCurrentVerifiedEmail new different email address secondary email`() {
      val user = createSampleUser(secondaryEmail = "someemail", secondaryEmailVerified = true)
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user))
      val returnValue = userService.isSameAsCurrentVerifiedEmail("someuser", "somenewemail", User.EmailType.SECONDARY)
      assertThat(returnValue).isFalse
    }

    @Test
    fun `isSameAsCurrentVerifiedEmail same address secondary email`() {
      val user = createSampleUser(secondaryEmail = "someemail", secondaryEmailVerified = true)
      whenever(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user))
      val returnValue = userService.isSameAsCurrentVerifiedEmail("someuser", "someemail", User.EmailType.SECONDARY)
      assertThat(returnValue).isTrue
    }
  }

  @Nested
  inner class FindPrisonUsersByFirstAndLastNames {
    @Test
    fun `no matches`() {
      whenever(nomisUserService.findPrisonUsersByFirstAndLastNames("first", "last")).thenReturn(listOf())
      whenever(authUserService.findAuthUsersByUsernames(listOf())).thenReturn(listOf())
      whenever(verifyEmailService.getExistingEmailAddressesForUsernames(listOf())).thenReturn(mapOf())

      assertThat(userService.findPrisonUsersByFirstAndLastNames("first", "last")).isEmpty()
    }

    @Test
    fun `prison users only`() {

      whenever(authUserService.findAuthUsersByUsernames(listOf())).thenReturn(listOf())

      whenever(nomisUserService.findPrisonUsersByFirstAndLastNames("first", "last")).thenReturn(
        listOf(
          createSampleNomisUser(
            staff = Staff(firstName = "f1", lastName = "l1", staffId = 1, status = "INACTIVE"),
            username = "U1",
            activeCaseLoadId = null
          ),
          createSampleNomisUser(
            staff = Staff(firstName = "f2", lastName = "l2", staffId = 2, status = "INACTIVE"),
            username = "U2",
            activeCaseLoadId = null
          ),
          createSampleNomisUser(
            staff = Staff(firstName = "f3", lastName = "l3", staffId = 3, status = "INACTIVE"),
            username = "U3",
            activeCaseLoadId = "MDI"
          ),
        )
      )

      whenever(verifyEmailService.getExistingEmailAddressesForUsernames(anyList()))
        .thenReturn(
          mapOf(
            "U1" to mutableSetOf("u1@justice.gov.uk", "u1@somethingelse.gov.uk"),

            // Two matching e-mail suffixes results in no e-mail address.
            "U3" to mutableSetOf("u3@justice.gov.uk", "another-u3@justice.gov.uk")
          )
        )

      assertThat(userService.findPrisonUsersByFirstAndLastNames("first", "last"))

        .containsExactlyInAnyOrder(
          PrisonUserDto(
            username = "U1",
            email = "u1@justice.gov.uk",
            verified = true,
            userId = "1",
            firstName = "F1",
            lastName = "l1",
            activeCaseLoadId = null
          ),
          PrisonUserDto(
            username = "U2",
            email = null,
            verified = false,
            userId = "2",
            firstName = "F2",
            lastName = "l2",
            activeCaseLoadId = null
          ),
          PrisonUserDto(
            username = "U3",
            email = null,
            verified = false,
            userId = "3",
            firstName = "F3",
            lastName = "l3",
            activeCaseLoadId = "MDI"
          ),
        )
      verify(verifyEmailService).getExistingEmailAddressesForUsernames(listOf("U1", "U2", "U3"))
    }

    @Test
    fun `Prison users matched in auth`() {

      whenever(nomisUserService.findPrisonUsersByFirstAndLastNames("first", "last")).thenReturn(
        listOf(
          createSampleNomisUser(
            staff = Staff(firstName = "f1", lastName = "l1", staffId = 1, status = "INACTIVE"),
            username = "U1",
            activeCaseLoadId = "MDI"
          ),
          createSampleNomisUser(
            staff = Staff(firstName = "f2", lastName = "l2", staffId = 2, status = "INACTIVE"),
            username = "U2",
            activeCaseLoadId = null
          ),
          createSampleNomisUser(
            staff = Staff(firstName = "f3", lastName = "l3", staffId = 3, status = "INACTIVE"),
            username = "U3",
            activeCaseLoadId = "MDI"
          ),
        )
      )

      whenever(authUserService.findAuthUsersByUsernames(anyList())).thenReturn(
        listOf(
          createSampleUser(verified = true, source = nomis, username = "U1", email = "u1@b.com"),
          createSampleUser(verified = true, source = nomis, username = "U2", email = "u2@b.com"),
          createSampleUser(verified = false, source = nomis, username = "U3", email = "u3@b.com"),
        )
      )

      assertThat(userService.findPrisonUsersByFirstAndLastNames("first", "last"))
        .containsExactlyInAnyOrder(
          PrisonUserDto(
            username = "U1",
            email = "u1@b.com",
            verified = true,
            userId = "1",
            firstName = "F1",
            lastName = "l1",
            activeCaseLoadId = "MDI"
          ),
          PrisonUserDto(
            username = "U2",
            email = "u2@b.com",
            verified = true,
            userId = "2",
            firstName = "F2",
            lastName = "l2",
            activeCaseLoadId = null

          ),
          PrisonUserDto(
            username = "U3",
            email = "u3@b.com",
            verified = false,
            userId = "3",
            firstName = "F3",
            lastName = "l3",
            activeCaseLoadId = "MDI"
          ),
        )

      verify(verifyEmailService).getExistingEmailAddressesForUsernames(listOf())
    }

    @Test
    fun `Prison users partially matched in auth`() {

      whenever(nomisUserService.findPrisonUsersByFirstAndLastNames("first", "last")).thenReturn(
        listOf(
          createSampleNomisUser(
            staff = Staff(firstName = "f1", lastName = "l1", staffId = 1, status = "INACTIVE"),
            username = "U1",
            activeCaseLoadId = "MDI"
          ),
          createSampleNomisUser(
            staff = Staff(firstName = "f2", lastName = "l2", staffId = 2, status = "INACTIVE"),
            username = "U2",
            activeCaseLoadId = null
          ),
          createSampleNomisUser(
            staff = Staff(firstName = "f3", lastName = "l3", staffId = 3, status = "INACTIVE"),
            username = "U3",
            activeCaseLoadId = null
          ),
          createSampleNomisUser(
            staff = Staff(firstName = "f4", lastName = "l4", staffId = 4, status = "INACTIVE"),
            username = "U4",
            activeCaseLoadId = "MDI"
          ),
        )
      )

      whenever(authUserService.findAuthUsersByUsernames(anyList())).thenReturn(
        listOf(
          createSampleUser(verified = true, source = nomis, username = "U1", email = "u1@b.com"),
          // User U2 in auth, but no email - so search NOMIS for e-mail for this user
          createSampleUser(verified = true, source = nomis, username = "U2", email = null),
          // User U3 found in auth, but source is not nomis
          createSampleUser(verified = true, source = auth, username = "U3", email = "u3@b.com"),
        )
      )

      whenever(verifyEmailService.getExistingEmailAddressesForUsernames(anyList()))
        .thenReturn(
          mapOf(
            Pair("U2", mutableSetOf("u2@justice.gov.uk", "u2@somethingelse.gov.uk")),
            Pair("U3", mutableSetOf("u3@justice.gov.uk")),
          )
        )

      assertThat(userService.findPrisonUsersByFirstAndLastNames("first", "last"))
        .containsExactlyInAnyOrder(
          PrisonUserDto(
            username = "U1",
            email = "u1@b.com",
            verified = true,
            userId = "1",
            firstName = "F1",
            lastName = "l1",
            activeCaseLoadId = "MDI"
          ),
          PrisonUserDto(
            username = "U2",
            email = "u2@justice.gov.uk",
            verified = true,
            userId = "2",
            firstName = "F2",
            lastName = "l2",
            activeCaseLoadId = null
          ),
          PrisonUserDto(
            username = "U3",
            email = "u3@justice.gov.uk",
            verified = true,
            userId = "3",
            firstName = "F3",
            lastName = "l3",
            activeCaseLoadId = null
          ),
          PrisonUserDto(
            username = "U4",
            email = null,
            verified = false,
            userId = "4",
            firstName = "F4",
            lastName = "l4",
            activeCaseLoadId = "MDI"
          ),
        )

      verify(verifyEmailService).getExistingEmailAddressesForUsernames(listOf("U2", "U3", "U4"))
    }
  }

  @Nested
  inner class GetMasterUserPersonDetailsWithEmailCheck {
    @Test
    fun `test getMasterUserPersonDetailsWithEmailCheck - auth user`() {
      val authUser =
        Optional.of(createSampleUser(username = "bob", verified = true, email = "joe@fred.com"))
      whenever(authUserService.getAuthUserByUsername(anyString())).thenReturn(authUser)
      val details = userService.getMasterUserPersonDetailsWithEmailCheck("user", auth, "joe@fred.com")
      assertThat(details).isEqualTo(authUser)
    }

    @Test
    fun `test getMasterUserPersonDetailsWithEmailCheck - auth user email not verified`() {
      val authUser =
        Optional.of(createSampleUser(username = "bob", verified = false, email = "joe@fred.com"))
      whenever(authUserService.getAuthUserByUsername(anyString())).thenReturn(authUser)
      val details = userService.getMasterUserPersonDetailsWithEmailCheck("user", auth, "joe@fred.com")
      assertThat(details).isEmpty
    }

    @Test
    fun `test getMasterUserPersonDetailsWithEmailCheck - auth user not matched`() {
      val authUser =
        Optional.of(createSampleUser(username = "bob", verified = true, email = "harold@henry.com"))
      whenever(authUserService.getAuthUserByUsername(anyString())).thenReturn(authUser)
      val details = userService.getMasterUserPersonDetailsWithEmailCheck("user", auth, "joe@fred.com")
      assertThat(details).isEmpty
    }

    @Test
    fun `test getMasterUserPersonDetailsWithEmailCheck - nomis user`() {
      whenever(nomisUserService.getNomisUserByUsername(anyString())).thenReturn(staffUserAccountForBob)
      whenever(nomisUserService.getNomisUsersByEmail(anyString())).thenReturn(listOf(staffUserAccountForBob.get()))
      val details = userService.getMasterUserPersonDetailsWithEmailCheck("user", nomis, "joe@fred.com")
      assertThat(details).isEqualTo(staffUserAccountForBob)
    }

    @Test
    fun `test getMasterUserPersonDetailsWithEmailCheck - nomis user not matched`() {
      whenever(nomisUserService.getNomisUserByUsername(anyString())).thenReturn(staffUserAccountForBob)
      val details = userService.getMasterUserPersonDetailsWithEmailCheck("user", nomis, "joe@fred.com")
      assertThat(details).isEmpty
    }

    @Test
    fun `test getMasterUserPersonDetailsWithEmailCheck - delius user`() {
      whenever(deliusUserService.getDeliusUserByUsername(anyString())).thenReturn(deliusUserAccountForBob)
      val details = userService.getMasterUserPersonDetailsWithEmailCheck("user", delius, "a@b.com")
      assertThat(details).isEqualTo(deliusUserAccountForBob)
    }

    @Test
    fun `test getMasterUserPersonDetailsWithEmailCheck - delius user not matched`() {
      whenever(deliusUserService.getDeliusUserByUsername(anyString())).thenReturn(deliusUserAccountForBob)
      val details = userService.getMasterUserPersonDetailsWithEmailCheck("user", delius, "joe@fred.com")
      assertThat(details).isEmpty
    }

    @Test
    fun `test getMasterUserPersonDetailsWithEmailCheck - azuread user`() {
      whenever(azureUserService.getAzureUserByUsername(anyString())).thenReturn(azureUserAccount)
      val details = userService.getMasterUserPersonDetailsWithEmailCheck("user", azuread, "joe.bloggs@justice.gov.uk")
      assertThat(details).isEqualTo(azureUserAccount)
    }

    @Test
    fun `test getMasterUserPersonDetailsWithEmailCheck - none`() {
      val details = userService.getMasterUserPersonDetailsWithEmailCheck("user", none, "joe@fred.com")
      assertThat(details).isEmpty
    }
  }

  @Nested
  inner class UserSearchMultipleSources {
    @Test
    fun `test search user with multiple auth sources `() {
      val unpaged = Pageable.unpaged()
      whenever(authUserService.findAuthUsers(anyString(), anyOrNull(), anyOrNull(), any(), anyString(), anyList(), any(), any()))
        .thenReturn(Page.empty())

      userService.searchUsersInMultipleSourceSystems(
        "test", unpaged, "bob", AUTHORITY_INTEL_ADMIN, UserFilter.Status.ALL, listOf(nomis, auth)
      )

      verify(authUserService).findAuthUsers(
        "test", emptyList(), emptyList(), unpaged, "bob", AUTHORITY_INTEL_ADMIN, UserFilter.Status.ALL, listOf(nomis, auth)
      )
    }

    @Test
    fun `test search user with default auth source when not provided`() {
      val unpaged = Pageable.unpaged()
      whenever(authUserService.findAuthUsers(anyString(), anyOrNull(), anyOrNull(), any(), anyString(), anyList(), any(), any()))
        .thenReturn(Page.empty())

      userService.searchUsersInMultipleSourceSystems(
        "test", unpaged, "bob", AUTHORITY_INTEL_ADMIN, UserFilter.Status.ALL, null
      )

      verify(authUserService).findAuthUsers(
        "test", emptyList(), emptyList(), unpaged, "bob", AUTHORITY_INTEL_ADMIN, UserFilter.Status.ALL, listOf(AuthSource.auth)
      )
    }
  }

  private fun createUser() = Optional.of(createSampleUser(username = "someuser"))

  private val staffUserAccountForBob: Optional<NomisUserPersonDetails>
    get() =
      Optional.of(
        createSampleNomisUser(
          staff = Staff(
            firstName = "bOb",
            status = "ACTIVE",
            lastName = "bloggs",
            staffId = 5
          ),
          username = "nomisuser"
        )
      )

  private val deliusUserAccountForBob =
    Optional.of(DeliusUserPersonDetails("deliusUser", "12345", "Delius", "Smith", "a@b.com", true, false, setOf()))

  private val azureUserAccount =
    Optional.of(
      AzureUserPersonDetails(
        ArrayList(),
        true,
        "D6165AD0-AED3-4146-9EF7-222876B57549",
        "Joe",
        "Bloggs",
        "joe.bloggs@justice.gov.uk",
        true,
        accountNonExpired = true,
        accountNonLocked = true
      )
    )

  companion object {
    private val AUTHORITY_INTEL_ADMIN: Set<GrantedAuthority> =
      setOf(SimpleGrantedAuthority("ROLE_INTEL_ADMIN"))
  }
}
