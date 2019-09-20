package stroom.dispatch.client;

import org.fusesource.restygwt.client.RestService;

public interface RestApi {
    <T extends RestService> T client(final Class<T> classLiteral);

    <T> Callback<T> callback();
}
