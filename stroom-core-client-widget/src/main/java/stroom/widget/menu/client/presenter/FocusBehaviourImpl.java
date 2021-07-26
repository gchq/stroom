package stroom.widget.menu.client.presenter;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.DomEvent;
import com.google.gwt.user.client.Event;

public class FocusBehaviourImpl implements FocusBehaviour {

    private final boolean switchFocus;
    private final Runnable focusRunnable;

    public FocusBehaviourImpl(final Event event) {
        switchFocus = event.getType().contains("key");
        final Element element = event.getEventTarget().cast();
        this.focusRunnable = element::focus;
    }

    public FocusBehaviourImpl(final DomEvent<?> event) {
        switchFocus = event.getNativeEvent().getType().contains("key");
        final Element element = event.getRelativeElement();
        this.focusRunnable = element::focus;
    }

    public FocusBehaviourImpl(final NativeEvent nativeEvent) {
        switchFocus = nativeEvent.getType().contains("key");
        final Element element = nativeEvent.getEventTarget().cast();
        this.focusRunnable = element::focus;
    }

    public FocusBehaviourImpl(final NativeEvent nativeEvent,
                              final Element focusElement) {
        switchFocus = nativeEvent.getType().contains("key");
        this.focusRunnable = focusElement::focus;
    }

    public FocusBehaviourImpl(final NativeEvent nativeEvent,
                              final Runnable focusRunnable) {
        this.switchFocus = nativeEvent.getType().contains("key");
        this.focusRunnable = focusRunnable;
    }

    public boolean switchFocus() {
        return switchFocus;
    }

    @Override
    public void refocus() {
        focusRunnable.run();
    }
}
