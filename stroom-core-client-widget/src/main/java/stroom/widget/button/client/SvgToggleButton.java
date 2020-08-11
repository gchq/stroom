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

public class SvgToggleButton extends SvgButton implements ToggleButtonView {

    private SvgPreset primaryPreset;
    private SvgPreset secondaryPreset;
    private boolean isInPrimaryState = true;

    public SvgToggleButton(final SvgPreset primaryPreset,
                           final SvgPreset secondaryPreset) {
        super(primaryPreset);
        this.primaryPreset = primaryPreset;
        this.secondaryPreset = secondaryPreset;

        addClickHandler(event -> {
            toggleState();
        });
    }

    public static SvgToggleButton create(final SvgPreset primaryPreset,
                                         final SvgPreset secondaryPreset) {
        return new SvgToggleButton(primaryPreset, secondaryPreset);
    }

    public SvgPreset setIsInPrimaryState(final boolean isInPrimaryState) {
        final SvgPreset newState;
        if (isInPrimaryState) {
            newState = primaryPreset;
        } else {
            newState = secondaryPreset;
        }
        if (this.isInPrimaryState != isInPrimaryState) {
            this.isInPrimaryState = isInPrimaryState;
            super.setSvgPreset(newState);
        }
        return newState;
    }
    public boolean isInPrimaryState() {
        return isInPrimaryState;
    }

    private SvgPreset toggleState() {
        return setIsInPrimaryState(!isInPrimaryState);
    }

    public HandlerRegistration addClickHandler(final ClickHandler primaryHandler,
                                               final ClickHandler secondaryHandler) {
        return super.addClickHandler(event -> {
            // The state will already have been toggled in toggleState by this point so
            // need to do the opposite
            if (isInPrimaryState) {
                secondaryHandler.onClick(event);
            } else {
                primaryHandler.onClick(event);
            }
        });
    }

    public HandlerRegistration addMouseDownHandler(final MouseDownHandler primaryHandler,
                                                   final MouseDownHandler secondaryHandler) {
        return super.addMouseDownHandler(event -> {
            if (isInPrimaryState) {
                primaryHandler.onMouseDown(event);
            } else {
                secondaryHandler.onMouseDown(event);
            }
        });
    }
}
