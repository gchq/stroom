/*
 * Copyright 2016 Crown Copyright
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

import stroom.entity.shared.EntityServiceException;
import stroom.node.server.StroomPropertyService;
import stroom.security.server.exception.AccountExpiredException;
import stroom.security.server.exception.BadCredentialsException;
import stroom.security.server.exception.DisabledException;
import stroom.security.server.exception.LockedException;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.security.shared.User.UserStatus;
import stroom.security.shared.UserRef;
import stroom.security.shared.UserService;
import stroom.util.cert.CertificateUtil;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.EqualsUtil;
import org.apache.shiro.authc.AccountException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.inject.Inject;
import java.util.regex.Pattern;

@Component
public class DBRealm extends AuthenticatingRealm {
    public static final StroomLogger LOGGER = StroomLogger.getLogger(DBRealm.class);

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
                && (token instanceof UsernamePasswordToken || token instanceof CertificateAuthenticationToken);
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token)
            throws AuthenticationException {
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

    private AuthenticationInfo authenticateWithCertificate(final CertificateAuthenticationToken token)
            throws AuthenticationException {
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
            return new SimpleAuthenticationInfo(user, user.getPasswordHash(), getName());
        }

        return null;
    }

    private AuthenticationInfo authenticateWithUsernamePassword(final UsernamePasswordToken token)
            throws AuthenticationException {
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
            return new SimpleAuthenticationInfo(user, user.getPasswordHash(), getName());
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
        if (!doneCreateOrRefreshAdminRole) {
            doneCreateOrRefreshAdminRole = true;
            createOrRefreshAdminUserGroup();
        }

        UserRef userRef = userService.getUserByName(username);
        User user = null;
        if (userRef == null) {
            // The requested system user does not exist.
            if (UserService.INITIAL_ADMIN_ACCOUNT.equals(username)) {
                userRef = createOrRefreshAdmin();
            }
        }

        if (userRef != null) {
            user = userService.loadByUuid(userRef.getUuid());
        }

//        // If the requested user doesn't exist then return null.
//        if (user == null) {
//            return null;
//        }
//
//        final String message = getMaintenanceMessage();
//        if (StringUtils.hasText(message)) {
//            throw new EntityServiceException(message);
//        }

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
                userService.addUserToGroup(UserRef.create(user), userGroup);
            } catch (final RuntimeException e) {
                // Expected.
                LOGGER.debug(e.getMessage());
            }

            userRef = UserRef.create(user);
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
