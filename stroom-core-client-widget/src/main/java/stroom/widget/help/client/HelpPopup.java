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
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.AbstractPopupPanel;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.popup.client.view.DialogActionUiHandlers;
import stroom.widget.popup.client.view.Popup;
import stroom.widget.popup.client.view.PopupUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Widget;

/// A popup of help, shown beside the button that opens it: a form group's help and a quick
/// filter's syntax help. It is a dialog that isn't modal, named for what it helps with; it doesn't
/// take the focus, and closes on Escape, a click outside it, or a second press of its button
/// (whose `aria-expanded` says whether it is open).
///
/// The BEM block `help-popup` (`help-popup.css`), its content `help-popup__content`.
public final class HelpPopup extends AbstractPopupPanel implements Popup {

    private static final String BLOCK = "help-popup";
    private static final String ARIA_EXPANDED = "aria-expanded";

    private Element opener;

    private HelpPopup(final SafeHtml content,
                      final String name,
                      final boolean markdown,
                      final CloseActionHandler closeActionHandler) {
        // Auto-hides: closes itself when the user clicks outside it
        super(closeActionHandler, true, false);
        // Escape (the close action) closes it
        closeActionHandler.popup = this;

        final HTMLPanel contentPanel = new HTMLPanel(content);
        contentPanel.setStyleName(BLOCK + "__content");
        setWidget(contentPanel);
        setStyleName(BLOCK);
        if (markdown) {
            // Styled as rendered markdown is elsewhere (the shared `markdown` block)
            addStyleName(BLOCK + "--markdown");
            contentPanel.addStyleName("markdown");
        }
        // A dialog that isn't modal, for screen readers
        getElement().setAttribute("role", "dialog");
        getElement().setAttribute("aria-label", name);
        addCloseHandler(event -> setOpenerExpanded(false));
    }

    /// Creates a help popup showing rendered markdown (a form group's help).
    ///
    /// @param content The rendered markdown.
    /// @param name    The popup's accessible name, e.g. `Help`.
    /// @return The popup, not yet shown.
    public static HelpPopup markdown(final SafeHtml content, final String name) {
        return new HelpPopup(content, name, true, new CloseActionHandler());
    }

    /// Creates a help popup showing HTML as it is (e.g. a quick filter's syntax help, with its own
    /// tables).
    ///
    /// @param content The HTML.
    /// @param name    The popup's accessible name, e.g. `Quick Filter Syntax Help`.
    /// @return The popup, not yet shown.
    public static HelpPopup html(final SafeHtml content, final String name) {
        return new HelpPopup(content, name, false, new CloseActionHandler());
    }

    /// Marks a button as opening a help popup (`aria-haspopup="dialog"`), with the popup closed
    /// (`aria-expanded="false"`). The popup keeps `aria-expanded` up to date once it is given the
    /// button with [#setOpener].
    ///
    /// @param button The button's element.
    public static void markOpener(final Element button) {
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

    /// Shows the popup, placed by `popupPosition`, without moving the focus.
    ///
    /// @param popupPosition Where to show it, e.g. below its button. Null centres it.
    public void show(final PopupPosition popupPosition) {
        setVisible(false);
        setModal(false);
        PopupUtil.showPopup(this, false, popupPosition, null, PopupType.POPUP, null);
        setOpenerExpanded(true);
    }

    private void setOpenerExpanded(final boolean expanded) {
        if (opener != null) {
            opener.setAttribute(ARIA_EXPANDED, String.valueOf(expanded));
        }
    }

    @Override
    public void setContent(final Widget widget) {
        setWidget(widget);
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


    private static class CloseActionHandler implements DialogActionUiHandlers {

        private HelpPopup popup;

        @Override
        public void onDialogAction(final DialogAction action) {
            if (action == DialogAction.CLOSE && popup != null) {
                popup.hide();
            }
        }
    }
}
