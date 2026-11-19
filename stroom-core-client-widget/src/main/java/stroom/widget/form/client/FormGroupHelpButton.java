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

package stroom.widget.form.client;

import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.help.client.HelpPopup;
import stroom.widget.help.client.ShowHelpEvent;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.ButtonBase;

/// A [FormGroup]'s help button, after its label (or its tick box's label): a
/// `<button class="form-group__help">` holding a help icon, that shows the group's help in a popup.
///
/// It isn't in the tab order, as F1 on the group's field shows the same help, and pressing it leaves
/// the focus where it was (e.g. in that field). It is shown only while there is help to show.
class FormGroupHelpButton extends ButtonBase {

    private String helpContentHeading;
    private SafeHtml helpContent;

    FormGroupHelpButton() {
        super(Document.get().createPushButtonElement());
        setStyleName("form-group__help");
        setIcon();
        // FocusWidget.onAttach replaces a tab index of -1 with 0, so -2 keeps it out of the tab order
        getElement().setTabIndex(-2);
        HelpPopup.markOpener(getElement());
        sinkEvents(Event.ONMOUSEDOWN);
        addClickHandler(event -> showHelpPopup());
        setVisible(false);
    }

    private void setIcon() {
        final Element holder = Document.get().createDivElement();
        holder.setInnerHTML(SvgImage.HELP_OUTLINE.getSvg());
        final Element svg = holder.getFirstChildElement();
        // An SVG element's className isn't a string, so set the class attribute itself
        final String classes = svg.getAttribute("class");
        svg.setAttribute("class", (NullSafe.isBlankString(classes)
                ? ""
                : classes + " ") + "form-group__help-icon");
        // The icon is decoration: the button is named by its title
        svg.setAttribute("aria-hidden", "true");
        getElement().appendChild(svg);
    }

    /// @param helpContentHeading The heading at the top of the help popup, or null for none.
    void setHelpContentHeading(final String helpContentHeading) {
        this.helpContentHeading = helpContentHeading;
    }

    /// Sets the help, showing the button if there is any and hiding it if not.
    ///
    /// @param helpContent The help, or null for none.
    void setHelpContent(final SafeHtml helpContent) {
        this.helpContent = helpContent;
        setVisible(hasHelpContent());
    }

    /// @return Whether there is help to show.
    boolean hasHelpContent() {
        return helpContent != null
               && !SafeHtmlUtils.EMPTY_SAFE_HTML.equals(helpContent)
               && !NullSafe.isBlankString(helpContent.asString());
    }

    /// Shows the help in a popup beside the button, e.g. when F1 is pressed in the group's field.
    void showHelpPopup() {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        if (!NullSafe.isBlankString(helpContentHeading)) {
            builder.appendHtmlConstant("<h4>")
                    .appendEscaped(helpContentHeading)
                    .appendHtmlConstant("</h4>");
        }
        builder.append(helpContent);
        ShowHelpEvent.builder(getElement())
                .withContent(builder.toSafeHtml())
                .fire();
    }

    @Override
    public void onBrowserEvent(final Event event) {
        if (DOM.eventGetType(event) == Event.ONMOUSEDOWN && MouseUtil.isPrimary(event)) {
            // Pressing it leaves the focus where it was (and doesn't start a drag)
            event.preventDefault();
        }
        super.onBrowserEvent(event);
    }
}
