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

import org.apache.shiro.authc.AccountException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
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
import stroom.security.server.exception.BadCredentialsException;
import stroom.security.server.exception.DisabledException;
import stroom.security.server.exception.LockedException;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.UserRef;
import stroom.security.shared.UserStatus;
import stroom.util.cert.CertificateUtil;
import stroom.util.shared.EqualsUtil;

import javax.inject.Inject;
import java.util.regex.Pattern;

@Component
public class DBRealm extends AuthenticatingRealm {
    private static final Logger LOGGER = LoggerFactory.getLogger(DBRealm.class);

    private static final String ADMINISTRATORS = "Administrators";

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
        return token != null && (token instanceof UsernamePasswordToken ||
                token instanceof CertificateAuthenticationToken ||
                token instanceof JWTAuthenticationToken);
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token)
            throws AuthenticationException {
        if (token instanceof JWTAuthenticationToken) {
            final JWTAuthenticationToken jwtAuthenticationToken = (JWTAuthenticationToken) token;
            return authenticateWithJWT(jwtAuthenticationToken);
        }

        if (token instanceof CertificateAuthenticationToken) {
            final CertificateAuthenticationToken certificateAuthenticationToken = (CertificateAuthenticationToken) token;
            return authenticateWithCertificate(certificateAuthenticationToken);
        }

        if (token instanceof UsernamePasswordToken) {
            final UsernamePasswordToken usernamePasswordToken = (UsernamePasswordToken) token;
            return authenticateWithUsernamePassword(usernamePasswordToken);
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
            return new SimpleAuthenticationInfo(UserRefFactory.create(user), user.getPasswordHash(), getName());
        }

        return null;
    }

    private AuthenticationInfo authenticateWithCertificate(final CertificateAuthenticationToken token)
            throws AuthenticationException {
        try {
            if (!allowCertificateAuthentication()) {
                throw new EntityServiceException("Certificate authentication is not allowed");
            }

            final Pattern pattern = getPattern();

            if (pattern == null) {
                throw new EntityServiceException("No valid certificateDNPattern found");
            }

            final String dn = (String) token.getCredentials();
            final String username = CertificateUtil.extractUserIdFromDN(dn, pattern);

            if (LOGGER.isDebugEnabled()) {
                final String cn = CertificateUtil.extractCNFromDN(dn);
                LOGGER.debug("authenticate() - dn=" + dn + ", cn=" + cn + ", userId=" + username);
            }

            final User user = loadUserByUsername(username);

            if (StringUtils.hasText(username) && user == null) {
                throw new EntityServiceException(username + " does not exist");
            }

            if (user != null) {
                check(user);
                return new SimpleAuthenticationInfo(UserRefFactory.create(user), user.getPasswordHash(), getName());
            }
        } catch (final AuthenticationException e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new BadCredentialsException(e.getMessage());
        }

        return null;
    }

    private AuthenticationInfo authenticateWithUsernamePassword(final UsernamePasswordToken token)
            throws AuthenticationException {
        try {
            final String username = token.getUsername();

            // Null username is invalid
            if (username == null) {
                throw new AccountException("Null user names are not allowed by this realm.");
            }

            final User user = loadUserByUsername(username);

            if (StringUtils.hasText(username) && user == null) {
                throw new BadCredentialsException("Bad Credentials");
            }

            if (user != null) {
                check(user);
                return new SimpleAuthenticationInfo(UserRefFactory.create(user), user.getPasswordHash(), getName());
            }
        } catch (final AuthenticationException e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new BadCredentialsException(e.getMessage());
        }

        return null;
    }

    private boolean allowCertificateAuthentication() {
        return stroomPropertyService.getBooleanProperty("stroom.security.allowCertificateAuthentication", false);
    }

    private Pattern getPattern() {
        final String regex = stroomPropertyService.getProperty("stroom.security.certificateDNPattern");
        if (!EqualsUtil.isEquals(cachedRegex, regex)) {
            cachedRegex = regex;
            cachedPattern = null;

            if (regex != null) {
                try {
                    cachedPattern = Pattern.compile(regex);
                } catch (final RuntimeException e) {
                    final String message = "Problem compiling certificateDNPattern regex: " + e.getMessage();
                    LOGGER.error(message, e);
                    throw new EntityServiceException(message);
                }
            }
        }

        return cachedPattern;
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
            if (!doneCreateOrRefreshAdminRole) {
                doneCreateOrRefreshAdminRole = true;
                createOrRefreshAdminUserGroup();
            }

            UserRef userRef = userService.getUserByName(username);
            if (userRef == null) {
                // The requested system user does not exist.
                if (UserService.INITIAL_ADMIN_ACCOUNT.equals(username)) {
                    userRef = createOrRefreshAdmin();
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
    public UserRef createOrRefreshAdmin() {
        // Ensure all perms have been created
        userAppPermissionService.init();

        UserRef userRef = userService.getUserByName(UserService.INITIAL_ADMIN_ACCOUNT);
        if (userRef == null) {
            User user = new User();
            user.setName(UserService.INITIAL_ADMIN_ACCOUNT);
            // Save the admin account.
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
        final FindUserCriteria findUserGroupCriteria = new FindUserCriteria(userGroupName, true);
        findUserGroupCriteria.getFetchSet().add(Permission.ENTITY_TYPE);

        final User userGroup = userService.find(findUserGroupCriteria).getFirst();
        if (userGroup != null) {
            return UserRefFactory.create(userGroup);
        }

        final UserRef newUserGroup = userService.createUserGroup(userGroupName);
        try {
            userAppPermissionService.addPermission(newUserGroup, PermissionNames.ADMINISTRATOR);
        } catch (final RuntimeException e) {
            // Expected.
            LOGGER.debug(e.getMessage());
        }

        return newUserGroup;
    }
}
