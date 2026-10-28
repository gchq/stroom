/*
 * Copyright 2016 Crown Copyright
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

package stroom.editor.client.ace;

import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SimplePanel;

/// Measures the browser's scroll bars, once: a box with scroll bars (`scrollbar-metrics`,
/// `scrollbar-metrics.css`), out of sight, whose content is bigger than it.
public final class ScrollbarMetrics {

    private static final SimplePanel inner = new SimplePanel();
    private static final SimplePanel outer = new SimplePanel();
    private static int horizontalScrollBarWidth = -1;
    private static int verticalScrollBarWidth = -1;

    private ScrollbarMetrics() {
        // Utility class.
    }

    public static int getHorizontalScrollBarWidth() {
        if (horizontalScrollBarWidth == -1) {
            calc();
        }
        return horizontalScrollBarWidth;
    }

    public static int getVerticalScrollBarWidth() {
        if (verticalScrollBarWidth == -1) {
            calc();
        }
        return verticalScrollBarWidth;
    }

    private static void calc() {
        inner.setStyleName("scrollbar-metrics__content");
        outer.setWidget(inner);
        outer.setStyleName("scrollbar-metrics");
        RootPanel.get().add(outer);

        horizontalScrollBarWidth = 100 - outer.getElement().getClientHeight();
        verticalScrollBarWidth = 100 - outer.getElement().getClientWidth();

        RootPanel.get().remove(outer);
    }
}
