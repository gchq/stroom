package stroom.dispatch.client;

import stroom.alert.client.event.AlertEvent;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.util.client.JSONUtil;

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
import javax.ws.rs.core.MediaType;

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
//                        String msg = exception.getMessage();
                        Throwable throwable = null;

                        if (method.getResponse() != null &&
                                method.getResponse().getText() != null &&
                                !method.getResponse().getText().trim().isEmpty()) {
                            final String responseText = method.getResponse().getText().trim();
                            final String contentType = method.getResponse().getHeader("Content-Type");

                            if (MediaType.TEXT_PLAIN.equals(contentType)) {
                                throwable = getThrowableFromStringResponse(method, exception, responseText);
                            } else {
                                try {
                                    throwable = getThrowableFromJsonResponse(method, exception, responseText);
                                } catch (Exception e) {
                                    GWT.log("Error parsing response as json: " + e.getMessage());
                                    try {
                                        // Try parsing it as text
                                        throwable = getThrowableFromStringResponse(method, exception, responseText);
                                    } catch (Exception e2) {
                                        GWT.log("Error parsing response as text: " + e.getMessage());
                                    }
                                }
                            }
                        }

                        // Fallback
                        if (throwable == null) {
                            throwable = exception;
                        }

                        if (errorConsumer != null) {
                            errorConsumer.accept(throwable);
                        } else {
                            GWT.log(throwable.getMessage(), throwable);

                            if (throwable instanceof ResponseException) {
                                final ResponseException responseException = (ResponseException) throwable;
                                String details = responseException.getDetails();
                                if (details != null && details.trim().length() > 0) {
                                    AlertEvent.fireError(hasHandlers,
                                            throwable.getMessage(),
                                            details.trim(),
                                            null);
                                } else {
                                    AlertEvent.fireError(hasHandlers, throwable.getMessage(), null);
                                }
                            } else {
                                AlertEvent.fireError(hasHandlers, throwable.getMessage(), null);
                            }
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

        private Throwable getThrowableFromStringResponse(final Method method,
                                                         final Throwable throwable,
                                                         final String response) {
            return new ResponseException(
                    method.builder.getHTTPMethod(),
                    method.builder.getUrl(),
                    null,
                    method.getResponse().getStatusCode(),
                    response,
                    null,
                    null,
                    throwable);
        }

        private Throwable getThrowableFromJsonResponse(final Method method,
                                                       final Throwable throwable,
                                                       final String json) {

            final Throwable newThrowable;
            // Assuming we get a response like { "code": "", "message": "" } or
            // { "code": "", "details": "" }
            final JSONObject responseJson = (JSONObject) JSONParser.parseStrict(json);
            final Integer code = JSONUtil.getInteger(responseJson.get("code"));
            final String message = JSONUtil.getString(responseJson.get("message"));
            final String details = JSONUtil.getString(responseJson.get("details"));
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

            if (message != null && message.length() > 0) {
                newThrowable = new ResponseException(
                        method.builder.getHTTPMethod(),
                        method.builder.getUrl(),
                        json,
                        code,
                        message,
                        details,
                        responseKeyValues,
                        throwable);

            } else {
                final String msg = "Error calling " +
                        method.builder.getHTTPMethod() +
                        " " +
                        method.builder.getUrl() +
                        " - " +
                        responseKeyValues;
                newThrowable = new RuntimeException(msg, throwable);
            }
            return newThrowable;
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

    private static class ResponseException extends RuntimeException {

        private final String method;
        private final String url;
        private final String json;
        private final Integer code;
        private final String details;
        private final String responseKeyValues;

        public ResponseException(final String method,
                                 final String url,
                                 final String json,
                                 final Integer code,
                                 final String message,
                                 final String details,
                                 final String responseKeyValues,
                                 final Throwable cause) {
            super(message, cause);
            this.method = method;
            this.url = url;
            this.json = json;
            this.code = code;
            this.details = details;
            this.responseKeyValues = responseKeyValues;
        }

        public String getDetails() {
            return details;
        }

        @Override
        public String toString() {
            return "ResponseException{" +
                    "method='" + method + '\'' +
                    ", url='" + url + '\'' +
                    ", json='" + json + '\'' +
                    ", code='" + code + '\'' +
                    ", message='" + getMessage() + '\'' +
                    ", details='" + details + '\'' +
                    ", responseKeyValues='" + responseKeyValues + '\'' +
                    '}';
        }
    }
}
