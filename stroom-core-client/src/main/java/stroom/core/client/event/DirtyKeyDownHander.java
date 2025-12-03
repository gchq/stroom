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

package stroom.core.client.event;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;

public abstract class DirtyKeyDownHander implements KeyDownHandler {

    private static boolean isCut(final KeyDownEvent event) {
        return event.isControlKeyDown() && (event.getNativeKeyCode() == 'X' || event.getNativeKeyCode() == 'x');
    }

    private static boolean isPaste(final KeyDownEvent event) {
        return event.isControlKeyDown() && (event.getNativeKeyCode() == 'V' || event.getNativeKeyCode() == 'v');
    }

    @Override
    public void onKeyDown(final KeyDownEvent event) {
        final int keyCode = event.getNativeKeyCode();

        if (!event.isAltKeyDown() && !event.isControlKeyDown() && !event.isMetaKeyDown()
                && !KeyDownEvent.isArrow(keyCode)) {
            // Fire event if no modifier keys or arrows are pressed.
            onDirty(event);
        } else if (isCut(event) || isPaste(event)) {
            // Fire events on cut and paste.
            onDirty(event);
        }
    }

    public abstract void onDirty(KeyDownEvent event);
}
