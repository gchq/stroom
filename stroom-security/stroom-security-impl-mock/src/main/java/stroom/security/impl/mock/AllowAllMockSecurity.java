package stroom.security.impl.mock;

import stroom.security.Security;

import java.util.function.Supplier;

public class MockSecurity implements Security {
    @Override
    public <T> T asUserResult(final String userToken, final Supplier<T> supplier) {
        return null;
    }

    @Override
    public void asUser(final String userToken, final Runnable runnable) {

    }

    @Override
    public <T> T asProcessingUserResult(final Supplier<T> supplier) {
        return null;
    }

    @Override
    public void asProcessingUser(final Runnable runnable) {

    }

    @Override
    public <T> T useAsReadResult(final Supplier<T> supplier) {
        return null;
    }

    @Override
    public void useAsRead(final Runnable runnable) {

    }

    @Override
    public void secure(final String permission, final Runnable runnable) {

    }

    @Override
    public <T> T secureResult(final String permission, final Supplier<T> supplier) {
        return null;
    }

    @Override
    public void secure(final Runnable runnable) {

    }

    @Override
    public <T> T secureResult(final Supplier<T> supplier) {
        return null;
    }

    @Override
    public void insecure(final Runnable runnable) {

    }

    @Override
    public <T> T insecureResult(final Supplier<T> supplier) {
        return null;
    }
}
