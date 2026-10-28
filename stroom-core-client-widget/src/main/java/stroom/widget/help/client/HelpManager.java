/*
 * Copyright 2024 Crown Copyright
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

package stroom.widget.help.client;

import stroom.widget.tooltip.client.event.ShowHelpEvent;

import com.google.gwt.dom.client.Element;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

public class HelpManager {

    private HelpPopup currentPopup;
    private Element currentElement;

    @Inject
    public HelpManager(final EventBus eventBus) {
        eventBus.addHandler(ShowHelpEvent.getType(), this::onShow);
    }

    private void onShow(final ShowHelpEvent showHelpEvent) {
        final Element element = showHelpEvent.getElement();
        // A second click on the help button that showed the popup closes it
        if (currentPopup != null && currentPopup.isShowing() && Objects.equals(element, currentElement)) {
            currentPopup.hide();
            return;
        }

        final HelpPopup popup = new HelpPopup();
        popup.setContent(showHelpEvent.getContent());
        popup.setMarkdown(true);
        popup.setAccessibleName("Help");
        popup.setOpener(element);
        popup.setOnClose(() -> {
            if (currentPopup == popup) {
                currentPopup = null;
                currentElement = null;
            }
        });
        currentPopup = popup;
        currentElement = element;
        popup.show(showHelpEvent.getPopupPosition());
    }
}
