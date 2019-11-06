package uk.gov.justice.digital.hmpps.oauth2server.security;

import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import uk.gov.justice.digital.hmpps.oauth2server.auth.model.Authority;
import uk.gov.justice.digital.hmpps.oauth2server.auth.model.Group;
import uk.gov.justice.digital.hmpps.oauth2server.auth.model.User;
import uk.gov.justice.digital.hmpps.oauth2server.auth.repository.UserRepository;
import uk.gov.justice.digital.hmpps.oauth2server.nomis.model.AccountDetail;
import uk.gov.justice.digital.hmpps.oauth2server.nomis.model.Staff;
import uk.gov.justice.digital.hmpps.oauth2server.nomis.model.StaffUserAccount;
import uk.gov.justice.digital.hmpps.oauth2server.nomis.repository.StaffIdentifierRepository;
import uk.gov.justice.digital.hmpps.oauth2server.nomis.repository.StaffUserAccountRepository;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private StaffUserAccountRepository staffUserAccountRepository;
    @Mock
    private StaffIdentifierRepository staffIdentifierRepository;
    @Mock
    private TelemetryClient telemetryClient;

    private UserService userService;

    @Mock
    private MaintainUserCheck maintainUserCheck;

    private static final Set<GrantedAuthority> SUPER_USER = Set.of(new SimpleGrantedAuthority("ROLE_MAINTAIN_OAUTH_USERS"));
    private static final Set<GrantedAuthority> GROUP_MANAGER = Set.of(new SimpleGrantedAuthority("ROLE_AUTH_GROUP_MANAGER"));


    @Before
    public void setUp() {
        userService = new UserService(staffUserAccountRepository, staffIdentifierRepository, userRepository, telemetryClient, maintainUserCheck);
    }

    @Test
    public void findUser_AuthUser() {
        when(userRepository.findByUsernameAndMasterIsTrue(anyString())).thenReturn(createUser());

        final var user = userService.findUser("   bob   ");

        assertThat(user).isPresent().get().extracting(UserPersonDetails::getUsername).isEqualTo("someuser");

        verify(userRepository).findByUsernameAndMasterIsTrue("BOB");
    }

    @Test
    public void findUser_NomisUser() {
        when(userRepository.findByUsernameAndMasterIsTrue(anyString())).thenReturn(Optional.empty());
        when(staffUserAccountRepository.findById(anyString())).thenReturn(getStaffUserAccountForBob());

        final var user = userService.findUser("bob");

        assertThat(user).isPresent().get().extracting(UserPersonDetails::getUsername).isEqualTo("nomisuser");

        verify(userRepository).findByUsernameAndMasterIsTrue("BOB");
        verify(staffUserAccountRepository).findById("BOB");
    }

    @Test
    public void findByEmailAndMasterIsTrue() {
        when(userRepository.findByEmailAndMasterIsTrueOrderByUsername(anyString())).thenReturn(List.of(User.of("someuser")));

        final var user = userService.findAuthUsersByEmail("  bob  ");

        assertThat(user).extracting(UserPersonDetails::getUsername).containsOnly("someuser");
    }

    @Test
    public void findAuthUsersByEmail_formatEmailAddress() {
        when(userRepository.findByEmailAndMasterIsTrueOrderByUsername(anyString())).thenReturn(List.of(User.of("someuser")));

        userService.findAuthUsersByEmail("  some.u’ser@SOMEwhere  ");

        verify(userRepository).findByEmailAndMasterIsTrueOrderByUsername("some.u'ser@somewhere");
    }

    @Test
    public void enableUser_superUser() throws MaintainUserCheck.AuthUserGroupRelationshipException {
        final var optionalUserEmail = createUser();
        when(userRepository.findByUsernameAndMasterIsTrue(anyString())).thenReturn(optionalUserEmail);
        userService.enableUser("user", "admin", SUPER_USER);
        assertThat(optionalUserEmail).get().extracting(User::isEnabled).isEqualTo(Boolean.TRUE);
        verify(userRepository).save(optionalUserEmail.orElseThrow());
    }

    @Test
    public void enableUser_invalidGroup_GroupManager() throws MaintainUserCheck.AuthUserGroupRelationshipException {
        final var optionalUserEmail = createUser();
        when(userRepository.findByUsernameAndMasterIsTrue(anyString())).thenReturn(optionalUserEmail);
        doThrow(new MaintainUserCheck.AuthUserGroupRelationshipException("someuser", "User not with your groups")).when(maintainUserCheck).ensureUserLoggedInUserRelationship(anyString(), anyCollection(), any(User.class));
        assertThatThrownBy(() -> userService.enableUser("someuser", "admin", GROUP_MANAGER)).
                isInstanceOf(MaintainUserCheck.AuthUserGroupRelationshipException.class).hasMessage("Unable to maintain user: someuser with reason: User not with your groups");
    }

    @Test
    public void enableUser_validGroup_groupManager() throws MaintainUserCheck.AuthUserGroupRelationshipException {
        final var user = User.of("user");
        final var group1 = new Group("group", "desc");
        user.setGroups(Set.of(group1, new Group("group2", "desc")));

        user.setAuthorities(new HashSet<>(List.of(new Authority("JOE", "bloggs"))));
        final var groupManager = User.of("groupManager");
        groupManager.setGroups(Set.of(new Group("group3", "desc"), group1));
        when(userRepository.findByUsernameAndMasterIsTrue(anyString()))
                .thenReturn(Optional.of(user));

        userService.enableUser("user", "admin", GROUP_MANAGER);

        assertThat(user).extracting(User::isEnabled).isEqualTo(Boolean.TRUE);
        verify(userRepository).save(user);
    }

    @Test
    public void enableUser_NotFound() {
        when(userRepository.findByUsernameAndMasterIsTrue(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.enableUser("user", "admin", SUPER_USER)).isInstanceOf(EntityNotFoundException.class).hasMessageContaining("username user");
    }

    @Test
    public void enableUser_trackEvent() throws MaintainUserCheck.AuthUserGroupRelationshipException {
        final var optionalUserEmail = createUser();
        when(userRepository.findByUsernameAndMasterIsTrue(anyString())).thenReturn(optionalUserEmail);
        doNothing().when(maintainUserCheck).ensureUserLoggedInUserRelationship(anyString(), anyCollection(), any(User.class));
        userService.enableUser("someuser", "someadmin", SUPER_USER);
        verify(telemetryClient).trackEvent("AuthUserChangeEnabled", Map.of("username", "someuser", "admin", "someadmin", "enabled", "true"), null);
    }

    @Test
    public void disableUser_superUser() throws MaintainUserCheck.AuthUserGroupRelationshipException {
        final var optionalUserEmail = createUser();
        when(userRepository.findByUsernameAndMasterIsTrue(anyString())).thenReturn(optionalUserEmail);
        doNothing().when(maintainUserCheck).ensureUserLoggedInUserRelationship(anyString(), anyCollection(), any(User.class));
        userService.disableUser("user", "admin", SUPER_USER);
        assertThat(optionalUserEmail).get().extracting(User::isEnabled).isEqualTo(Boolean.FALSE);
        verify(userRepository).save(optionalUserEmail.orElseThrow());
    }

    @Test
    public void disableUser_invalidGroup_GroupManager() throws MaintainUserCheck.AuthUserGroupRelationshipException {
        final var optionalUserEmail = createUser();
        when(userRepository.findByUsernameAndMasterIsTrue(anyString())).thenReturn(optionalUserEmail);
        doThrow(new MaintainUserCheck.AuthUserGroupRelationshipException("someuser", "User not with your groups")).when(maintainUserCheck).ensureUserLoggedInUserRelationship(anyString(), anyCollection(), any(User.class));
        assertThatThrownBy(() -> userService.disableUser("someuser", "admin", GROUP_MANAGER)).
                isInstanceOf(MaintainUserCheck.AuthUserGroupRelationshipException.class).hasMessage("Unable to maintain user: someuser with reason: User not with your groups");
    }

    @Test
    public void disableUser_validGroup_groupManager() throws MaintainUserCheck.AuthUserGroupRelationshipException {
        final var user = User.of("user");
        final var group1 = new Group("group", "desc");
        user.setGroups(Set.of(group1, new Group("group2", "desc")));
        user.setEnabled(true);

        user.setAuthorities(new HashSet<>(List.of(new Authority("JOE", "bloggs"))));
        final var groupManager = User.of("groupManager");
        groupManager.setGroups(Set.of(new Group("group3", "desc"), group1));
        when(userRepository.findByUsernameAndMasterIsTrue(anyString()))
                .thenReturn(Optional.of(user));

        userService.disableUser("user", "admin", GROUP_MANAGER);

        assertThat(user).extracting(User::isEnabled).isEqualTo(Boolean.FALSE);
        verify(userRepository).save(user);
    }

    @Test
    public void disableUser_trackEvent() throws MaintainUserCheck.AuthUserGroupRelationshipException {
        final var optionalUserEmail = createUser();
        when(userRepository.findByUsernameAndMasterIsTrue(anyString())).thenReturn(optionalUserEmail);
        userService.disableUser("someuser", "someadmin", SUPER_USER);
        verify(telemetryClient).trackEvent("AuthUserChangeEnabled", Map.of("username", "someuser", "admin", "someadmin", "enabled", "false"), null);
    }

    @Test
    public void disableUser_notFound() {
        when(userRepository.findByUsernameAndMasterIsTrue(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.disableUser("user", "admin", SUPER_USER)).isInstanceOf(EntityNotFoundException.class).hasMessageContaining("username user");
    }

    @Test
    public void findUserEmail() {
        final var user = createUser();
        when(userRepository.findByUsername(anyString())).thenReturn(user);

        final var found = userService.findAuthUser("bob");

        assertThat(found).isSameAs(user);
        verify(userRepository).findByUsername("BOB");
    }

    private Optional<User> createUser() {
        return Optional.of(User.of("someuser"));
    }

    private Optional<StaffUserAccount> getStaffUserAccountForBob() {
        final var staffUserAccount = new StaffUserAccount();
        staffUserAccount.setUsername("nomisuser");
        final var staff = new Staff();
        staff.setFirstName("bOb");
        staff.setStatus("ACTIVE");
        staffUserAccount.setStaff(staff);
        final var detail = new AccountDetail("user", "OPEN", "profile", null);
        staffUserAccount.setAccountDetail(detail);
        return Optional.of(staffUserAccount);
    }
}
