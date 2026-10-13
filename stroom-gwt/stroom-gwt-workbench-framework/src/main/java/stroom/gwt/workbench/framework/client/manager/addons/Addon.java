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

package stroom.gwt.workbench.framework.client.manager.addons;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

/// A tab of the addon panel, e.g. Controls or Actions. Each addon renders into its own element,
/// which the panel shows when its tab is selected.
public abstract class Addon {

    private Element element;
    private Runnable badgeChangeHandler = () -> {
    };

    /// @return The title of the tab.
    public abstract String getTitle();

    /// @return The count shown next to the title, or null for none.
    public String getBadge() {
        return null;
    }

    /// Called once by the panel to give the addon its element.
    ///
    /// @param element            The element to render into.
    /// @param badgeChangeHandler Called by [#badgeChanged()].
    public void attach(final Element element, final Runnable badgeChangeHandler) {
        this.element = element;
        this.badgeChangeHandler = badgeChangeHandler;
        onAttach();
    }

    /// Called once the addon has its element, e.g. to add event listeners.
    protected void onAttach() {
    }

    /// @return The element the addon renders into.
    protected Element getElement() {
        return element;
    }

    /// Tells the panel the badge has changed.
    protected void badgeChanged() {
        badgeChangeHandler.run();
    }

    /// Appends Storybook's empty state, as shown by an addon with nothing to show.
    ///
    /// @param builder     The builder.
    /// @param title       The bold title.
    /// @param description The text below it.
    /// @param docsUrl     The URL of the 'Read docs' link, or null for none.
    protected static void appendEmptyState(final SafeHtmlBuilder builder,
                                           final String title,
                                           final String description,
                                           final String docsUrl) {
        builder.appendHtmlConstant("<div class=\"wbm-empty\"><div class=\"wbm-empty__title\">")
                .appendEscaped(title)
                .appendHtmlConstant("</div><div class=\"wbm-empty__description\">")
                .appendEscaped(description)
                .appendHtmlConstant("</div>");
        if (docsUrl != null) {
            builder.appendHtmlConstant("<a class=\"wbm-empty__link\" target=\"_blank\" rel=\"noopener noreferrer\" "
                                       + "href=\"" + docsUrl + "\">"
                                       + "<svg class=\"wbm-icon\" aria-hidden=\"true\">"
                                       + "<use href=\"#wbm-icon-document\"></use></svg>Read docs"
                                       + "<svg class=\"wbm-icon\" aria-hidden=\"true\">"
                                       + "<use href=\"#wbm-icon-chevron-small-right\"></use></svg></a>");
        }
        builder.appendHtmlConstant("</div>");
    }
}
