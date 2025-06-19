package stroom.dispatch.client;

import stroom.util.client.JSONUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import org.fusesource.restygwt.client.Method;

import java.util.Objects;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;

public class RestError {

    private final Method method;
    private final Throwable throwable;

    private Throwable unwrapped;

    public RestError(final Method method, final Throwable throwable) {
        this.method = method;
        this.throwable = throwable;
    }

    public Method getMethod() {
        return method;
    }

    public String getMessage() {
        return getUnwrapped().getMessage();
    }

    public Throwable getException() {
        return getUnwrapped();
    }

    public Throwable getRawThrowable() {
        return throwable;
    }

    @Override
    public String toString() {
        return getMessage();
    }

    private Throwable getUnwrapped() {
        if (unwrapped == null) {
            unwrapped = unwrap();
        }
        return unwrapped;
    }

    private Throwable unwrap() {
        // The exception is restyGWT's FailedResponseException
        // so extract the response payload and treat it as WebApplicationException
        Throwable unwrapped = null;

        if (method.getResponse() != null &&
                method.getResponse().getText() != null &&
                !method.getResponse().getText().trim().isEmpty()) {
            final String responseText = method.getResponse().getText().trim();
            final String contentType = method.getResponse().getHeader("Content-Type");

            if (MediaType.TEXT_PLAIN.equals(contentType)) {
                unwrapped = getThrowableFromStringResponse(method, throwable, responseText);
            } else {
                try {
                    unwrapped = getThrowableFromJsonResponse(method, throwable, responseText);
                } catch (final Exception e) {
                    GWT.log("Error parsing response as json: " + e.getMessage());
                    try {
                        // Try parsing it as text
                        unwrapped = getThrowableFromStringResponse(method, throwable, responseText);
                    } catch (final Exception e2) {
                        GWT.log("Error parsing response as text: " + e.getMessage());
                    }
                }
            }
        }

        // Fallback
        if (unwrapped == null) {
            unwrapped = throwable;
        }

        return unwrapped;
    }

    private static Throwable getThrowableFromStringResponse(final Method method,
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

    private static Throwable getThrowableFromJsonResponse(final Method method,
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

    private static String getJsonKey(final JSONObject jsonObject, final String key) {

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
