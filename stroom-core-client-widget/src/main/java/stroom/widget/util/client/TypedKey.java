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

package stroom.widget.util.client;

import com.google.gwt.dom.client.NativeEvent;

/// What a key press types, for lists that send typing to their filter or jump to what is typed.
public final class TypedKey {

    private TypedKey() {
        // Static utility
    }

    /// @param event A key event.
    /// @return The event's key (the browser's `key`, e.g. `a` or `ArrowDown`), or an empty string.
    public static native String keyOf(NativeEvent event) /*-{
        return event.key || "";
    }-*/;

    /// @param event A key event.
    /// @param key   Its key (see [#keyOf(NativeEvent)]).
    /// @return Whether the key types a character (one character, with no Ctrl, Alt or Meta).
    public static boolean isTypedCharacter(final NativeEvent event, final String key) {
        return key.length() == 1
               && !event.getCtrlKey()
               && !event.getAltKey()
               && !event.getMetaKey();
    }
}
