package stroom.widget.popup.client.view;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

import java.util.Stack;

class CurrentFocus {

    private static final Stack<Runnable> stack = new Stack<>();
    private static Runnable retainedRunnable;

    static void push() {
        if (retainedRunnable != null) {
            stack.push(retainedRunnable);
            retainedRunnable = null;

        } else {
            final Element focusElement = getActiveElement(Document.get());
            if (focusElement != null) {
                final Runnable runnable = focusElement::focus;
                stack.push(runnable);
            }
        }
    }

    static void pop() {
        if (!stack.empty()) {
            final Runnable runnable = stack.pop();
            runnable.run();
        }
    }

    public static native Element getActiveElement(Document doc) /*-{
        return doc.activeElement;
    }-*/;
}
