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

import stroom.svg.client.Preset;

public class SvgToggleButton extends BaseSvgButton implements ToggleButtonView {

    private final Preset onPreset;
    private final Preset offPreset;
    private boolean isOn = false;

    private SvgToggleButton(final Preset onPreset,
                            final Preset offPreset) {
        super(offPreset);
        this.onPreset = onPreset;
        this.offPreset = offPreset;

        addClickHandler(event -> toggleState());
    }

    @Override
    public void focus() {
        getElement().focus();
    }

    /**
     * @param onPreset  The face to display when in the ON state, e.g. an OFF icon
     * @param offPreset The face to display when in the OFF state, e.g. an ON icon
     */
    public static SvgToggleButton create(final Preset onPreset,
                                         final Preset offPreset) {
        return new SvgToggleButton(onPreset, offPreset);
    }

    @Override
    public void setState(final boolean isOn) {
        if (this.isOn != isOn) {
            this.isOn = isOn;
            if (isOn) {
                super.toggleSvgPreset(onPreset);
            } else {
                super.toggleSvgPreset(offPreset);
            }
        }
    }

    @Override
    public boolean getState() {
        return isOn;
    }

    private void toggleState() {
        setState(!isOn);
    }
}
