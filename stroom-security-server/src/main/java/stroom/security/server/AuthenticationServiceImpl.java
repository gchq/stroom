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

import event.logging.AuthenticateOutcomeReason;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.logging.AuthenticationEventLog;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.shared.EntityServiceException;
import stroom.node.server.StroomPropertyService;
import stroom.security.Insecure;
import stroom.security.Secured;
import stroom.security.SecurityContext;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.UserRef;
import stroom.security.shared.UserStatus;
import stroom.servlet.HttpServletRequestHolder;
import stroom.util.cert.CertificateUtil;
import stroom.util.config.StroomProperties;
import stroom.util.shared.UserTokenUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Component
@Secured(FindUserCriteria.MANAGE_USERS_PERMISSION)
public class AuthenticationServiceImpl implements AuthenticationService {
    private static final String USER_SESSION_KEY = AuthenticationServiceImpl.class.getName() + "_US";
    private static final String USER_ID_SESSION_KEY = AuthenticationServiceImpl.class.getName() + "_UID";
    private static final int DEFAULT_DAYS_TO_PASSWORD_EXPIRY = 90;
    private static final String PREVENT_LOGIN_PROPERTY = "stroom.maintenance.preventLogin";

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationServiceImpl.class);

    private final StroomPropertyService stroomPropertyService;
    private final transient HttpServletRequestHolder httpServletRequestHolder;
    private final AuthenticationEventLog eventLog;
    private final Provider<AuthenticationServiceMailSender> mailSenderProvider;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContext securityContext;

    @Inject
    AuthenticationServiceImpl(final StroomPropertyService stroomPropertyService, final HttpServletRequestHolder httpServletRequestHolder, final AuthenticationEventLog eventLog, final Provider<AuthenticationServiceMailSender> mailSenderProvider, final UserService userService, final PasswordEncoder passwordEncoder, final SecurityContext securityContext) {
        this.stroomPropertyService = stroomPropertyService;
        this.httpServletRequestHolder = httpServletRequestHolder;
        this.eventLog = eventLog;
        this.mailSenderProvider = mailSenderProvider;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.securityContext = securityContext;
    }

    private void checkLoginAllowed(final UserRef userRef) {
        if (userRef != null) {
            final boolean preventLogin = StroomProperties.getBooleanProperty(PREVENT_LOGIN_PROPERTY, false);
            if (preventLogin) {
                securityContext.pushUser(UserTokenUtil.create(userRef.getName(), null));
                try {
                    if (!securityContext.isAdmin()) {
                        throw new AuthenticationException("You are not allowed to login at this time");
                    }
                } finally {
                    securityContext.popUser();
                }
            }
        }
    }

    @Override
    @Insecure
    public UserRef login(final String userName, final String password) {
        UserRef userRef = null;

        if (userName == null || userName.length() == 0) {
            loginFailure(userName, new AuthenticationException("No user name"));

        } else {
            final HttpServletRequest request = httpServletRequestHolder.get();

            try {
                // Create the authentication token from the user name and
                // password
                final UsernamePasswordToken token = new UsernamePasswordToken(userName, password, true,
                        request.getRemoteHost());

                // Attempt authentication
                final Subject subject = SecurityUtils.getSubject();
                subject.login(token);

                userRef = (UserRef) subject.getPrincipal();
            } catch (final RuntimeException e) {
                loginFailure(userName, e);
            }

            // Ensure regular users are allowed to login at this time.
            checkLoginAllowed(userRef);

            try {
                // Pass back the user info
                userRef = handleLogin(request, userRef, userName);

                // Audit the successful logon
                if (userRef != null) {
                    eventLog.logon(userRef.getName());
                }

            } catch (final RuntimeException e) {
                loginFailure(userName, e);
            }
        }

        // Pass back the user info
        return userRef;
    }

    private void loginFailure(final String userName, final RuntimeException e) {
        // Audit logon failure.
        eventLog.logon(userName, false, e.getMessage(), AuthenticateOutcomeReason.INCORRECT_USERNAME_OR_PASSWORD);

        if (userName != null && userName.length() > 0) {
            final UserRef userRef = userService.getUserByName(userName);
            if (userRef != null) {
                // Increment the number of login failures.
                final User user = userService.loadByUuid(userRef.getUuid());
                if (user != null) {
                    user.setCurrentLoginFailures(user.getCurrentLoginFailures() + 1);
                    user.setTotalLoginFailures(user.getTotalLoginFailures() + 1);

                    if (user.getCurrentLoginFailures() > 3) {
                        LOGGER.error("login() - Locking account {} due to {} failures", user.getName(), user.getCurrentLoginFailures());
                        user.updateStatus(UserStatus.LOCKED);
                    }

                    userService.save(user);
                }
            }
        }

        LOGGER.warn("login() - {}", e.getMessage());

        try {
            throw e;
        } catch (IncorrectCredentialsException ice) {
            throw new EntityServiceException("Bad Credentials", null, false);
        } catch (final RuntimeException ex) {
            throw new EntityServiceException(ex.getMessage(), null, false);
        }
    }

    @Override
    @Insecure
    public String logout() {
        final HttpServletRequest request = httpServletRequestHolder.get();
        final UserRef user = getCurrentUser();

        if (user != null) {
            // Create an event for logout
            eventLog.logoff(user.getName());

            // Remove the user authentication object
            SecurityUtils.getSubject().logout();

            // Invalidate the current user session
            request.getSession().invalidate();

            return user.getName();
        }

        return null;
    }


    @Override
    @Insecure
    public UserRef changePassword(final UserRef userRef, final String oldPassword, final String newPassword) {
        if (userRef == null) {
            return null;
        }

        // Load the user for the supplied ref.
        final User user = userService.loadByUuid(userRef.getUuid());

        if (user == null) {
            return null;
        }

        // Check the old password again to authorise this change.
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            eventLog.changePassword(user.getName(), false, "The old password is incorrect!",
                    AuthenticateOutcomeReason.OTHER);
            throw new RuntimeException("The old password is incorrect!");
        }

        // Make sure the new password is not the same as the old password.
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            eventLog.changePassword(user.getName(), false,
                    "The new password cannot be the same as the old password!", AuthenticateOutcomeReason.OTHER);
            throw new RuntimeException("The new password cannot be the same as the old password!");
        }

        // Hash the new password.
        final String newHash = passwordEncoder.encode(newPassword);

        // Set the new password hash.
        user.setPasswordHash(newHash);

        // Set the expiry.
        user.setPasswordExpiryMs(getNextPasswordExpiryMs());

        // Write event log data for password change.
        eventLog.changePassword(user.getName());

        // Save the system user.
        return UserRefFactory.create(userService.save(user));
    }

    @Override
    public UserRef resetPassword(final UserRef userRef, final String password) {
        if (userRef == null) {
            return null;
        }

        // Add event log data for reset password.
        eventLog.resetPassword(userRef.getName(), false);

        // Make sure only a user with manage users permission can reset a password or that the user is resetting their own.
        if (!securityContext.hasAppPermission(FindUserCriteria.MANAGE_USERS_PERMISSION) && !userRef.equals(getCurrentUser())) {
            return null;
        }

        // Load the user for the supplied ref.
        final User user = userService.loadByUuid(userRef.getUuid());

        if (user == null) {
            return null;
        }

        return UserRefFactory.create(updatePassword(user, password));
    }

    private User updatePassword(final User user, final String newPassword) {
        // Hash the new password.
        final String newHash = passwordEncoder.encode(newPassword);

        // Set the new password hash.
        user.setPasswordHash(newHash);

        // Make sure it gets reset next time
        user.setPasswordExpiryMs(System.currentTimeMillis());

        if (UserStatus.LOCKED.equals(user.getStatus())) {
            user.updateStatus(UserStatus.ENABLED);
        }

        // Save the system user.
        return userService.save(user);
    }

    @Override
    public UserRef getCurrentUser() {
        if (sessionExists()) {
            final HttpServletRequest request = httpServletRequestHolder.get();
            return (UserRef) request.getSession().getAttribute(USER_SESSION_KEY);
        }

        return null;
    }

    private boolean sessionExists() {
        return httpServletRequestHolder.getSessionId() != null;
    }

    private Long getNextPasswordExpiryMs() {
        // Get the current number of milliseconds.
        ZonedDateTime expiryDate = ZonedDateTime.now(ZoneOffset.UTC);
        // Days to expiry will be 90 days.
        expiryDate = expiryDate.plusDays(getDaysToPasswordExpiry());

        return expiryDate.toInstant().toEpochMilli();
    }

    @Override
    public boolean canEmailPasswordReset() {
        return mailSenderProvider.get().canEmailPasswordReset();
    }

    private int getDaysToPasswordExpiry() {
        return stroomPropertyService.getIntProperty("stroom.daysToPasswordExpiry", DEFAULT_DAYS_TO_PASSWORD_EXPIRY);
    }

    @Override
    public Boolean emailPasswordReset(final String userName) throws RuntimeException {
        final UserRef userRef = userService.getUserByName(userName);
        if (userRef != null) {
            final User user = userService.loadByUuid(userRef.getUuid());
            if (user != null) {
                final String password = PasswordGenerator.generatePassword();
                final User updatedUser = updatePassword(user, password);
                mailSenderProvider.get().emailPasswordReset(UserRefFactory.create(updatedUser), password);
            }
        }
        return Boolean.TRUE;
    }

    @Override
    @Insecure
    public UserRef autoLogin() throws RuntimeException {
        UserRef userRef = getCurrentUser();
        if (userRef != null) {
            return userRef;
        }
        return loginWithCertificate();
    }

    private UserRef loginWithCertificate() {
        UserRef userRef;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("loginWithCertificate()");
        }

        final HttpServletRequest request = httpServletRequestHolder.get();
        final String certificateDn = CertificateUtil.extractCertificateDN(request);

        try {
            if (certificateDn == null) {
                return null;
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("loginWithCertificate() - certificateDn=" + certificateDn);
            }

            // Create the authentication token from the certificate
            final CertificateAuthenticationToken token = new CertificateAuthenticationToken(certificateDn, true,
                    request.getRemoteHost());

            // Attempt authentication
            final Subject subject = SecurityUtils.getSubject();
            subject.login(token);

            userRef = (UserRef) subject.getPrincipal();
        } catch (final RuntimeException ex) {
            final String message = EntityServiceExceptionUtil.unwrapMessage(ex, ex);

            // Audit the failed login
            eventLog.logon(certificateDn, false, message, AuthenticateOutcomeReason.OTHER);

            throw EntityServiceExceptionUtil.create(ex);
        }

        // Ensure regular users are allowed to login at this time.
        checkLoginAllowed(userRef);

        try {
            // Pass back the user info
            return handleLogin(request, userRef, certificateDn);

        } catch (final RuntimeException ex) {
            final String message = EntityServiceExceptionUtil.unwrapMessage(ex, ex);

            // Audit the failed login
            eventLog.logon(certificateDn, false, message, AuthenticateOutcomeReason.OTHER);

            throw EntityServiceExceptionUtil.create(ex);
        }
    }

    private UserRef handleLogin(final HttpServletRequest request, final UserRef userRef, final String userId) {
        if (userRef == null) {
            return null;
        }

        User user = userService.loadByUuid(userRef.getUuid());
        user.updateValidLogin();
        user = userService.save(user);

        final UserRef newRef = UserRefFactory.create(user);

        // Audit the successful login
        eventLog.logon(userId);

        final HttpSession session = request.getSession(true);
        session.setAttribute(USER_SESSION_KEY, newRef);
        session.setAttribute(USER_ID_SESSION_KEY, newRef.getName());

        return newRef;
    }
}
