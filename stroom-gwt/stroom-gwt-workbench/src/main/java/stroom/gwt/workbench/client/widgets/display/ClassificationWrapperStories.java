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

import stroom.data.client.view.ClassificationLabel;
import stroom.data.client.view.ClassificationWrapperViewImpl;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

/// Stories for Stroom's [ClassificationWrapperViewImpl] (the view that docks a
/// [ClassificationLabel] banner below some content, e.g. the data viewer), matching
/// `Widgets/Display/ClassificationWrapper` in the React Storybook.
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
        // React's args (classification, labelColoursCsv, children) are only defaults for its Controls;
        // both stories render fixed content, so no args are declared
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
            final ClassificationWrapperViewImpl view = new ClassificationWrapperViewImpl(
                    GWT.create(ClassificationWrapperViewImpl.Binder.class),
                    new ClassificationLabel(harness.getUiConfigCache()));
            view.setContent(new ContentView(new HTML(contentHtml)));
            view.setClassification(classification);
            container.add(view.asWidget());
        });
        harness.add(container);
        return harness.asWidget();
    }

    // --------------------------------------------------------------------------------


    /// The wrapped content, as a view (Stroom wraps another presenter's view).
    private static final class ContentView extends ViewImpl {

        private final Widget widget;

        private ContentView(final Widget widget) {
            this.widget = widget;
        }

        @Override
        public Widget asWidget() {
            return widget;
        }
    }
}
