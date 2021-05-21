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

import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.user.client.ui.FlowPanel;

public class ButtonPanel extends FlowPanel {

    private boolean vertical;

    public ButtonPanel() {
        setStyleName("buttonPanel-layout");
    }

    public ButtonView addButton(final SvgPreset preset) {
        final SvgButton button = createButton(preset);
        add(button);
        return button;
    }

    public ToggleButtonView addToggleButton(final SvgPreset primaryPreset,
                                            final SvgPreset secondaryPreset) {
        final SvgToggleButton button = createToggleButton(primaryPreset, secondaryPreset);
        add(button);
        return button;
    }

    private SvgButton createButton(final SvgPreset preset) {
        final SvgButton button = SvgButton.create(preset);
        if (vertical) {
            button.getElement().getStyle().setDisplay(Display.BLOCK);
        }
        return button;
    }

    private SvgToggleButton createToggleButton(final SvgPreset primaryPreset,
                                               final SvgPreset secondaryPreset) {
        final SvgToggleButton button = SvgToggleButton.create(primaryPreset, secondaryPreset);
        if (vertical) {
            button.getElement().getStyle().setDisplay(Display.BLOCK);
        }
        return button;
    }

    public void setVertical(final boolean vertical) {
        this.vertical = vertical;
    }
}
