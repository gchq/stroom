/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
