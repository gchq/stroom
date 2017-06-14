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

import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.FlowPanel;
import stroom.svg.client.SvgPreset;

public class ButtonPanel extends FlowPanel {
    public interface Style extends CssResource {
        String layout();
    }

    public interface Resources extends ClientBundle {
        @Source("ButtonPanel.css")
        Style style();
    }

    private static volatile Resources resources;
    private boolean vertical;

    public ButtonPanel() {
        if (resources == null) {
            synchronized (ButtonPanel.class) {
                if (resources == null) {
                    resources = GWT.create(Resources.class);
                    resources.style().ensureInjected();
                }
            }
        }
        setStyleName(resources.style().layout());
    }

    public ImageButtonView add(final String title, final ImageResource enabledImage, final ImageResource disabledImage,
            final boolean enabled) {
        final ImageButton button = createButton(title, enabledImage, disabledImage, enabled);
        add(button);
        return button;
    }


    public ButtonView add(final SvgPreset preset) {
        final SvgButton button = createButton(preset);
        add(button);
        return button;
    }

    private ImageButton createButton(final String title, final ImageResource enabledImage,
            final ImageResource disabledImage, final boolean enabled) {
        final ImageButton button = new ImageButton();
        button.setTitle(title);
        button.setEnabledImage(enabledImage);
        button.setDisabledImage(disabledImage);
        button.setEnabled(enabled);
        if (vertical) {
            button.getElement().getStyle().setDisplay(Display.BLOCK);
        }
        return button;
    }

    private SvgButton createButton(final SvgPreset preset) {
        final SvgButton button = SvgButton.create(preset);
        if (vertical) {
            button.getElement().getStyle().setDisplay(Display.BLOCK);
        }
        return button;
    }

    public void setVertical(final boolean vertical) {
        this.vertical = vertical;
    }
}
