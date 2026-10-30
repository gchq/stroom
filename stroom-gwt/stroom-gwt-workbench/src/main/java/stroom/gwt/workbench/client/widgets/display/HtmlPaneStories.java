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


package stroom.gwt.workbench.client.widgets.display;

import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.text.client.HtmlPane;

import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [HtmlPane] (an `info-page` scroll panel).
public final class HtmlPaneStories {

    // Arg names
    private static final String HTML_CONTENT = "htmlContent";

    private HtmlPaneStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Display/HtmlPane", HtmlPaneStories.class)
                .layout(StoryLayout.PADDED)
                .argType(ArgType.text(HTML_CONTENT).description("The HTML to show (GWT setHtml(...))."))
                .args(Args.of(HTML_CONTENT, ""))
                // A simple block of HTML
                .story("Default", HtmlPaneStories::fromArgs)
                .withArgs(Args.of(HTML_CONTENT,
                        "<h3>Info page</h3><p>This content is supplied as a raw HTML string.</p>"))
                // Richer markup with a list and a link
                .story("RichContent", HtmlPaneStories::fromArgs)
                .withArgs(Args.of(HTML_CONTENT,
                        "<h3>Details</h3><ul><li>First item</li><li>Second item</li></ul>"
                        + "<p><a href=\"#\">A link</a> and some <strong>bold</strong> text.</p>"));
    }

    /// A pane made entirely from the story's args, so the Controls addon changes it.
    private static Widget fromArgs(final StoryContext context) {
        final HtmlPane pane = new HtmlPane();
        pane.setHtml(context.getArgs().getString(HTML_CONTENT, ""));
        return pane;
    }
}
