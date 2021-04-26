package stroom.dispatch.client;

import stroom.alert.client.event.AlertEvent;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import org.fusesource.restygwt.client.Defaults;
import org.fusesource.restygwt.client.DirectRestService;
import org.fusesource.restygwt.client.Dispatcher;
import org.fusesource.restygwt.client.Method;
import org.fusesource.restygwt.client.MethodCallback;
import org.fusesource.restygwt.client.REST;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    public <R> Rest<R> create() {
        return new RestImpl<>(this);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    @Override
    public String getImportFileURL() {
        return GWT.getHostPageBaseURL() + "importfile.rpc";
    }

    private static class RestImpl<R> implements Rest<R> {

        private final HasHandlers hasHandlers;
        private final REST<R> rest;
        private Consumer<R> resultConsumer;
        private Consumer<Throwable> errorConsumer;

        RestImpl(final HasHandlers hasHandlers) {
            this.hasHandlers = hasHandlers;
            final MethodCallback<R> methodCallback = new MethodCallback<R>() {
                @Override
                public void onFailure(final Method method, final Throwable exception) {
                    try {
                        // The exception is restyGWT's FailedResponseException
                        // so extract the response payload and treat it as WebApplicationException
                        // json
                        String message = exception.getMessage();
                        Throwable throwable = exception;

                        if (method.getResponse() != null &&
                                method.getResponse().getText() != null &&
                                !method.getResponse().getText().trim().isEmpty()) {
                            message = method.getResponse().getText().trim();

                            try {
                                // Assuming we get a response like { "code": "", "message": "" } or
                                // { "code": "", "details": "" }
                                final JSONObject responseJson = (JSONObject) JSONParser.parseStrict(message);
                                final String responseKeyValues = responseJson.keySet()
                                        .stream()
                                        .map(key -> {
                                            final String val = getJsonKey(responseJson, key);
                                            return val != null
                                                    ? key + ": " + val
                                                    : null;
                                        })
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.joining(", "));

                                message = "Error calling " +
                                        method.builder.getHTTPMethod() +
                                        " " +
                                        method.builder.getUrl() +
                                        " - " +
                                        responseKeyValues;
                            } catch (Exception e) {
                                // Unable to parse message as JSON.
                            }

                            throwable = new RuntimeException(message, exception);
                        }

                        if (errorConsumer != null) {
                            errorConsumer.accept(throwable);
                        } else {
                            GWT.log(message, throwable);
                            AlertEvent.fireError(hasHandlers, message, null);
                        }
                    } catch (final Throwable t) {
                        GWT.log(method.getRequest().toString());
                        GWT.log(t.getMessage(), t);
                        AlertEvent.fireErrorFromException(hasHandlers, t, null);
                    } finally {
                        decrementTaskCount();
                    }
                }

                @Override
                public void onSuccess(final Method method, final R response) {
                    try {
                        if (resultConsumer != null) {
                            resultConsumer.accept(response);
                        }
                    } catch (final Throwable t) {
                        GWT.log(method.getRequest().toString());
                        GWT.log(t.getMessage(), t);
                        AlertEvent.fireErrorFromException(hasHandlers, t, null);
                    } finally {
                        decrementTaskCount();
                    }
                }
            };
            rest = REST.withCallback(methodCallback);
        }

        @Override
        public Rest<R> onSuccess(Consumer<R> consumer) {
            resultConsumer = consumer;
            return this;
        }

        @Override
        public Rest<R> onFailure(Consumer<Throwable> consumer) {
            errorConsumer = consumer;
            return this;
        }

        @Override
        public <T extends DirectRestService> T call(T service) {
            incrementTaskCount();
            return rest.call(service);
        }

        private void incrementTaskCount() {
            // Add the task to the map.
            TaskStartEvent.fire(hasHandlers);
        }

        private void decrementTaskCount() {
            // Remove the task from the task count.
            TaskEndEvent.fire(hasHandlers);
        }

        private String getJsonKey(final JSONObject jsonObject, final String key) {

            final String value;
            if (jsonObject.containsKey(key)) {
                final JSONValue jsonValue = jsonObject.get(key);
                if (jsonValue.isString() != null) {
                    value = jsonValue.isString().stringValue();
                } else if (jsonValue.isNumber() != null) {
                    value = Double.toString(jsonValue.isNumber().doubleValue());
                } else if (jsonValue.isBoolean() != null) {
                    value = Boolean.toString(jsonValue.isBoolean().booleanValue());
                } else {
                    // Just give back the json
                    value = jsonValue.toString();
                }
            } else {
                value = null;
            }
            return value;
        }
    }
}
