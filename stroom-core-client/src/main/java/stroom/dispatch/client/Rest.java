package stroom.dispatch.client;

import org.fusesource.restygwt.client.DirectRestService;

import java.util.function.Consumer;

public interface Rest<R> {
    Rest<R> onSuccess(Consumer<R> consumer);

    Rest<R> onFailure(Consumer<Throwable> consumer);

    <T extends DirectRestService> T call(T service);
}
