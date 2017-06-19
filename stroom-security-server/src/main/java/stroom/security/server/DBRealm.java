/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.server;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import stroom.entity.shared.EntityServiceException;
import stroom.node.server.StroomPropertyService;
import stroom.security.server.exception.AccountExpiredException;
import stroom.security.server.exception.DisabledException;
import stroom.security.server.exception.LockedException;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.security.shared.User.UserStatus;
import stroom.security.shared.UserRef;
import stroom.security.shared.UserService;

import javax.inject.Inject;
import java.util.regex.Pattern;

@Component
public class DBRealm extends AuthenticatingRealm {
    private static final Logger LOGGER = LoggerFactory.getLogger(DBRealm.class);

    public static final String ADMINISTRATORS = "Administrators";

    private final UserService userService;
    private final UserAppPermissionService userAppPermissionService;
    private final StroomPropertyService stroomPropertyService;

    private String cachedRegex;
    private Pattern cachedPattern;

    private volatile boolean doneCreateOrRefreshAdminRole = false;

    @Inject
    public DBRealm(final UserService userService, final UserAppPermissionService userAppPermissionService,
                   final StroomPropertyService stroomPropertyService,
                   final CredentialsMatcher matcher) {
        super(matcher);
        this.userService = userService;
        this.userAppPermissionService = userAppPermissionService;
        this.stroomPropertyService = stroomPropertyService;
    }

    @Override
    public boolean supports(final AuthenticationToken token) {
        return token != null
                && (token instanceof JWTAuthenticationToken);
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token)
            throws AuthenticationException {
        if (token instanceof JWTAuthenticationToken) {
            final JWTAuthenticationToken jwtAuthenticationToken = (JWTAuthenticationToken) token;
            return authenticateWithJWT(jwtAuthenticationToken);
        }
        throw new AuthenticationException("Token type '" + token.getClass().getSimpleName() + "' is not supported");
    }

    private AuthenticationInfo authenticateWithJWT(final JWTAuthenticationToken token)
            throws AuthenticationException {
        String userId = null;
        if (token != null && token.getUserId() != null) {
            userId = token.getUserId().toString();
        }

        final User user = loadUserByUsername(userId);

        if (StringUtils.hasText(userId) && user == null) {
            throw new EntityServiceException(userId + " does not exist");
        }

        if (user != null) {
            check(user);
            return new SimpleAuthenticationInfo(user, user.getPasswordHash(), getName());
        }

        return null;
    }

    private void check(final User user) {
        if (UserStatus.LOCKED.equals(user.getStatus())) {
            throw new LockedException("User account is locked");
        } else if (UserStatus.DISABLED.equals(user.getStatus())) {
            throw new DisabledException("User account has been deactivated");
        } else if (UserStatus.EXPIRED.equals(user.getStatus())) {
            throw new AccountExpiredException("User account has expired");
        } else if (!UserStatus.ENABLED.equals(user.getStatus())) {
            throw new DisabledException("User account is not enabled");
        }
    }

    private User loadUserByUsername(final String username) throws DataAccessException {
        User user = null;

        try {
            UserRef userRef = userService.getUserRefByName(username);

            if (userRef != null) {
                user = userService.loadByUuidInsecure(userRef.getUuid());
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }

        return user;
    }

    //TODO: Used by SetupSampleData, but should go because it's no longer used in prod code.
    /**
     * @return a new admin user
     */
    public UserRef createOrRefreshAdmin() {
        // Ensure all perms have been created
        userAppPermissionService.init();

        UserRef userRef = userService.getUserRefByName(UserService.INITIAL_ADMIN_ACCOUNT);
        if (userRef == null) {
            User user = new User();
            user.setName(UserService.INITIAL_ADMIN_ACCOUNT);
            // Save the admin account.
            user = userService.save(user);

            final UserRef userGroup = createOrRefreshAdminUserGroup();
            try {
                userService.addUserToGroup(UserRef.create(user), userGroup);
            } catch (final RuntimeException e) {
                // Expected.
                LOGGER.debug(e.getMessage());
            }

            userRef = UserRef.create(user);
        }

        return userRef;
    }

    //TODO: Used by SetupSampleData, but should go because it's no longer used in prod code.
    /**
     * Enusure the admin user groups are created
     *
     * @return the full admin user group
     */
    private UserRef createOrRefreshAdminUserGroup() {
        return createOrRefreshAdminUserGroup(ADMINISTRATORS);
    }

    //TODO: Used by SetupSampleData, but should go because it's no longer used in prod code.
    private UserRef createOrRefreshAdminUserGroup(final String userGroupName) {
        final FindUserCriteria findUserGroupCriteria = new FindUserCriteria(userGroupName, true);
        findUserGroupCriteria.getFetchSet().add(Permission.ENTITY_TYPE);

        User userGroup = userService.find(findUserGroupCriteria).getFirst();
        if (userGroup == null) {
            userGroup = userService.createUserGroup(userGroupName);

            try {
                userAppPermissionService.addPermission(UserRef.create(userGroup), PermissionNames.ADMINISTRATOR);
            } catch (final RuntimeException e) {
                // Expected.
                LOGGER.debug(e.getMessage());
            }
        }

        return UserRef.create(userGroup);
    }
}
