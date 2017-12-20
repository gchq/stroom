/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.UserRef;
import stroom.util.shared.UserTokenUtil;

import javax.inject.Inject;

@Component
public class DBRealm extends AuthenticatingRealm {
    private static final Logger LOGGER = LoggerFactory.getLogger(DBRealm.class);

    private static final String ADMINISTRATORS = "Administrators";

    private final UserService userService;
    private final UserAppPermissionService userAppPermissionService;
    private final SecurityContext securityContext;

    private volatile boolean doneCreateOrRefreshAdminRole = false;

    @Inject
    public DBRealm(final UserService userService, final UserAppPermissionService userAppPermissionService,
                   final CredentialsMatcher matcher,
                   final SecurityContext securityContext) {
        super(matcher);
        this.userService = userService;
        this.userAppPermissionService = userAppPermissionService;
        this.securityContext = securityContext;
        createOrRefreshAdminUser();
        createOrRefreshStroomServiceUser();
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
            return doGetJwtAuthenticationInfo(jwtAuthenticationToken);
        }
        throw new AuthenticationException("Token type '" + token.getClass().getSimpleName() + "' is not supported");
    }

    private AuthenticationInfo doGetJwtAuthenticationInfo(final JWTAuthenticationToken token)
            throws AuthenticationException {
        String userId = null;
        if (token != null && token.getUserId() != null) {
            userId = token.getUserId().toString();
        }

        User user = loadUserByUsername(userId);

        if (StringUtils.hasText(userId) && user == null) {
            // At this point the user has been authenticated using JWT.
            // If the user doesn't exist in the DB then we need to create them an account here, so Stroom has
            // some way of sensibly referencing the user and something to attach permissions to.
            // We need to elevate the user because no one is currently logged in.
            try (SecurityHelper securityHelper = SecurityHelper.processingUser(securityContext)) {
                userService.createUser(userId);
            }
        }

        securityContext.pushUser(UserTokenUtil.create((String) token.getUserId(), null, token.getToken()));

        if (user != null) {
            return new SimpleAuthenticationInfo(UserRefFactory.create(user), user.getPasswordHash(), getName());
        }

        return null;
    }

    private User loadUserByUsername(final String username) throws DataAccessException {
        User user = null;

        try {
            if (!doneCreateOrRefreshAdminRole) {
                doneCreateOrRefreshAdminRole = true;
                createOrRefreshAdminUserGroup();
            }

            UserRef userRef = userService.getUserByName(username);
            if (userRef == null) {
                // The requested system user does not exist.
                if (UserService.ADMIN_USER_NAME.equals(username)) {
                    userRef = createOrRefreshAdminUser();
                }
            }

            if (userRef != null) {
                user = userService.loadByUuid(userRef.getUuid());
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }

        return user;
    }

    /**
     * @return a new admin user
     */
    public UserRef createOrRefreshAdminUser() {
        return createOrRefreshUser(UserService.ADMIN_USER_NAME);
    }

    /**
     * @return a new stroom service user in the admin group
     */
    public UserRef createOrRefreshStroomServiceUser() {
        return createOrRefreshUser(UserService.STROOM_SERVICE_USER_NAME);
    }

    private UserRef createOrRefreshUser(String name) {
        UserRef userRef;

        try (SecurityHelper securityHelper = SecurityHelper.processingUser(securityContext)) {
            // Ensure all perms have been created
            userAppPermissionService.init();

            userRef = userService.getUserByName(name);
            if (userRef == null) {
                User user = new User();
                user.setName(name);
                user = userService.save(user);

                final UserRef userGroup = createOrRefreshAdminUserGroup();
                try {
                    userService.addUserToGroup(UserRefFactory.create(user), userGroup);
                } catch (final RuntimeException e) {
                    // Expected.
                    LOGGER.debug(e.getMessage());
                }

                userRef = UserRefFactory.create(user);
            }
        }
        return userRef;
    }

    /**
     * Enusure the admin user groups are created
     *
     * @return the full admin user group
     */
    private UserRef createOrRefreshAdminUserGroup() {
        return createOrRefreshAdminUserGroup(ADMINISTRATORS);
    }

    private UserRef createOrRefreshAdminUserGroup(final String userGroupName) {
        UserRef newUserGroup;
        try (SecurityHelper securityHelper = SecurityHelper.processingUser(securityContext)) {
            final FindUserCriteria findUserGroupCriteria = new FindUserCriteria(userGroupName, true);
            findUserGroupCriteria.getFetchSet().add(Permission.ENTITY_TYPE);

            final User userGroup = userService.find(findUserGroupCriteria).getFirst();
            if (userGroup != null) {
                return UserRefFactory.create(userGroup);
            }

            newUserGroup = userService.createUserGroup(userGroupName);
            try {
                userAppPermissionService.addPermission(newUserGroup, PermissionNames.ADMINISTRATOR);
            } catch (final RuntimeException e) {
                // Expected.
                LOGGER.debug(e.getMessage());
            }
        }
        return newUserGroup;
    }
}
