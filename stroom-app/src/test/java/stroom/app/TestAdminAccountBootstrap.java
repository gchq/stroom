package stroom.app;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.cluster.lock.mock.MockClusterLockService;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.AppPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.config.PasswordPolicyConfig;
import stroom.security.identity.shared.Account;
import stroom.security.impl.StroomOpenIdConfig;
import stroom.security.mock.MockSecurityContext;
import stroom.security.openid.api.IdpType;
import stroom.security.shared.User;
import stroom.util.shared.UserRef;

import event.logging.EventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestAdminAccountBootstrap {

    @Mock
    private AccountService accountService;
    @Mock
    private AppPermissionService appPermissionService;
    @Mock
    private AppPermissionService userAppPermissionService;
    @Mock
    private IdentityConfig identityConfig;
    @Mock
    private PasswordPolicyConfig passwordPolicyConfig;
    @Mock
    private StroomEventLoggingService stroomEventLoggingService;
    @Mock
    private StroomOpenIdConfig stroomOpenIdConfig;
    @Mock
    private UserService userService;

    private final SecurityContext securityContext = MockSecurityContext.getInstance();
    private final ClusterLockService clusterLockService = new MockClusterLockService();

    @InjectMocks
    AdminAccountBootstrap adminAccountBootstrap;

    @Captor
    ArgumentCaptor<String> typeIdCaptor;

    @BeforeEach
    void setUp() {
        adminAccountBootstrap = new AdminAccountBootstrap(
                accountService,
                appPermissionService,
                userAppPermissionService,
                clusterLockService,
                () -> identityConfig,
                securityContext,
                stroomEventLoggingService,
                () -> stroomOpenIdConfig,
                userService);
    }

    @Test
    void testNotEnabled() {
        Mockito.when(identityConfig.isAutoCreateAdminAccountOnBoot())
                .thenReturn(false);

        adminAccountBootstrap.startup();

        Mockito.verify(stroomEventLoggingService, Mockito.never())
                .log(Mockito.anyString(), Mockito.anyString(), Mockito.any(EventAction.class));
    }

    @Test
    void testEnabled_WrongIdpType() {
        Mockito.when(identityConfig.isAutoCreateAdminAccountOnBoot())
                .thenReturn(true);
        Mockito.when(stroomOpenIdConfig.getIdentityProviderType())
                .thenReturn(IdpType.EXTERNAL_IDP);

        adminAccountBootstrap.startup();

        Mockito.verify(stroomEventLoggingService, Mockito.never())
                .log(Mockito.anyString(), Mockito.anyString(), Mockito.any(EventAction.class));
    }

    @Test
    void testEnabled_NoAction() {
        Mockito.when(identityConfig.isAutoCreateAdminAccountOnBoot())
                .thenReturn(true);
        Mockito.when(stroomOpenIdConfig.getIdentityProviderType())
                .thenReturn(IdpType.INTERNAL_IDP);
        Mockito.when(accountService.read(Mockito.eq(AdminAccountBootstrap.ADMIN_ACCOUNT_NAME)))
                .thenReturn(Optional.of(Mockito.mock(Account.class)));
        Mockito.when(userService.getUserBySubjectId(Mockito.eq(AdminAccountBootstrap.ADMIN_ACCOUNT_NAME)))
                .thenReturn(Optional.of(Mockito.mock(User.class)));
        final User group = Mockito.mock(User.class);
        Mockito.when(userService.getGroupByName(Mockito.eq(AdminAccountBootstrap.ADMINISTRATION_GROUP_NAME)))
                .thenReturn(Optional.of(group));
        final UserRef groupRef = UserRef.builder()
                .subjectId(AdminAccountBootstrap.ADMINISTRATION_GROUP_NAME)
                .uuid("123")
                .build();
        Mockito.when(group.asRef())
                .thenReturn(groupRef);
        Mockito.when(appPermissionService.getDirectAppUserPermissions(Mockito.eq(groupRef)))
                .thenReturn(AdminAccountBootstrap.ADMINISTRATION_GROUP_PERMS);

        adminAccountBootstrap.startup();

        Mockito.verify(stroomEventLoggingService, Mockito.never())
                .log(Mockito.anyString(), Mockito.anyString(), Mockito.any(EventAction.class));
    }

    @Test
    void testEnabled_createAll() {
        Mockito.when(identityConfig.isAutoCreateAdminAccountOnBoot())
                .thenReturn(true);
        Mockito.when(identityConfig.getPasswordPolicyConfig())
                .thenReturn(passwordPolicyConfig);
        Mockito.when(passwordPolicyConfig.isForcePasswordChangeOnFirstLogin())
                .thenReturn(false);
        Mockito.when(stroomOpenIdConfig.getIdentityProviderType())
                .thenReturn(IdpType.INTERNAL_IDP);
        Mockito.when(accountService.read(Mockito.eq(AdminAccountBootstrap.ADMIN_ACCOUNT_NAME)))
                .thenReturn(Optional.empty());

        final User user = Mockito.mock(User.class);
        Mockito.when(userService.getOrCreateUser(Mockito.eq(AdminAccountBootstrap.ADMIN_ACCOUNT_NAME)))
                .thenReturn(user);
        Mockito.when(userService.getUserBySubjectId(Mockito.eq(AdminAccountBootstrap.ADMIN_ACCOUNT_NAME)))
                .thenReturn(Optional.empty());
        final UserRef userRef = UserRef.builder()
                .subjectId(AdminAccountBootstrap.ADMIN_ACCOUNT_NAME)
                .uuid("456")
                .build();
        Mockito.when(user.asRef())
                .thenReturn(userRef);

        Mockito.when(userService.getGroupByName(Mockito.eq(AdminAccountBootstrap.ADMINISTRATION_GROUP_NAME)))
                .thenReturn(Optional.empty());
        final User group = Mockito.mock(User.class);
        final UserRef groupRef = UserRef.builder()
                .subjectId(AdminAccountBootstrap.ADMINISTRATION_GROUP_NAME)
                .uuid("123")
                .build();
        Mockito.when(userService.getOrCreateUserGroup(Mockito.eq(AdminAccountBootstrap.ADMINISTRATION_GROUP_NAME)))
                .thenReturn(group);
        Mockito.when(group.asRef())
                .thenReturn(groupRef);

        adminAccountBootstrap.startup();

        Mockito.verify(stroomEventLoggingService, Mockito.times(4))
                .log(typeIdCaptor.capture(), Mockito.anyString(), Mockito.any(EventAction.class));
        assertThat(typeIdCaptor.getAllValues())
                .containsExactly("AdminAccountBootstrap.createAccount",
                        "AdminAccountBootstrap.createUser",
                        "AdminAccountBootstrap.createGroup",
                        "AdminAccountBootstrap.addPermissionToGroup");
    }
}
