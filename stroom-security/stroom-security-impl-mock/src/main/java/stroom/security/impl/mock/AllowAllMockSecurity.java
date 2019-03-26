package stroom.security.impl.mock;

import stroom.security.api.Security;
import stroom.security.shared.UserToken;

import java.util.function.Supplier;

/**
 * All methods proceed with no intervention from a security perspective
 */
public class AllowAllMockSecurity implements Security {

    @Override
    public <T> T asUserResult(final UserToken userToken, final Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    public void asUser(final UserToken userToken, final Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> T asProcessingUserResult(final Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    public void asProcessingUser(final Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> T useAsReadResult(final Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    public void useAsRead(final Runnable runnable) {
        runnable.run();
    }

    @Override
    public void secure(final String permission, final Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> T secureResult(final String permission, final Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    public void secure(final Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> T secureResult(final Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    public void insecure(final Runnable runnable) {
        runnable.run();

    }

    @Override
    public <T> T insecureResult(final Supplier<T> supplier) {
        return supplier.get();
    }
}
