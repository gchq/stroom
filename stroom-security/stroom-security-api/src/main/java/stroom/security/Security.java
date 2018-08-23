package stroom.security;

import java.util.function.Supplier;

public interface Security {
    <T> T asUserResult(String userToken, Supplier<T> supplier);

    void asUser(String userToken, Runnable runnable);

    <T> T asProcessingUserResult(Supplier<T> supplier);

    void asProcessingUser(Runnable runnable);

    <T> T useAsReadResult(Supplier<T> supplier);

    void useAsRead(Runnable runnable);

    void secure(String permission, Runnable runnable);

    <T> T secureResult(String permission, Supplier<T> supplier);

    void secure(Runnable runnable);

    <T> T secureResult(Supplier<T> supplier);

    void insecure(Runnable runnable);

    <T> T insecureResult(Supplier<T> supplier);
}
