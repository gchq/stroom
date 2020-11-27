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

package stroom.widget.button.client;

import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.shared.HandlerRegistration;

public interface ToggleButtonView {

    boolean isEnabled();

    void setEnabled(boolean enabled);

    boolean isVisible();

    void setVisible(boolean visible);

    void setTitle(String title);

    void setState(final boolean isOn);

    boolean isOn();

    boolean isOff();

    /**
     * @param onClickedHandler The handler that will be invoked when the button is clicked while
     *                         the ON face is showing.
     * @param offClickedHandler The handler that will be invoked when the button is clicked while
     *                          the OFF face is showing.
     */
    HandlerRegistration addClickHandler(final ClickHandler onClickedHandler,
                                        final ClickHandler offClickedHandler);

    /**
     * @param onMouseDownedHandler The handler that will be invoked when the button is mouse-downed while
     *                             the ON face is showing.
     * @param offMouseDownedHandler The handler that will be invoked when the button is mouse-downed while
     *                              the OFF face is showing.
     */
    HandlerRegistration addMouseDownHandler(final MouseDownHandler onMouseDownedHandler,
                                            final MouseDownHandler offMouseDownedHandler);
}
