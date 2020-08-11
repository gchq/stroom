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

import stroom.svg.client.SvgPreset;

import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.shared.HandlerRegistration;

public interface ToggleButtonView {

    boolean isEnabled();

    void setEnabled(boolean enabled);

    boolean isVisible();

    void setVisible(boolean visible);

    void setTitle(String title);

    SvgPreset setIsInPrimaryState(final boolean isInPrimaryState);

    boolean isInPrimaryState();

    HandlerRegistration addClickHandler(final ClickHandler primaryHandler,
                                        final ClickHandler secondaryHandler);

    HandlerRegistration addMouseDownHandler(final MouseDownHandler primaryHandler,
                                            final MouseDownHandler secondaryHandler);
}
