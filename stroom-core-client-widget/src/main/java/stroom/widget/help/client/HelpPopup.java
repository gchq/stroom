/*
 * Copyright 2026 Crown Copyright
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

import stroom.svg.shared.SvgImage;
import stroom.widget.popup.client.AbstractPopupPanel;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.Popup;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.PopupUtil;
import stroom.widget.text.client.Html;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.InputModality;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A popup of help, shown beside the button that opens it: a form group's help and a quick
/// filter's syntax help. It is a dialog that isn't modal, named for what it helps with, and closes
/// on Escape, a click outside it, or a second press of its button (whose `aria-expanded` says
/// whether it is open).
///
/// Opened from the keyboard (F1 in a form group's field, or Enter on a help button), it takes the
/// focus, so that a screen reader reads it, and gives it back when it closes. Opened with the mouse
/// it leaves the focus where it is, e.g. in the field being filled in.
///
/// The BEM block `help-popup` (`help-popup.css`), its content `help-popup__content`.
public final class HelpPopup extends AbstractPopupPanel implements Popup {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String BLOCK = "help-popup";
    private static final String ARIA_EXPANDED = "aria-expanded";
    // The room beside its button, which limits its width (help-popup.css)
    private static final String ROOM = "--help-popup__room";

    @UiField
    Html content;
    private Element opener;
    private Runnable onClose;

    /// Makes an empty help popup, which closes itself when the user clicks outside it, presses
    /// Escape or the browser's history changes (e.g. Back). Give it its content with
    /// [#setContent(SafeHtml)] and its name with [#setAccessibleName(String)] before showing it. Its
    /// content's markup is `HelpPopup.ui.xml`.
    public HelpPopup() {
        setAutoHideEnabled(true);
        setAutoHideOnHistoryEventsEnabled(true);
        super.setWidget(BINDER.createAndBindUi(this));
        setStyleName(BLOCK);
        // A dialog that isn't modal, for screen readers
        getElement().setAttribute("role", "dialog");
        super.setOnAction(action -> {
            if (action == DialogAction.CLOSE) {
                hide();
            }
        });
        addCloseHandler(event -> {
            setOpenerExpanded(false);
            if (onClose != null) {
                onClose.run();
            }
        });
    }

    /// @param onClose Called when the popup has closed, however it was closed.
    public void setOnClose(final Runnable onClose) {
        this.onClose = onClose;
    }

    /// @param html The help, as HTML.
    public void setContent(final SafeHtml html) {
        content.setHTML(html);
    }

    /// Styles the content as rendered markdown is elsewhere (the shared `markdown` block), e.g. a
    /// form group's help, rather than as HTML as it is (e.g. a quick filter's syntax help, with its
    /// own tables).
    ///
    /// @param markdown Whether the content is rendered markdown.
    public void setMarkdown(final boolean markdown) {
        setStyleName(BLOCK + "--markdown", markdown);
        content.setStyleName("markdown", markdown);
    }

    /// @param name The popup's name for screen readers, e.g. `Help`.
    public void setAccessibleName(final String name) {
        getElement().setAttribute("aria-label", name);
    }

    /// Marks a button as opening a help popup (`aria-haspopup="dialog"`), with the popup closed
    /// (`aria-expanded="false"`). The popup keeps `aria-expanded` up to date once it is given the
    /// button with [#setOpener].
    ///
    /// @param button The button's element.
    public static void markOpener(final Element button) {
        // Listening from now, so that the press that opens the popup is seen
        InputModality.install();
        button.setAttribute("aria-haspopup", "dialog");
        button.setAttribute(ARIA_EXPANDED, "false");
    }

    /// Sets the button that opens the popup. A mousedown on it doesn't count as a click outside the
    /// popup (so its click can close the popup), and its `aria-expanded` says whether the popup is
    /// open.
    ///
    /// @param opener The button's element, or null.
    public void setOpener(final Element opener) {
        this.opener = opener;
        if (opener != null) {
            addAutoHidePartner(opener);
        }
    }

    /// Shows the popup, placed by `popupPosition`. It takes the focus if the keyboard is in use (see
    /// the class comment).
    ///
    /// @param popupPosition Where to show it, e.g. below its button. Null centres it.
    public void show(final PopupPosition popupPosition) {
        final boolean takeFocus = InputModality.isKeyboard();
        limitWidthToRoomBeside(popupPosition);
        setVisible(false);
        setModal(false);
        PopupUtil.showPopup(this, false, popupPosition, null, PopupType.POPUP, takeFocus
                ? this::focus
                : null);
        setOpenerExpanded(true);
    }

    // Beside its button, the popup is no wider than the room on the roomier side, as its help wraps:
    // wider (long help at a large text size), it would be placed over the button and the field it
    // helps with (help-popup.css)
    private void limitWidthToRoomBeside(final PopupPosition popupPosition) {
        final boolean beside = popupPosition != null
                               && (PopupLocation.RIGHT.equals(popupPosition.getPopupLocation())
                                   || PopupLocation.LEFT.equals(popupPosition.getPopupLocation()));
        if (beside) {
            final Rect anchor = popupPosition.getRelativeRect();
            final double windowLeft = Window.getScrollLeft();
            final double windowRight = windowLeft + Window.getClientWidth();
            final double room = Math.max(windowRight - anchor.getRight(), anchor.getLeft() - windowLeft);
            CssProperties.set(getElement(), ROOM, Math.floor(room) + "px");
        } else {
            CssProperties.remove(getElement(), ROOM);
        }
    }

    // The popup itself takes the focus (not its first link), so that a screen reader reads it from
    // the start; the focus goes back to where it was when it closes (PopupUtil)
    private void focus() {
        getElement().setTabIndex(-1);
        getElement().focus();
    }

    private void setOpenerExpanded(final boolean expanded) {
        if (opener != null) {
            opener.setAttribute(ARIA_EXPANDED, String.valueOf(expanded));
        }
    }

    /// Not supported: the popup's content is its help, set with [#setContent(SafeHtml)].
    ///
    /// @throws UnsupportedOperationException Always.
    @Override
    public void setContent(final Widget widget) {
        throw new UnsupportedOperationException("A help popup's content is set as HTML");
    }

    /// Not supported: the popup's content is its help, set with [#setContent(SafeHtml)].
    ///
    /// @throws UnsupportedOperationException Always.
    @Override
    public void setWidget(final Widget widget) {
        throw new UnsupportedOperationException("A help popup's content is set as HTML");
    }

    /// Not supported: the popup closes itself (on Escape, a click outside it or a change of
    /// history); [#setOnClose(Runnable)] says when it has.
    ///
    /// @throws UnsupportedOperationException Always.
    @Override
    public void setOnAction(final Consumer<DialogAction> onAction) {
        throw new UnsupportedOperationException("A help popup handles its own actions");
    }

    @Override
    public void forceHide(final boolean autoClose) {
        super.hide(autoClose);
    }

    @Override
    public void setIcon(final SvgImage icon) {
        // No icon
    }

    @Override
    public void setCaption(final String caption) {
        // No caption
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, HelpPopup> {

    }
}
