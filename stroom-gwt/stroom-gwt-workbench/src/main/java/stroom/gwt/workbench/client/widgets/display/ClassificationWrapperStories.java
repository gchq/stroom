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

import stroom.data.client.component.ClassificationWrapper;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [ClassificationWrapper] (the component that docks a
/// [stroom.data.client.component.ClassificationLabel] banner below some content, e.g. the data viewer).
///
/// As for `ClassificationLabelStories`, the label colours are served in the UI config fixture of
/// a [ScreenHarness], and the view is created once the config has been fetched.
public final class ClassificationWrapperStories {

    private ClassificationWrapperStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // No args: both stories render fixed content
        registry.component("Widgets/Display/ClassificationWrapper", ClassificationWrapperStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Content with the classification banner docked at the bottom
                .story("Default", context -> wrapper(context, "SECRET",
                        "<div style=\"padding: 16px\">"
                        + "<h3 style=\"margin-top: 0\">Wrapped content</h3>"
                        + "<p>The classification banner sits below this content.</p>"
                        + "</div>"))
                // An unmatched classification falls back to the default grey banner
                .story("DefaultColour", context -> wrapper(context, "RESTRICTED",
                        "<div style=\"padding: 16px\">Some content.</div>"));
    }

    /// `<div style={{height: 300}}><ClassificationWrapper ...>{content}</ClassificationWrapper></div>`.
    private static Widget wrapper(final StoryContext context, final String classification, final String contentHtml) {
        final ScreenHarness harness = DisplayWidgets.classificationHarness(context, DisplayWidgets.COLOURS);
        final FlowPanel container = new FlowPanel();
        container.getElement().getStyle().setProperty("height", "300px");
        DisplayWidgets.whenUiConfigLoaded(harness, () -> {
            final ClassificationWrapper wrapper = new ClassificationWrapper();
            DisplayWidgets.withColours(harness, wrapper.getClassificationLabel());
            wrapper.setContent(new HTML(contentHtml));
            wrapper.setClassification(classification);
            container.add(wrapper);
        });
        harness.add(container);
        return harness.asWidget();
    }

}
