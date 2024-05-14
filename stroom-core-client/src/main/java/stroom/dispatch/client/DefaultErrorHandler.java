package stroom.dispatch.client;

import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.GwtStringBuilderOutputStream;
import stroom.util.shared.EntityServiceException;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HasHandlers;
import org.fusesource.restygwt.client.Method;

import java.io.PrintStream;

public class DefaultErrorHandler implements RestErrorHandler {

    private final HasHandlers hasHandlers;
    private final AlertCallback callback;

    public DefaultErrorHandler(final HasHandlers hasHandlers,
                               final AlertCallback callback) {
        this.hasHandlers = hasHandlers;
        this.callback = callback;
    }

    @Override
    public void onError(final RestError error) {
        final Method method = error.getMethod();
        final Throwable throwable = error.getException();
        if (method != null && method.getRequest() != null) {
            GWT.log(method.getRequest().toString());
        }
        GWT.log(throwable.getMessage(), throwable);

        String message = throwable.getMessage();
        String details = null;

        if (throwable instanceof ResponseException) {
            final ResponseException responseException = (ResponseException) throwable;
            details = responseException.getDetails();
            if (details != null) {
                details = details.trim();
            }

        } else if (throwable instanceof EntityServiceException) {
            final StringBuilder detail = new StringBuilder();
            final String detailMessage = ((EntityServiceException) throwable).getDetail();
            if (detailMessage != null) {
                detail.append(detailMessage);
                detail.append("\n\n");
            }
            final String callStack = ((EntityServiceException) throwable).getCallStack();

            if (callStack != null) {
                detail.append(callStack);
                detail.append("\n\n");
            }
            details = detail.toString();
        }

        if ((message == null || message.trim().length() <= 1) &&
                (details == null || details.trim().length() == 0)) {

            final StringBuilder detail = new StringBuilder();
            if (method != null && method.builder != null) {
                detail.append(method.builder.getUrl());
                detail.append("\n\n");
            }
            try {
                throwable.printStackTrace(new PrintStream(new GwtStringBuilderOutputStream(detail)));
            } catch (final RuntimeException e) {
                detail.append(e.getMessage());
            }
            details = detail.toString();
        }

        if (message == null || message.trim().length() <= 1) {
            message = throwable.getClass().getName();
        }

        AlertEvent.fireError(hasHandlers, message, details, callback);
    }
}
