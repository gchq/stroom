package stroom.security;

import stroom.security.shared.PermissionException;
import stroom.security.util.UserTokenUtil;

import java.util.function.Supplier;

public class Security {
    private final ThreadLocal<Boolean> checkTypeThreadLocal = ThreadLocal.withInitial(() -> Boolean.TRUE);

    private final SecurityContext securityContext;

    public Security(final SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    /**
     * Run the supplied code as the specified user.
     */
    public <T> T asUserResult(final String userToken, final Supplier<T> supplier) {
        T result;
        try {
            securityContext.pushUser(userToken);
            result = supplier.get();
        } finally {
            securityContext.popUser();
        }
        return result;
    }

    /**
     * Run the supplied code as the specified user.
     */
    public void asUser(final String userToken, final Runnable runnable) {
        try {
            securityContext.pushUser(userToken);
            runnable.run();
        } finally {
            securityContext.popUser();
        }
    }

    /**
     * Run the supplied code as the internal processing user.
     */
    public <T> T asProcessingUserResult(final Supplier<T> supplier) {
        T result;
        try {
            securityContext.pushUser(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN);
            result = supplier.get();
        } finally {
            securityContext.popUser();
        }
        return result;
    }

    /**
     * Run the supplied code as the internal processing user.
     */
    public void asProcessingUser(final Runnable runnable) {
        try {
            securityContext.pushUser(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN);
            runnable.run();
        } finally {
            securityContext.popUser();
        }
    }

    /**
     * Allow the current user to read items that they only have 'Use' permission on.
     */
    public <T> T useAsReadResult(final Supplier<T> supplier) {
        T result;
        try {
            securityContext.elevatePermissions();
            result = supplier.get();
        } finally {
            securityContext.restorePermissions();
        }
        return result;
    }

    /**
     * Allow the current user to read items that they only have 'Use' permission on.
     */
    public void useAsRead(final Runnable runnable) {
        try {
            securityContext.elevatePermissions();
            runnable.run();
        } finally {
            securityContext.restorePermissions();
        }
    }

    /**
     * Secure the supplied code with the supplied application permission.
     */
    public void secure(final String permission, final Runnable runnable) {
        // Initiate current check type.
        final Boolean currentCheckType = checkTypeThreadLocal.get();

        // If we aren't currently checking anything then just proceed.
        if (Boolean.FALSE.equals(currentCheckType)) {
            runnable.run();
        } else {
            // If the current user is an administrator then don't do any security checking.
            if (permission == null || isAdmin()) {
                try {
                    // Don't check any further permissions.
                    checkTypeThreadLocal.set(Boolean.FALSE);
                    runnable.run();
                } finally {
                    // Reset the current check type.
                    checkTypeThreadLocal.set(currentCheckType);
                }
            } else {
                // We must be logged in to access a secured service.
                checkLogin();
                checkAppPermission(permission);

                runnable.run();
            }
        }
    }

    /**
     * Secure the supplied code with the supplied application permission.
     */
    public <T> T secureResult(final String permission, final Supplier<T> supplier) {
        T result;

        // Initiate current check type.
        final Boolean currentCheckType = checkTypeThreadLocal.get();

        // If we aren't currently checking anything then just proceed.
        if (Boolean.FALSE.equals(currentCheckType)) {
            result = supplier.get();
        } else {
            // If the current user is an administrator then don't do any security checking.
            if (permission == null || isAdmin()) {
                try {
                    // Don't check any further permissions.
                    checkTypeThreadLocal.set(Boolean.FALSE);
                    result = supplier.get();
                } finally {
                    // Reset the current check type.
                    checkTypeThreadLocal.set(currentCheckType);
                }
            } else {
                // We must be logged in to access a secured service.
                checkLogin();
                checkAppPermission(permission);

                result = supplier.get();
            }
        }

        return result;
    }

    /**
     * Secure the supplied code to ensure that there is a current authenticated user.
     */
    public void secure(final Runnable runnable) {
        // Initiate current check type.
        final Boolean currentCheckType = checkTypeThreadLocal.get();

        // If we aren't currently checking anything then just proceed.
        if (Boolean.FALSE.equals(currentCheckType)) {
            runnable.run();
        } else {
            // If the current user is an administrator then don't do any security checking.
            if (isAdmin()) {
                try {
                    // Don't check any further permissions.
                    checkTypeThreadLocal.set(Boolean.FALSE);
                    runnable.run();
                } finally {
                    // Reset the current check type.
                    checkTypeThreadLocal.set(currentCheckType);
                }
            } else {
                // We must be logged in to access a secured service.
                checkLogin();

                runnable.run();
            }
        }
    }

    /**
     * Secure the supplied code to ensure that there is a current authenticated user.
     */
    public <T> T secureResult(final Supplier<T> supplier) {
        T result;

        // Initiate current check type.
        final Boolean currentCheckType = checkTypeThreadLocal.get();

        // If we aren't currently checking anything then just proceed.
        if (Boolean.FALSE.equals(currentCheckType)) {
            result = supplier.get();
        } else {
            // If the current user is an administrator then don't do any security checking.
            if (isAdmin()) {
                try {
                    // Don't check any further permissions.
                    checkTypeThreadLocal.set(Boolean.FALSE);
                    result = supplier.get();
                } finally {
                    // Reset the current check type.
                    checkTypeThreadLocal.set(currentCheckType);
                }
            } else {
                // We must be logged in to access a secured service.
                checkLogin();

                result = supplier.get();
            }
        }

        return result;
    }

    /**
     * Run the supplied code regardless of whether there is a current user and also allow all inner code to run
     * insecurely even if it is often secured when executed from other entry points.
     */
    public void insecure(final Runnable runnable) {
        secure(null, runnable);
    }

    /**
     * Run the supplied code regardless of whether there is a current user and also allow all inner code to run
     * insecurely even if it is often secured when executed from other entry points.
     */
    public <T> T insecureResult(final Supplier<T> supplier) {
        return secureResult(null, supplier);
    }

    private void checkAppPermission(final String permission) {
        if (!hasAppPermission(permission)) {
            throw new PermissionException("User does not have the required permission (" + permission + ")", securityContext.getUserId());
        }
    }

    private void checkLogin() {
        final Boolean currentCheckType = checkTypeThreadLocal.get();
        try {
            // Don't check any further permissions.
            checkTypeThreadLocal.set(Boolean.FALSE);
            if (!securityContext.isLoggedIn()) {
                throw new PermissionException("A user must be logged in to call service");
            }
        } finally {
            checkTypeThreadLocal.set(currentCheckType);
        }
    }

    private boolean isAdmin() {
        final Boolean currentCheckType = checkTypeThreadLocal.get();
        try {
            // Don't check any further permissions.
            checkTypeThreadLocal.set(Boolean.FALSE);
            return securityContext.isAdmin();
        } finally {
            checkTypeThreadLocal.set(currentCheckType);
        }
    }

    private boolean hasAppPermission(final String permission) {
        final Boolean currentCheckType = checkTypeThreadLocal.get();
        try {
            // Don't check any further permissions.
            checkTypeThreadLocal.set(Boolean.FALSE);
            return securityContext.hasAppPermission(permission);
        } finally {
            checkTypeThreadLocal.set(currentCheckType);
        }
    }
}
