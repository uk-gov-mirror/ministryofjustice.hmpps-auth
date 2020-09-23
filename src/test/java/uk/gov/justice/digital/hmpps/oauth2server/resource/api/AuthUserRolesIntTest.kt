package uk.gov.justice.digital.hmpps.oauth2server.resource.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.oauth2server.resource.DeliusExtension
import uk.gov.justice.digital.hmpps.oauth2server.resource.IntegrationTest

@ExtendWith(DeliusExtension::class)
class AuthUserRolesIntTest : IntegrationTest() {
  @Test
  fun `Auth User Roles add role endpoint adds a role to a user`() {
    webTestClient
        .put().uri("/auth/api/authuser/AUTH_RO_USER/roles/licence_vary")
        .headers(setAuthorisation("ITAG_USER_ADM", listOf("ROLE_MAINTAIN_OAUTH_USERS")))
        .exchange()
        .expectStatus().isNoContent

    checkRolesForUser("AUTH_RO_USER", listOf("GLOBAL_SEARCH", "LICENCE_RO", "LICENCE_VARY"))
  }

  @Test
  fun `Auth User Roles add role endpoint adds a role to a user that already exists`() {
    webTestClient
        .put().uri("/auth/api/authuser/AUTH_RO_USER/roles/licence_ro")
        .headers(setAuthorisation("ITAG_USER_ADM", listOf("ROLE_MAINTAIN_OAUTH_USERS")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody().json("""{error: "role.exists", error_description: "Username AUTH_RO_USER already has role licence_ro", field: "role"}""")
  }

  @Test
  fun `Auth User Roles add role endpoint adds a role to a user not in their group`() {
    webTestClient
        .put().uri("/auth/api/authuser/AUTH_ADM/roles/licence_vary")
        .headers(setAuthorisation("AUTH_GROUP_MANAGER", listOf("ROLE_AUTH_GROUP_MANAGER")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody().json("""{error: "unable to add role", error_description: "Unable to add role, the user is not within one of your groups", field: "role"}""")
  }

  @Test
  fun `Auth User Roles add role endpoint adds a role that doesn't exist`() {
    webTestClient
        .put().uri("/auth/api/authuser/AUTH_RO_USER_TEST/roles/licence_bob")
        .headers(setAuthorisation("ITAG_USER_ADM", listOf("ROLE_MAINTAIN_OAUTH_USERS")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody().json("""{error: "role.notfound", error_description: "role failed validation", field: "role"}""")
  }

  @Test
  fun `Auth User Roles add role endpoint adds a role requires role`() {
    webTestClient
        .put().uri("/auth/api/authuser/AUTH_RO_USER_TEST/roles/licence_bob")
        .headers(setAuthorisation("AUTH_GROUP_MANAGER", listOf("ROLE_GLOBAL_SEARCH")))
        .exchange()
        .expectStatus().isForbidden
        .expectBody().json("""{error: "access_denied", error_description: "Access is denied"}""")
  }

  @Test
  fun `Auth User Roles remove role endpoint removes a role from a user`() {
    webTestClient
        .delete().uri("/auth/api/authuser/AUTH_RO_USER_TEST/roles/licence_ro")
        .headers(setAuthorisation("ITAG_USER_ADM", listOf("ROLE_MAINTAIN_OAUTH_USERS")))
        .exchange()
        .expectStatus().isNoContent

    checkRolesForUser("AUTH_RO_USER_TEST", listOf("GLOBAL_SEARCH"))
  }

  @Test
  fun `Auth User Roles remove role endpoint removes a role from a user that isn't on the user`() {
    webTestClient
        .delete().uri("/auth/api/authuser/AUTH_RO_USER_TEST/roles/licence_bob")
        .headers(setAuthorisation("ITAG_USER_ADM", listOf("ROLE_MAINTAIN_OAUTH_USERS")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody().json("""{error: "role.notfound", error_description: "role failed validation", field: "role"}""")
  }

  @Test
  fun `Auth User Roles remove role endpoint removes a role from a user not in their group`() {
    webTestClient
        .delete().uri("/auth/api/authuser/AUTH_ADM/roles/licence_ro")
        .headers(setAuthorisation("AUTH_GROUP_MANAGER", listOf("ROLE_AUTH_GROUP_MANAGER")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody().json("""{error: "unable to remove role", error_description: "Unable to remove role, the user is not within one of your groups", field: "role"}""")
  }

  @Test
  fun `Auth User Roles remove role endpoint requires role`() {
    webTestClient
        .delete().uri("/auth/api/authuser/AUTH_ADM/roles/licence_ro")
        .headers(setAuthorisation("AUTH_GROUP_MANAGER", listOf("ROLE_GLOBAL_SEARCH")))
        .exchange()
        .expectStatus().isForbidden
        .expectBody().json("""{error: "access_denied", error_description: "Access is denied"}""")
  }

  @Test
  fun `Auth User Roles endpoint returns user roles`() {
    checkRolesForUser("auth_ro_vary_user", listOf("GLOBAL_SEARCH", "LICENCE_RO", "LICENCE_VARY"))
  }

  @Test
  fun `Auth User Roles endpoint returns user roles not allowed`() {
    webTestClient
        .get().uri("/auth/api/authuser/AUTH_ADM/roles")
        .exchange()
        .expectStatus().isUnauthorized
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody().json("""{error: "unauthorized", error_description: "Full authentication is required to access this resource"}""")
  }

  @Test
  fun `Auth Roles endpoint returns all assignable auth roles for a group for admin maintainer`() {
    webTestClient
        .get().uri("/auth/api/authuser/auth_ro_vary_user/assignable-roles")
        .headers(setAuthorisation("ITAG_USER_ADM", listOf("ROLE_MAINTAIN_OAUTH_USERS")))
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.[*].roleCode").value<List<String>> {
          assertThat(it).hasSizeGreaterThan(5)
          assertThat(it).contains("GLOBAL_SEARCH")
        }
  }

  @Test
  fun `Auth Roles endpoint returns all assignable auth roles for a group for group manager`() {
    webTestClient
        .get().uri("/auth/api/authuser/AUTH_RO_USER/assignable-roles")
        .headers(setAuthorisation("AUTH_GROUP_MANAGER", listOf("ROLE_AUTH_GROUP_MANAGER")))
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.[*].roleCode").value<List<String>> {
          assertThat(it).hasSize(3)
          assertThat(it).containsExactlyInAnyOrder("GLOBAL_SEARCH", "LICENCE_RO", "LICENCE_VARY")
        }
  }

  private fun checkRolesForUser(user: String, roles: List<String>) {
    webTestClient
        .get().uri("/auth/api/authuser/$user/roles")
        .headers(setAuthorisation("ITAG_USER_ADM"))
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.[*].roleCode").value<List<String>> {
          assertThat(it).containsExactlyInAnyOrderElementsOf(roles)
        }
  }
}

