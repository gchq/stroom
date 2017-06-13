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

    public GlyphButtonView add(final GlyphIcon preset) {
        final GlyphButton button = createButton(preset);
        add(button);
        return button;
    }

    public SVGButtonView add(final SVGIcon preset) {
        final SVGButton button = createButton(preset);
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

    private GlyphButton createButton(final GlyphIcon preset) {
        final GlyphButton button = new GlyphButton();
        button.setIcon(preset.getGlyph());
        button.setColour(preset.getColourSet());
        button.setTitle(preset.getTitle());
        button.setEnabled(preset.isEnabled());
        if (vertical) {
            button.getElement().getStyle().setDisplay(Display.BLOCK);
        }
        return button;
    }

    private SVGButton createButton(final SVGIcon preset) {
        final SVGButton button = new SVGButton();
        button.setIcon(preset.getUrl());
        button.setTitle(preset.getTitle());
        button.setEnabled(preset.isEnabled());
        if (vertical) {
            button.getElement().getStyle().setDisplay(Display.BLOCK);
        }
        return button;
    }

    public void setVertical(final boolean vertical) {
        this.vertical = vertical;
    }
}
