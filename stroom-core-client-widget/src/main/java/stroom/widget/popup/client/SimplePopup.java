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

package stroom.widget.popup.client;

import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;

/// A popup with no title bar or buttons (`PopupType.POPUP`): a menu, a chooser, a filter's list,
/// a tooltip. The BEM block `popup` (`popup.css`), its look on the popup itself (it was three
/// elements inside it), with its content inside GWT's own `popupContent`.
///
/// A popup given a caption is a dialog that isn't modal, named by the caption (which isn't
/// shown); one with none (a menu, whose content has its own role) has no role.
public class SimplePopup extends AbstractPopupPanel {

    private static final String ROLE = "role";
    private static final String ARIA_LABEL = "aria-label";

    /// Creates a popup that closes itself when the user clicks outside it or the browser's history
    /// changes (e.g. Back). It should not be shown until its content has been set with
    /// [#setContent(Widget)]; [#setOnAction] tells its owner when it is asked to close.
    public SimplePopup() {
        setAutoHideEnabled(true);
        setAutoHideOnHistoryEventsEnabled(true);
        // Floating: hidden until PopupUtil has placed it
        setStyleName("popup popup--floating");
    }

    @Override
    public void setIcon(final SvgImage icon) {
        // No icon
    }

    /// Names the popup, making it a dialog for screen readers. The caption isn't shown.
    ///
    /// @param text The popup's name, or null or blank for none (and no role).
    @Override
    public void setCaption(final String text) {
        final Element element = getElement();
        if (NullSafe.isBlankString(text)) {
            element.removeAttribute(ROLE);
            element.removeAttribute(ARIA_LABEL);
        } else {
            element.setAttribute(ROLE, "dialog");
            element.setAttribute(ARIA_LABEL, text);
        }
    }

    @Override
    public void setContent(final Widget widget) {
        setWidget(widget);
    }

    @Override
    public void forceHide(final boolean autoClosed) {
        super.hide(autoClosed);
    }

    /// This is overridden as we don't want popups to hide without the controller to get a chance to
    /// do something about it. When hide occurs the controller will be notified via the event and will
    /// then choose whether or not to force the popup to hide.
    @Override
    public void hide(final boolean autoClosed) {
        fireAction(autoClosed
                ? DialogAction.AUTO_CLOSE
                : DialogAction.CLOSE);
    }
}
