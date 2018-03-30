package stroom.security;

import java.util.function.Supplier;

public class Security {
    private final SecurityContext securityContext;

    Security(final SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

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

    public void asUser(final String userToken, final Runnable runnable) {
        try {
            securityContext.pushUser(userToken);
            runnable.run();
        } finally {
            securityContext.popUser();
        }
    }

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

    public void asProcessingUser(final Runnable runnable) {
        try {
            securityContext.pushUser(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN);
            runnable.run();
        } finally {
            securityContext.popUser();
        }
    }

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

    public void useAsRead(final Runnable runnable) {
        try {
            securityContext.elevatePermissions();
            runnable.run();
        } finally {
            securityContext.restorePermissions();
        }
    }


//    public static SecurityHelper elevate(SecurityContext securityContext) {
//        return new SecurityHelper(securityContext, Action.ELEVATE, null);
//    }
}
