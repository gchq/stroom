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

package stroom.query.client.result.component;

import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;

/// Lays a visualisation's frame ([VisFrame], which is on the page's root) over the space a pane
/// keeps for it, clipped to the dashboard's scrolled area if the pane is in one. A dashboard's
/// Visualisation pane and a query's result visualisation both use it.
public final class VisFrameLayout {

    private VisFrameLayout() {
        // Static utility
    }

    /// Moves and sizes the frame to the space.
    ///
    /// @param visFrame  The frame.
    /// @param container The space kept for it, or `null` to move the frame off the page.
    public static void layOver(final VisFrame visFrame, final Element container) {
        final Element dashboard = getParentByClass(container, "flex-layout__scroll");

        if (container != null) {
            final Rect inner = ElementUtil.getBoundingClientRectPlusWindowScroll(container);
            if (dashboard != null) {
                final Rect outer = ElementUtil.getClientRect(dashboard);
                final Rect min = Rect.min(outer, inner);

                visFrame.setContainerPositionAndSize(
                        min.getLeft(),
                        min.getTop(),
                        min.getWidth(),
                        min.getHeight());

                visFrame.setInnerPositionAndSize(
                        Math.min(0, inner.getLeft() - outer.getLeft()),
                        Math.min(0, inner.getTop() - outer.getTop()),
                        inner.getWidth(),
                        inner.getHeight());
            } else {
                visFrame.setInnerPositionAndSize(
                        inner.getLeft(),
                        inner.getTop(),
                        inner.getWidth(),
                        inner.getHeight());
            }

            visFrame.onResize();

        } else {
            visFrame.setContainerPositionAndSize(
                    -1000,
                    -1000,
                    1000,
                    1000);

            visFrame.onResize();
        }
    }

    private static Element getParentByClass(final Element element, final String className) {
        Element el = element;
        while (el != null &&
               (el.getClassName() == null ||
                !el.getClassName().contains(className))) {
            el = el.getParentElement();
        }
        return el;
    }
}
