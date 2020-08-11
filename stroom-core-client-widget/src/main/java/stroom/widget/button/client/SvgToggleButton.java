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

public class SvgToggleButton extends BaseSvgButton implements ToggleButtonView {

    private SvgPreset onPreset;
    private SvgPreset offPreset;
    private boolean isOn = false;

    private SvgToggleButton(final SvgPreset onPreset,
                           final SvgPreset offPreset) {
        super(offPreset);
        this.onPreset = onPreset;
        this.offPreset = offPreset;

        addClickHandler(event -> {
            toggleState();
        });
    }

    /**
     * @param onPreset The face to display when in the ON state, e.g. an OFF icon
     * @param offPreset The face to display when in the OFF state, e.g. an ON icon
     */
    public static SvgToggleButton create(final SvgPreset onPreset,
                                         final SvgPreset offPreset) {
        return new SvgToggleButton(onPreset, offPreset);
    }

    public void setState(final boolean isOn) {
        if (this.isOn != isOn) {
            final SvgPreset newState = isOn
                    ? onPreset
                    : offPreset;
            this.isOn = isOn;
            super.setSvgPreset(newState);
        }
    }

    public boolean isOn() {
        return isOn;
    }

    public boolean isOff() {
        return !isOn;
    }

    private void toggleState() {
        setState(!isOn);
    }

    public HandlerRegistration addClickHandler(final ClickHandler onClickedHandler,
                                               final ClickHandler offClickedHandler) {
        return super.addClickHandler(event -> {
            // The state will already have been toggled in toggleState by this point so
            // need to do the opposite
            if (isOn) {
                offClickedHandler.onClick(event);
            } else {
                onClickedHandler.onClick(event);
            }
        });
    }

    public HandlerRegistration addMouseDownHandler(final MouseDownHandler onMouseDownedHandler,
                                                   final MouseDownHandler offMouseDownedHandler) {
        return super.addMouseDownHandler(event -> {
            if (isOn) {
                onMouseDownedHandler.onMouseDown(event);
            } else {
                offMouseDownedHandler.onMouseDown(event);
            }
        });
    }
}
