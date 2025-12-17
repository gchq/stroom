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

package stroom.data.grid.client;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.Event;

public final class MouseHelper {

    private MouseHelper() {
        // Utility class.
    }

    public static boolean mouseIsOverElement(final Event event, final Element element) {
        return mouseIsOverElement(event, element.getAbsoluteLeft(), element.getAbsoluteRight(),
                element.getAbsoluteTop(), element.getAbsoluteBottom());
    }

    public static boolean mouseIsOverElement(final Event event, final int left, final int right, final int top,
                                             final int bottom) {
        return left <= event.getClientX() && right >= event.getClientX() && top <= event.getClientY()
                && bottom >= event.getClientY();
    }

    public static boolean mouseIsOverElement(final NativeEvent event, final Element element) {
        return mouseIsOverElement(event, element.getAbsoluteLeft(), element.getAbsoluteRight(),
                element.getAbsoluteTop(), element.getAbsoluteBottom());
    }

    public static boolean mouseIsOverElement(final NativeEvent event, final int left, final int right, final int top,
                                             final int bottom) {
        return left <= event.getClientX() && right >= event.getClientX() && top <= event.getClientY()
                && bottom >= event.getClientY();
    }

    /**
     * Gets the mouse x-position relative to a given element.
     *
     * @param target the element whose coordinate system is to be used
     * @return the relative x-position
     */
    public static int getRelativeX(final NativeEvent e, final Element target) {
        return e.getClientX() - target.getAbsoluteLeft() + target.getScrollLeft()
                + target.getOwnerDocument().getScrollLeft();
    }

    /**
     * Gets the mouse y-position relative to a given element.
     *
     * @param target the element whose coordinate system is to be used
     * @return the relative y-position
     */
    public static int getRelativeY(final NativeEvent e, final Element target) {
        return e.getClientY() - target.getAbsoluteTop() + target.getScrollTop()
                + target.getOwnerDocument().getScrollTop();
    }
}
