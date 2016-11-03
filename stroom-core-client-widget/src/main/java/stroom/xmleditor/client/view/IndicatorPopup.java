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

package stroom.xmleditor.client.view;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.PopupPanel;

/**
 * A popup that displays a warning/error message for a given indicator.
 */
public class IndicatorPopup extends PopupPanel {
    public interface Style extends CssResource {
        String indicatorPopup();
    }

    public interface Resources extends ClientBundle {
        @Source("indicatorpopup.css")
        Style style();
    }

    private static volatile Resources resources;

    private final HTML content;

    public IndicatorPopup() {
        if (resources == null) {
            synchronized (IndicatorPopup.class) {
                if (resources == null) {
                    resources = GWT.create(Resources.class);
                    resources.style().ensureInjected();
                }
            }
        }

        content = new HTML();

        setModal(false);
        setAutoHideEnabled(true);
        setStyleName(resources.style().indicatorPopup());

        setWidget(content);
    }

    /**
     * Sets the html to be displayed in the popup.
     */
    public void setHTML(final String html) {
        content.setHTML(html);
    }

    /**
     * Sets the text to be displayed in the popup.
     */
    public void setText(final String text) {
        content.setText(text);
    }
}
