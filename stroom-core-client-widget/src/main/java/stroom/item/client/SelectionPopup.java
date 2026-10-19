/*
 * Copyright 2023 Crown Copyright
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
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.PopupPanel;

import java.util.function.Supplier;

public class SelectionPopup<T, I extends SelectionItem> extends Composite {

    private final PopupPanel popupPanel;
    private final SelectionList<T, I> selectionList;
    // What was typed to open the list, which it goes to once shown, or null if nothing was
    private String typed;
    // Whether the list has been shown and has the focus
    private boolean shown;

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            super.onBind();
            registerHandler(popupPanel.addCloseHandler(event -> hide()));
            registerHandler(selectionList.addCloseHandler(event -> hide()));
        }
    };

    public SelectionPopup() {
        popupPanel = new PopupPanel() {
            @Override
            protected void onPreviewNativeEvent(final NativePreviewEvent event) {
                super.onPreviewNativeEvent(event);
                // Escape closes the list wherever the focus is (e.g. still in the box that opened
                // it), and is cancelled so that nothing else (e.g. a dialog or the window) sees it
                if (event.getTypeInt() == Event.ONKEYDOWN
                    && KeyBinding.test(event.getNativeEvent()) == Action.CLOSE) {
                    event.cancel();
                    hide();
                }
            }
        };
        selectionList = new SelectionList<>();
        selectionList.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.ENABLED);

        final SimplePopupLayout simplePopupLayout = new SimplePopupLayout();
        simplePopupLayout.setContent(selectionList);

        popupPanel.add(simplePopupLayout);
        popupPanel.setAutoHideEnabled(true);
        // Styled by selection-popup.css
        popupPanel.setStyleName("selection-popup");
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
        show(relativeElement, null);
    }

    /// Shows the list below an element, and goes to what was typed to open it, if anything (see
    /// [SelectionList#typeAhead(String)]).
    ///
    /// @param relativeElement The element to show the list below, e.g. the box that opened it.
    /// @param typed           What was typed to open the list, or null if nothing was.
    public void show(final Element relativeElement, final String typed) {
        this.typed = typed;
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
        Scheduler.get().scheduleDeferred(() -> {
            shown = true;
            if (typed == null) {
                selectionList.focus();
            } else {
                selectionList.typeAhead(typed);
                typed = null;
            }
        });
    }

    /// Goes to more typed text: what is typed in the box after it opened the list, before the list
    /// took the focus. It is kept until the list is shown.
    ///
    /// @param text The text typed, e.g. a character.
    public void type(final String text) {
        if (shown) {
            selectionList.typeAhead(text);
        } else {
            typed = typed == null
                    ? text
                    : typed + text;
        }
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

    /// Names the popup's list for screen readers, e.g. after the box that opened it.
    ///
    /// @param name The list's accessible name.
    public void setAccessibleName(final String name) {
        selectionList.setAccessibleName(name);
    }

    /// @return The id of the popup's list box element, for a box that controls it (`aria-controls`).
    public String getListBoxId() {
        return selectionList.getListBoxId();
    }
}
