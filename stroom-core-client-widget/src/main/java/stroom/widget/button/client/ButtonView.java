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

package stroom.widget.button.client;

import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.dom.client.HasMouseDownHandlers;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;

public interface ButtonView extends HasClickHandlers, HasMouseDownHandlers, Focus {

    boolean isEnabled();

    void setEnabled(boolean enabled);

    boolean isVisible();

    void setVisible(boolean visible);

    void setTitle(String title);

    /**
     * Retrieves this view as a {@link Widget} so that it can be inserted within
     * the DOM.
     *
     * @return This view as a DOM object.
     */
    Widget asWidget();

    /**
     * Null-safe setting of the enabled state. If buttonView is null, it is a no-op.
     */
    static void setEnabled(final ButtonView buttonView, final boolean enabled) {
        if (buttonView != null) {
            buttonView.setEnabled(enabled);
        }
    }
}
