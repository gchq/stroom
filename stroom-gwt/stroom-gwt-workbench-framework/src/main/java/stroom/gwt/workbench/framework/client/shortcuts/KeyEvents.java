/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.framework.client.shortcuts;

import stroom.gwt.workbench.framework.client.BrowserUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;

/// Helpers for working out keyboard shortcuts from browser keyboard events.
public final class KeyEvents {

    private KeyEvents() {
        // Static utility
    }

    /// @param event A keydown event.
    /// @return The keys pressed, or null if only a modifier was pressed.
    public static KeyCombo toCombo(final NativeEvent event) {
        final String key = KeyCombo.fromKeyCode(BrowserUtil.getKeyCode(event));
        if (key == null) {
            return null;
        }
        return new KeyCombo(event.getAltKey(), event.getCtrlKey(), event.getShiftKey(),
                event.getMetaKey(), key);
    }

    /// @param event A keyboard event.
    /// @return True if the event's target is a field the user types into.
    public static boolean isTyping(final NativeEvent event) {
        final EventTarget target = event.getEventTarget();
        if (target == null || !Element.is(target)) {
            return false;
        }
        final Element element = Element.as(target);
        final String tagName = element.getTagName();
        return "INPUT".equalsIgnoreCase(tagName)
               || "TEXTAREA".equalsIgnoreCase(tagName)
               || "SELECT".equalsIgnoreCase(tagName)
               || isContentEditable(element);
    }

    /// Uses the DOM's `isContentEditable`, which covers `contenteditable=""`,
    /// `plaintext-only` and elements inside an editable element.
    private static native boolean isContentEditable(Element element) /*-{
        return !!element.isContentEditable;
    }-*/;
}
