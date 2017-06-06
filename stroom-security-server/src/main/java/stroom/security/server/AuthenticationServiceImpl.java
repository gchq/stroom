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

import event.logging.AuthenticateOutcomeReason;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.shared.EntityServiceException;
import stroom.logging.AuthenticationEventLog;
import stroom.node.server.StroomPropertyService;
import stroom.security.Insecure;
import stroom.security.Secured;
import stroom.security.SecurityContext;
import stroom.security.shared.User;
import stroom.security.shared.User.UserStatus;
import stroom.security.shared.UserRef;
import stroom.security.shared.UserService;
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
@Secured(User.MANAGE_USERS_PERMISSION)
public class AuthenticationServiceImpl implements AuthenticationService {
    public static final String USER_SESSION_KEY = AuthenticationServiceImpl.class.getName() + "_US";
    public static final String USER_ID_SESSION_KEY = AuthenticationServiceImpl.class.getName() + "_UID";
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

    private void checkLoginAllowed(final User user) {
        if (user != null) {
            final boolean preventLogin = StroomProperties.getBooleanProperty(PREVENT_LOGIN_PROPERTY, false);
            if (preventLogin) {
                securityContext.pushUser(UserTokenUtil.create(user.getName(), null));
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

    /**
     * @param userName
     * @param password
     * @return
     */
    @Override
    @Insecure
    public User login(final String userName, final String password) {
        User user = null;

        if (userName == null || userName.length() == 0) {
            loginFailure(userName, new AuthenticationException("No user name"));

        } else {
            final HttpServletRequest request = httpServletRequestHolder.get();

            try {
                // Create the authentication token from the user name and password
                final UsernamePasswordToken token = request == null ?
                        new UsernamePasswordToken(userName, password,true) :
                        new UsernamePasswordToken(userName, password,true, request.getRemoteHost());

                // Attempt authentication
                final Subject currentUser = SecurityUtils.getSubject();
                currentUser.login(token);

                user = (User) currentUser.getPrincipal();
            } catch (final RuntimeException e) {
                loginFailure(userName, e);
            }

            // Ensure regular users are allowed to login at this time.
            checkLoginAllowed(user);

            try {
                // Pass back the user info
                user = handleLogin(request, user, userName);

                // Audit the successful logon
                eventLog.logon(user.getName());

            } catch (final RuntimeException e) {
                loginFailure(userName, e);
            }
        }

        // Pass back the user info
        return user;
    }

    private void loginFailure(final String userName, final RuntimeException e) {
        // Audit logon failure.
        eventLog.logon(userName, false, e.getMessage(), AuthenticateOutcomeReason.INCORRECT_USERNAME_OR_PASSWORD);

        if (userName != null && userName.length() > 0) {
            final UserRef userRef = userService.getUserRefByName(userName);
            if (userRef != null) {
                // Increment the number of login failures.
                final User user = userService.loadByUuid(userRef.getUuid());
                if (user != null) {
                    user.setCurrentLoginFailures(user.getCurrentLoginFailures() + 1);
                    user.setTotalLoginFailures(user.getTotalLoginFailures() + 1);

                    if (user.getCurrentLoginFailures() > 3) {
                        LOGGER.error("login() - Locking account {}", user.getName());
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
        final User user = getCurrentUser();

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
    public User changePassword(final User user, final String oldPassword, final String newPassword) {
        if (user == null) {
            return null;
        }

        // Make sure only a user with manage users permission can change a password or that the user is changing their own.
        if (!securityContext.hasAppPermission(User.MANAGE_USERS_PERMISSION) && !user.equals(getCurrentUser())) {
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
        user.setPasswordExpiryMs(getExpiryDate().toInstant().toEpochMilli());

        // Write event log data for password change.
        eventLog.changePassword(user.getName());

        // Save the system user.
        return userService.save(user);
    }

    @Override
    public User resetPassword(final User user, final String password) {
        // Add event log data for reset password.
        eventLog.resetPassword(user.getName(), false);
        return doResetPassword(user, password);
    }

    private User doResetPassword(final User user, final String newPassword) {
        if (user == null) {
            return null;
        }

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
    public User getCurrentUser() {
        if (sessionExists()) {
            final HttpServletRequest request = httpServletRequestHolder.get();

            return (User) request.getSession().getAttribute(USER_SESSION_KEY);
        }

        return null;
    }

    @Override
    public String getCurrentUserId() throws RuntimeException {
        if (sessionExists()) {
            final HttpServletRequest request = httpServletRequestHolder.get();
            return (String) request.getSession().getAttribute(USER_ID_SESSION_KEY);
        }

        return null;
    }

    @Override
    @Insecure
    public void refreshCurrentUser() throws RuntimeException {
        if (sessionExists()) {
            final HttpServletRequest request = httpServletRequestHolder.get();

            User user = getCurrentUser();
            if (user != null) {
                user = userService.loadByUuid(user.getUuid());
            }

            request.getSession().setAttribute(USER_SESSION_KEY, user);
        }
    }

    private boolean sessionExists() {
        return httpServletRequestHolder.getSessionId() != null;
    }

    private ZonedDateTime getExpiryDate() {
        // Get the current number of milliseconds.
        final ZonedDateTime expiryDate = ZonedDateTime.now(ZoneOffset.UTC);
        // Days to expiry will be 90 days.
        return expiryDate.plusDays(getDaysToPasswordExpiry());
    }

    @Override
    public boolean canEmailPasswordReset() {
        return mailSenderProvider.get().canEmailPasswordReset();
    }

    @Override
    public User emailPasswordReset(User user) {
        // Make sure only a user with manage users permission can change a password or that the user is changing their own.
        if (!securityContext.hasAppPermission(User.MANAGE_USERS_PERMISSION) && !user.equals(getCurrentUser())) {
            return null;
        }

        final String password = PasswordGenerator.generatePassword();
        user = doResetPassword(user, password);
        mailSenderProvider.get().emailPasswordReset(user, password);
        return user;
    }

    private int getDaysToPasswordExpiry() {
        return stroomPropertyService.getIntProperty("stroom.daysToPasswordExpiry", DEFAULT_DAYS_TO_PASSWORD_EXPIRY);
    }

    @Override
    public Boolean emailPasswordReset(final String userName) throws RuntimeException {
        final UserRef userRef = userService.getUserRefByName(userName);
        if (userRef != null) {
            final User user = userService.loadByUuid(userRef.getUuid());
            if (user != null) {
                emailPasswordReset(user);
            }
        }
        return Boolean.TRUE;
    }

    /**
     * TODO JC 2017-06-06: This is invoked by GWT using AutoLoginAction. But because we're logging in using
     * a token the characterisation of this flow as 'logging in' is incorrect. I.e. change the naming.
     */
    @Override
    @Insecure
    public User autoLogin() throws RuntimeException {
        User user = userService.loadByUuid(securityContext.getUserUuid());
        return user;
    }

    private User loginWithCertificate() {
        User user;

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
            final Subject currentUser = SecurityUtils.getSubject();
            currentUser.login(token);

            user = (User) currentUser.getPrincipal();
        } catch (final RuntimeException ex) {
            final String message = EntityServiceExceptionUtil.unwrapMessage(ex, ex);

            // Audit the failed login
            eventLog.logon(certificateDn, false, message, AuthenticateOutcomeReason.OTHER);

            throw EntityServiceExceptionUtil.create(ex);
        }

        // Ensure regular users are allowed to login at this time.
        checkLoginAllowed(user);

        try {
            // Pass back the user info
            return handleLogin(request, user, certificateDn);

        } catch (final RuntimeException ex) {
            final String message = EntityServiceExceptionUtil.unwrapMessage(ex, ex);

            // Audit the failed login
            eventLog.logon(certificateDn, false, message, AuthenticateOutcomeReason.OTHER);

            throw EntityServiceExceptionUtil.create(ex);
        }
    }

    private User handleLogin(final HttpServletRequest request, final User user, final String userId) {
        if (user != null) {
            User reloadUser = userService.loadByUuid(user.getUuid());
            reloadUser.updateValidLogin();
            reloadUser = userService.save(reloadUser);

            // Audit the successful login
            eventLog.logon(userId);

            if(request != null) {
                final HttpSession session = request.getSession(true);
                session.setAttribute(USER_SESSION_KEY, reloadUser);
                session.setAttribute(USER_ID_SESSION_KEY, userId);
            }

            return reloadUser;
        }
        return user;
    }
}
