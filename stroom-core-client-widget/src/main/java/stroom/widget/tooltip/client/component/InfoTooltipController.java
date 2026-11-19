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

package stroom.widget.tooltip.client.component;

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows an [InfoTooltip] as a grid row's "Information" popup, beside the icon that opened it. An
/// owner keeps one and shows it again, so a second click on the icon closes it.
public class InfoTooltipController extends Controller {

    private final InfoTooltip tooltip;

    /// @param eventBus The app's event bus, which the popup manager listens to.
    /// @param tooltip  The tooltip's content.
    @Inject
    public InfoTooltipController(final EventBus eventBus, final InfoTooltip tooltip) {
        super(eventBus);
        this.tooltip = tooltip;
        initWidget(tooltip);
    }

    /// Shows the tooltip, or hides it if it is shown.
    ///
    /// @param text          The tooltip's content, as plain text.
    /// @param popupPosition Where to show it.
    public void show(final String text, final PopupPosition popupPosition) {
        tooltip.setText(text);
        show(popupPosition, null);
    }

    /// Shows the tooltip, or hides it if it is shown.
    ///
    /// @param html          The tooltip's content.
    /// @param popupPosition Where to show it.
    public void show(final SafeHtml html, final PopupPosition popupPosition) {
        show(html, popupPosition, null);
    }

    /// Shows the tooltip, opened by an element such as an icon, or hides it if it is shown.
    ///
    /// @param html          The tooltip's content.
    /// @param popupPosition Where to show it.
    /// @param anchor        The element that opened it, or null. A mousedown on it doesn't
    ///                      auto-hide the tooltip, so that clicking it again closes the tooltip
    ///                      rather than showing it again.
    public void show(final SafeHtml html, final PopupPosition popupPosition, final Element anchor) {
        tooltip.setHtml(html);
        show(popupPosition, anchor);
    }

    private void show(final PopupPosition popupPosition, final Element anchor) {
        final ShowPopupEvent.Builder builder = ControllerDialog.show(this)
                .popupType(PopupType.POPUP)
                .caption("Information")
                .popupPosition(popupPosition)
                .onShow(e -> tooltip.focus());
        if (anchor != null) {
            builder.addAutoHidePartner(anchor);
        }
        builder.fire();
    }
}
