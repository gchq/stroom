package stroom.item.client;

import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.List;

public abstract class EventBinder {

    private final List<HandlerRegistration> handlerRegistrations = new java.util.ArrayList<HandlerRegistration>();
    private boolean bound;

    public void registerHandler(final HandlerRegistration handlerRegistration) {
        handlerRegistrations.add(handlerRegistration);
    }

    public void bind() {
        if (!bound) {
            bound = true;
            onBind();
        }
    }

    public void unbind() {
        if (bound) {
            bound = false;
            for (final HandlerRegistration registration : handlerRegistrations) {
                registration.removeHandler();
            }
            handlerRegistrations.clear();
            onUnbind();
        }
    }

    protected void onBind() {

    }

    protected void onUnbind() {
    }
}
