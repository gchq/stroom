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

package stroom.widget.help.client;

import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.tooltip.client.event.ShowHelpEvent;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class HelpButton extends InlineSvgButton {

    private String helpContentHeading;
    private SafeHtml helpContent;

    private HelpButton(final String title,
                       final SafeHtml helpContent) {
        super();
        this.helpContent = helpContent;
        setSvg(SvgImage.HELP_OUTLINE);
        setTitle(NullSafe.nonBlankStringElse(title, "Help"));
        setEnabled(true);
        addStyleName("help-button info");
        addClickHandler(event -> showHelpPopup());
        addKeyDownHandler(event -> {
            if (Action.SELECT == KeyBinding.test(event.getNativeEvent())) {
                showHelpPopup();
            }
        });
    }

    /**
     * Stop the help button being part of the tab index. Useful on forms where
     * you have a help button for each form group. The help is accessible
     */
    public void preventTabFocus() {
        // com.google.gwt.user.client.ui.FocusWidget.onAttach replaces a tabIndex of -1 with 0
        // so use -2 to stop the help button having a tab index.
        getElement().setTabIndex(-2);
    }

    public static HelpButton create() {
        return new HelpButton(null, null);
    }

    public static HelpButton create(final String title) {
        return new HelpButton(title, null);
    }

    public static HelpButton create(final String title,
                                    final SafeHtml helpContent) {
        return new HelpButton(title, helpContent);
    }

    public void setHelpContentHeading(final String helpContentHeading) {
        this.helpContentHeading = helpContentHeading;
    }

    public void setHelpContent(final SafeHtml helpContent) {
        this.helpContent = helpContent;
        updateVisibleState();
//        setHelpContent(getTitle(), helpContent);
    }

    /**
     * Set the HTML content of the help popup.
     *
     * @param helpContentHeading The heading to show at the top of the popup
     * @param helpContent        The help content inside the popup
     */
    public void setHelpContent(final String helpContentHeading,
                               final SafeHtml helpContent) {
        this.helpContentHeading = helpContentHeading;
        this.helpContent = helpContent;
        updateVisibleState();
    }

    public boolean hasHelpContent() {
        return helpContent != null
               && !SafeHtmlUtils.EMPTY_SAFE_HTML.equals(helpContent)
               && !NullSafe.isBlankString(helpContent.asString());
    }

    /**
     * Show the help popup. Useful if you want to trigger the help popup from a keydown
     * on some other widget, e.g. a form group.
     */
    public void showHelpPopup() {
        showHelpPopup(this.getElement());
    }

    private void showHelpPopup(final Element element) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        if (!NullSafe.isBlankString(helpContentHeading)) {
            builder.appendHtmlConstant("<h4>")
                    .appendEscaped(helpContentHeading)
                    .appendHtmlConstant("</h4>");
        }
        final SafeHtml popupText = builder
                .append(helpContent)
                .toSafeHtml();

        ShowHelpEvent.builder(element)
                .withContent(popupText)
                .fire();
    }

    private void updateVisibleState() {
        setVisible(helpContent != null
                   && !SafeHtmlUtils.EMPTY_SAFE_HTML.equals(helpContent)
                   && !NullSafe.isBlankString(helpContent.asString()));
    }
}
