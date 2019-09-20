package stroom.dispatch.client;

import org.fusesource.restygwt.client.MethodCallback;

import java.util.function.Consumer;

public interface Callback<T> extends MethodCallback<T> {
    Callback<T> onSuccess(Consumer<T> consumer);

    Callback<T> onFailure(Consumer<Throwable> consumer);
}
