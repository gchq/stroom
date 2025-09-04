package stroom.alert.client.event;

import com.google.gwt.event.shared.HasHandlers;

public interface FireAlertEventFunction {
    void apply(HasHandlers hasHandlers, String message, String detail, AlertCallback alertCallback);
}
