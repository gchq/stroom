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

package stroom.widget.tooltip.client.presenter;

import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;
import stroom.widget.tooltip.client.component.InfoTooltip;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows an [InfoTooltip] as a grid row's "Information" popup, beside the icon that opened it. A
/// thin presenter over the component, kept while its owners (the lists) are presenters, as an owner
/// keeps one and shows it again (a second click on the icon closes it).
public class TooltipPresenter extends MyPresenterWidget<View> {

    private final InfoTooltip tooltip;

    @Inject
    public TooltipPresenter(final EventBus eventBus, final InfoTooltip tooltip) {
        super(eventBus, tooltip::asWidget);
        this.tooltip = tooltip;
    }

    public void show(final String text, final PopupPosition popupPosition) {
        tooltip.setText(text);
        show(popupPosition);
    }

    public void show(final SafeHtml html, final PopupPosition popupPosition) {
        show(html, popupPosition, null);
    }

    /// Shows the tooltip, opened by an element such as an icon.
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

    private void show(final PopupPosition popupPosition) {
        show(popupPosition, null);
    }

    private void show(final PopupPosition popupPosition, final Element anchor) {
        final ShowPopupEvent.Builder builder = ShowPopupEvent.builder(this)
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
