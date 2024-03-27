package stroom.dispatch.client;

import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.web.bindery.event.shared.EventBus;
import org.fusesource.restygwt.client.Defaults;
import org.fusesource.restygwt.client.Dispatcher;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public RestBuilder builder() {
        return new RestBuilderImpl(this);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    @Override
    public String getImportFileURL() {
        return GWT.getHostPageBaseURL() + "importfile.rpc";
    }


    // --------------------------------------------------------------------------------


    private static class RestBuilderImpl implements RestBuilder {

        @SuppressWarnings("Convert2Diamond")
        private static final TypeLiteral<Void> VOID_TYPE_LITERAL = new TypeLiteral<Void>() {
        };
        @SuppressWarnings("Convert2Diamond")
        private static final TypeLiteral<Boolean> BOOLEAN_TYPE_LITERAL = new TypeLiteral<Boolean>() {
        };
        @SuppressWarnings("Convert2Diamond")
        private static final TypeLiteral<String> STRING_TYPE_LITERAL = new TypeLiteral<String>() {
        };
        @SuppressWarnings("Convert2Diamond")
        private static final TypeLiteral<Integer> INTEGER_TYPE_LITERAL = new TypeLiteral<Integer>() {
        };
        @SuppressWarnings("Convert2Diamond")
        private static final TypeLiteral<Long> LONG_TYPE_LITERAL = new TypeLiteral<Long>() {
        };

        private final HasHandlers hasHandlers;

        private RestBuilderImpl(final HasHandlers hasHandlers) {
            this.hasHandlers = hasHandlers;
        }

        private <R> RestImpl<R> createRest(final TypeLiteral<R> typeLiteral) {
            return new RestImpl<>(hasHandlers, typeLiteral);
        }

        @Override
        public Rest<Void> forVoid() {
            return createRest(VOID_TYPE_LITERAL);
        }

        @Override
        public Rest<Boolean> forBoolean() {
            return createRest(BOOLEAN_TYPE_LITERAL);
        }

        @Override
        public Rest<String> forString() {
            return createRest(STRING_TYPE_LITERAL);
        }

        @Override
        public Rest<Long> forLong() {
            return createRest(LONG_TYPE_LITERAL);
        }

        @Override
        public Rest<Integer> forInteger() {
            return createRest(INTEGER_TYPE_LITERAL);
        }

        @Override
        public <R> Rest<R> forType(final Class<R> type) {
            return createRest(new TypeLiteral<R>() {
            });
        }

        @Override
        public <R> Rest<R> forWrappedType(final TypeLiteral<R> typeLiteral) {
            return createRest(typeLiteral);
        }

        @Override
        public <T> Rest<List<T>> forListOf(final Class<T> itemType) {
            //noinspection Convert2Diamond
            return createRest(new TypeLiteral<List<T>>() {
            });
        }

        @Override
        public Rest<List<String>> forStringList() {
            //noinspection Convert2Diamond
            return createRest(new TypeLiteral<List<String>>() {
            });
        }

        @Override
        public <K, V> Rest<Map<K, V>> forMapOf(final Class<K> keyType, final Class<V> valueType) {
            //noinspection Convert2Diamond
            return createRest(new TypeLiteral<Map<K, V>>() {
            });
        }

        @Override
        public <T> Rest<Set<T>> forSetOf(final Class<T> itemType) {
            //noinspection Convert2Diamond
            return createRest(new TypeLiteral<Set<T>>() {
            });
        }

        @Override
        public <T> Rest<ResultPage<T>> forResultPageOf(final Class<T> itemType) {
            //noinspection Convert2Diamond
            return createRest(new TypeLiteral<ResultPage<T>>() {
            });
        }
    }
}