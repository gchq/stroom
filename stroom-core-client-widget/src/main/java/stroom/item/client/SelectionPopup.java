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

package stroom.item.client;

import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.Position;
import stroom.widget.popup.client.presenter.PositionUtil;
import stroom.widget.popup.client.view.SimplePopupLayout;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.PopupPanel;

import java.util.function.Supplier;

public class SelectionPopup<T, I extends SelectionItem> extends Composite {

    private final PopupPanel popupPanel;
    private final SelectionList<T, I> selectionList;

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            super.onBind();
            registerHandler(popupPanel.addCloseHandler(event -> hide()));
            registerHandler(selectionList.addCloseHandler(event -> hide()));
        }
    };

    public SelectionPopup() {
        popupPanel = new PopupPanel();
        selectionList = new SelectionList<>();
        selectionList.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.ENABLED);

        final SimplePopupLayout simplePopupLayout = new SimplePopupLayout();
        simplePopupLayout.setContent(selectionList);

        popupPanel.add(simplePopupLayout);
        popupPanel.setAutoHideEnabled(true);
        popupPanel.setStyleName("SelectionPopup");
    }

    public void addAutoHidePartner(final Element partner) {
        popupPanel.addAutoHidePartner(partner);
    }

    public void init(final SelectionListModel<T, I> model) {
        selectionList.init(model);
    }

    public void refresh() {
        selectionList.refresh();
    }

    public MultiSelectionModel<I> getSelectionModel() {
        return selectionList.getSelectionModel();
    }

    public void setEnabled(final boolean enabled) {
        if (!enabled) {
            popupPanel.hide();
        }
    }

    public void show(final Element relativeElement) {
        final Rect relativeRect = new Rect(relativeElement);
        final PopupPosition position = new PopupPosition(relativeRect, PopupLocation.BELOW);
        show(position);
    }

    private void show(final PopupPosition popupPosition) {
        if (popupPanel.isShowing()) {
            hide();
        } else {
            eventBinder.bind();
            popupPanel.setPopupPosition(-1000, -1000);
            popupPanel.show();
            // Defer the command to position and make visible because we need the
            // popup to size first.
            Scheduler.get().scheduleDeferred(() -> {
                // Now get the popup size.
                final int offsetWidth = popupPanel.getOffsetWidth();
                final int offsetHeight = popupPanel.getOffsetHeight();
                final Position position = PositionUtil
                        .getPosition(4, popupPosition, offsetWidth, offsetHeight);
                popupPanel.setPopupPosition(
                        (int) position.getLeft(),
                        (int) position.getTop());
                afterShow();
            });

//            popupPanel.setPopupPositionAndShow((offsetWidth, offsetHeight) -> {
//                final Position position = PositionUtil
//                        .getPosition(4, popupPosition, offsetWidth, offsetHeight);
//                popupPanel.setPopupPosition(
//                        (int) position.getLeft(),
//                        (int) position.getTop());
//                afterShow();
//            });
        }
    }

    private void afterShow() {
        Scheduler.get().scheduleDeferred(selectionList::focus);
    }

    public void hide() {
        eventBinder.unbind();
        popupPanel.hide();
        selectionList.destroy();
    }

    public HandlerRegistration addCloseHandler(final CloseHandler<PopupPanel> handler) {
        return popupPanel.addCloseHandler(handler);
    }

    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        selectionList.registerPopupTextProvider(popupTextSupplier);
    }
}
