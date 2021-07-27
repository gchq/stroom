package stroom.widget.menu.client.presenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.List;

public class CurrentFocus {

    private static final List<Runnable> stack = new ArrayList<>();
    private static Runnable retainedRunnable;

    public static void push() {
        if (retainedRunnable != null) {
            GWT.log("Pushing retained");
            stack.add(retainedRunnable);
            retainedRunnable = null;

        } else {
            final Element focusElement = getActiveElement(Document.get());
            GWT.log("Pushing active element: " + focusElement);
            if (focusElement != null) {
                final Runnable runnable = focusElement::focus;
                stack.add(runnable);
            }
        }
    }

    public static void push(final Runnable runnable) {
        GWT.log("Push " + stack.size());
        stack.add(runnable);
    }

    public static void pop() {
        if (stack.size() > 0) {
            final Runnable runnable = stack.remove(stack.size() - 1);
            runnable.run();
        }
        GWT.log("Pop " + stack.size());
    }

    public static void retain() {
        if (stack.size() > 0) {
            retainedRunnable = stack.remove(stack.size() - 1);
        }
        GWT.log("Retain " + stack.size());
    }

    public static native Element getActiveElement(Document doc) /*-{
        return doc.activeElement;
    }-*/;
}
