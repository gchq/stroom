package stroom.dispatch.client;

import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
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
        return new RestBuilderImpl(this, false);
    }

    @Override
    public RestBuilder builder(final boolean isQuiet) {
        return new RestBuilderImpl(this, isQuiet);
    }

    @Override
    public <R> Rest<R> create() {
        return new RestImpl<>(this);
    }

    @Override
    public <R> Rest<R> createQuiet() {
        return new QuietRestImpl<>(this);
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
        private static final TypeLiteral<Long> LONG_TYPE_LITERAL = new TypeLiteral<Long>() {
        };

        private final HasHandlers hasHandlers;
        private final boolean isQuiet;

        private RestBuilderImpl(final HasHandlers hasHandlers,
                                final boolean isQuiet) {
            this.hasHandlers = hasHandlers;
            this.isQuiet = isQuiet;
        }

        private <R> AbstractRest<R> createRest(final TypeLiteral<R> typeLiteral) {
            return isQuiet
                    ? new QuietRestImpl<>(hasHandlers, typeLiteral)
                    : new RestImpl<>(hasHandlers, typeLiteral);
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


    // --------------------------------------------------------------------------------


    private static class RestImpl<R> extends AbstractRest<R> {

        private final HasHandlers hasHandlers;

        RestImpl(final HasHandlers hasHandlers) {
            super(hasHandlers);
            this.hasHandlers = hasHandlers;
        }

        // typeLiteral used to fix the type
        @SuppressWarnings("unused")
        RestImpl(final HasHandlers hasHandlers, TypeLiteral<R> typeLiteral) {
            super(hasHandlers);
            this.hasHandlers = hasHandlers;
        }

        @Override
        protected void incrementTaskCount() {
            // Add the task to the map.
            TaskStartEvent.fire(hasHandlers);
        }

        @Override
        protected void decrementTaskCount() {
            // Remove the task from the task count.
            TaskEndEvent.fire(hasHandlers);
        }
    }


    // --------------------------------------------------------------------------------


    private static class QuietRestImpl<R> extends AbstractRest<R> {

        QuietRestImpl(final HasHandlers hasHandlers) {
            super(hasHandlers);
        }

        // typeLiteral used to fix the type
        @SuppressWarnings("unused")
        QuietRestImpl(final HasHandlers hasHandlers, final TypeLiteral<R> typeLiteral) {
            super(hasHandlers);
        }

        @Override
        protected void incrementTaskCount() {
            // Do nothing.
        }

        @Override
        protected void decrementTaskCount() {
            // Do nothing.
        }
    }
}
