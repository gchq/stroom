package stroom.dispatch.client;

import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.web.bindery.event.shared.EventBus;
import org.fusesource.restygwt.client.Defaults;
import org.fusesource.restygwt.client.DirectRestService;
import org.fusesource.restygwt.client.Dispatcher;

import java.util.function.Consumer;
import java.util.function.Function;

class RestFactoryImpl implements RestFactory, HasHandlers {

    private final EventBus eventBus;

    @Inject
    public RestFactoryImpl(final EventBus eventBus, final Dispatcher dispatcher) {
        this.eventBus = eventBus;

        String hostPageBaseUrl = GWT.getHostPageBaseURL();
        hostPageBaseUrl = hostPageBaseUrl.substring(0, hostPageBaseUrl.lastIndexOf("/"));
        hostPageBaseUrl = hostPageBaseUrl.substring(0, hostPageBaseUrl.lastIndexOf("/"));
        final String apiUrl = hostPageBaseUrl + "/api/";
        Defaults.setServiceRoot(apiUrl);
        Defaults.setDispatcher(dispatcher);
    }


    @Override
    public <T extends DirectRestService> Resource<T> resource(final T service) {
        return new ResourceImpl<>(this, service);
    }


    private static class ResourceImpl<T extends DirectRestService> implements Resource<T> {

        private final HasHandlers hasHandlers;
        private final T service;

        public ResourceImpl(final HasHandlers hasHandlers,
                            final T service) {
            this.hasHandlers = hasHandlers;
            this.service = service;
        }

        @Override
        public <R> RestExecutor<T, R> method(final Function<T, R> function) {
            final Rest<R> rest = new RestImpl<R>(hasHandlers, null);
            return new RestExecutorImpl<>(rest, service, function);
        }

        @Override
        public <R> RestExecutor<T, R> call(final Consumer<T> consumer) {
            final Rest<R> rest = new RestImpl<R>(hasHandlers, null);
            final Function<T, R> function = t -> {
                consumer.accept(t);
                return null;
            };
            return new RestExecutorImpl<>(rest, service, function);
        }
    }

    private static class RestExecutorImpl<T extends DirectRestService, R> implements RestExecutor<T, R> {

        private final Rest<R> rest;
        private final T service;
        private final Function<T, R> function;

        public RestExecutorImpl(final Rest<R> rest, final T service, final Function<T, R> function) {
            this.rest = rest;
            this.service = service;
            this.function = function;
        }

        @Override
        public RestExecutor<T, R> quiet(final boolean quiet) {
            rest.quiet(quiet);
            return this;
        }

        @Override
        public RestExecutor<T, R> onSuccess(final Consumer<R> consumer) {
            rest.onSuccess(consumer);
            return this;
        }

        @Override
        public RestExecutor<T, R> onFailure(final Consumer<Throwable> consumer) {
            rest.onFailure(consumer);
            return this;
        }

        @Override
        public void exec() {
            function.apply(rest.call(service));
        }
    }

    @Override
    public <R> Rest<R> forType(final Class<R> type) {
        return createRest(new TypeLiteral<R>() {
        });
    }

    @Override
    public <T> Rest<ResultPage<T>> forResultPageOf(final Class<T> itemType) {
        //noinspection Convert2Diamond
        return createRest(new TypeLiteral<ResultPage<T>>() {
        });
    }

    private <R> RestImpl<R> createRest(final TypeLiteral<R> typeLiteral) {
        return new RestImpl<>(this, typeLiteral);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    @Override
    public String getImportFileURL() {
        return GWT.getHostPageBaseURL() + "importfile.rpc";
    }
}