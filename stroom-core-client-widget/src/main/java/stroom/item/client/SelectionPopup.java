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

import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.popup.client.Position;
import stroom.widget.popup.client.PositionUtil;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.Rect;
import stroom.widget.util.client.TabOrder;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Supplier;

/// A [SelectionList] in a popup below the box that opens it (a selection box, a selection box
/// cell, the table's Add Column button), styled by selection-popup.css. Escape, Tab, a click outside
/// or a choice closes it; [#setOnClose(Runnable)] is told however it closes.
///
/// @param <T> The type of an option's value.
/// @param <I> The type of an option.
public class SelectionPopup<T, I extends SelectionItem> {

    private static final Binder BINDER = GWT.create(Binder.class);

    private final PopupPanel popupPanel;
    @UiField
    SelectionList<T, I> selectionList;
    private Runnable onClose;
    // What was typed to open the list, which it goes to once shown, or null if nothing was
    private String typed;
    // Whether the list opens with the focus on its last option (true) or its first (false), as End or
    // Home opens it, or null to open it at its chosen option
    private Boolean openAtLast;
    // Whether the list has been shown and has the focus
    private boolean shown;

    /// Makes a popup with an empty list; [#setModel] gives it its options.
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
                } else if (event.getTypeInt() == Event.ONKEYDOWN && isTabInList(event.getNativeEvent())) {
                    // Tab closes the list, leaving the value as it is, and goes on to what follows the
                    // box; Shift+Tab closes it and goes back to the box (which takes the focus when
                    // the list closes)
                    final boolean backwards = event.getNativeEvent().getShiftKey();
                    event.cancel();
                    event.getNativeEvent().preventDefault();
                    hide();
                    if (!backwards) {
                        TabOrder.focusNext(activeElement(), false);
                    }
                }
            }
        };
        // The list, as big as its options within the popup's limits (selection-popup.css)
        popupPanel.add(BINDER.createAndBindUi(this));
        // However it closes, and before it forgets where the list was (hide), as the boxes' own
        // close handlers on the panel were
        popupPanel.addCloseHandler(event -> {
            if (onClose != null) {
                onClose.run();
            }
            // The list forgets where it was however the popup closes (hide, or a click outside it)
            selectionList.destroy();
        });
        // The list asks to close (Escape, or choosing with nothing to choose) only while it is shown
        selectionList.setOnClose(() -> {
            if (popupPanel.isShowing()) {
                hide();
            }
        });
        selectionList.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.ENABLED);
        popupPanel.setAutoHideEnabled(true);
        // Styled by selection-popup.css
        popupPanel.setStyleName("selection-popup");
    }

    // Whether a key is Tab (or Shift+Tab) with the focus in the list
    private boolean isTabInList(final NativeEvent event) {
        final EventTarget target = event.getEventTarget();
        return event.getKeyCode() == KeyCodes.KEY_TAB
               && !event.getCtrlKey() && !event.getAltKey() && !event.getMetaKey()
               && Element.is(target)
               && popupPanel.getElement().isOrHasChild(Element.as(target));
    }

    private static native Element activeElement() /*-{
        return $doc.activeElement;
    }-*/;

    public void addAutoHidePartner(final Element partner) {
        popupPanel.addAutoHidePartner(partner);
    }

    /// Gives the popup's list its options, once.
    ///
    /// @param model The options.
    public void setModel(final SelectionListModel<T, I> model) {
        selectionList.setModel(model);
    }

    public void refresh() {
        selectionList.refresh();
    }

    public MultiSelectionModel<I> getSelectionModel() {
        return selectionList.getSelectionModel();
    }

    /// @return Whether the popup is open.
    public boolean isShowing() {
        return popupPanel.isShowing();
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
        this.openAtLast = null;
        final Rect relativeRect = new Rect(relativeElement);
        final PopupPosition position = new PopupPosition(relativeRect, PopupLocation.BELOW);
        show(position);
    }

    /// Shows the list below an element with the focus on its first or last option, as Home or End
    /// opens a selection box's list (WAI-ARIA combobox).
    ///
    /// @param relativeElement The element to show the list below, e.g. the box that opened it.
    /// @param last            Whether to go to the last option rather than the first.
    public void showAtFirstOrLast(final Element relativeElement, final boolean last) {
        show(relativeElement, null);
        this.openAtLast = last;
    }

    private void show(final PopupPosition popupPosition) {
        if (popupPanel.isShowing()) {
            hide();
        } else {
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
        }
    }

    private void afterShow() {
        Scheduler.get().scheduleDeferred(() -> {
            shown = true;
            if (openAtLast != null) {
                selectionList.focusFirstOrLast(openAtLast);
                openAtLast = null;
            } else if (typed == null) {
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
        if (popupPanel.isShowing()) {
            // Its close handler makes the list forget where it was
            popupPanel.hide();
        } else {
            selectionList.destroy();
        }
    }

    /// Sets what is told when the popup closes, however it closes (a choice, Escape, Tab or a click
    /// outside it), e.g. to put the focus back on the box that opened it.
    ///
    /// @param onClose What is told, or null for nothing.
    public void setOnClose(final Runnable onClose) {
        this.onClose = onClose;
    }

    /// Sets what the list's quick filter shows as its help, e.g. the fields it can filter on.
    ///
    /// @param popupTextSupplier Gives the help.
    public void setPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        selectionList.setPopupTextProvider(popupTextSupplier);
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


    // --------------------------------------------------------------------------------


    // The owner is raw: a binder can't name a type variable
    @SuppressWarnings("rawtypes")
    interface Binder extends UiBinder<Widget, SelectionPopup> {

    }
}
