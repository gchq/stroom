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

import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.stream.StreamSupport;

public class ButtonPanel extends FlowPanel {

    private boolean vertical;

    public ButtonPanel() {
        setStyleName("button-container");
    }

    public void addButton(final ButtonView view) {
        add(view.asWidget());
    }

    public void addButtons(final List<ButtonView> buttons) {
        for (final ButtonView buttonView : buttons) {
            addButton(buttonView);
        }
    }

    public ButtonView addButton(final Preset preset) {
        final ButtonView button = createButton(preset);
        add((Widget) button);
        return button;
    }

    public void removeButton(final ButtonView buttonView) {
        remove(buttonView.asWidget());
    }

    public ToggleButtonView addToggleButton(final Preset primaryPreset,
                                            final Preset secondaryPreset) {
        final SvgToggleButton button = createToggleButton(primaryPreset, secondaryPreset);
        add(button);
        return button;
    }

    public boolean containsButton(final ButtonView buttonView) {
        if (buttonView == null) {
            return false;
        } else {
            return StreamSupport.stream(spliterator(), false)
                    .anyMatch(widget ->
                            widget.equals(buttonView));
        }
    }

    public ButtonView createButton(final Preset preset) {
        final SvgButton button = SvgButton.create(preset);
        if (vertical) {
            button.getElement().getStyle().setDisplay(Display.BLOCK);
        }
        return button;
    }

    private SvgToggleButton createToggleButton(final Preset primaryPreset,
                                               final Preset secondaryPreset) {
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
