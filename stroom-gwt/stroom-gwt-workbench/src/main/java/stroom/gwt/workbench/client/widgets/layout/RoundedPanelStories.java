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


package stroom.gwt.workbench.client.widgets.layout;

import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;

/// Stories matching `Widgets/Layout/RoundedPanel` in the React Storybook.
///
/// Stroom has no rounded panel widget: the React `RoundedPanel` ports the centred, rounded card
/// that `LoginViewImpl.ui.xml` builds (`LoginView` > `LoginViewFormPanel` >
/// `max form-padding form LoginViewForm`), so these stories build the same panels, styled by
/// Stroom's `Login.css`.
public final class RoundedPanelStories {

    private RoundedPanelStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Layout/RoundedPanel", RoundedPanelStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The centred, rounded login card with some sample content
                .story("Default", context -> {
                    // Differs from React: LoginViewImpl's outer panel is a div, not a <main>
                    // landmark.
                    final FlowPanel view = new FlowPanel();
                    view.setStyleName("LoginView");
                    final FlowPanel formPanel = new FlowPanel();
                    formPanel.setStyleName("LoginViewFormPanel");
                    final FlowPanel form = new FlowPanel();
                    form.setStyleName("max form-padding form LoginViewForm");
                    form.add(new HTML("<h2 style=\"margin: 0 0 12px\">Sign in</h2>"
                                      + "<p style=\"margin: 0\">Placeholder content inside the rounded panel.</p>"));
                    formPanel.add(form);
                    view.add(formPanel);
                    return view;
                });
    }
}
