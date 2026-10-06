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

import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for the Stroom wordmark logo in the navigation header, matching
/// `Widgets/Display/Logo` in the React Storybook.
///
/// There is no logo widget in GWT: the stories build it as Stroom's `NavigationViewImpl` does, a
/// `navigation-logo` button holding the [SvgImage#LOGO] image, whose click (with the primary
/// button) opens the main menu in Stroom.
public final class LogoStories {

    private static final String ON_CLICK = "onClick";

    private LogoStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Display/Logo", LogoStories.class)
                .layout(StoryLayout.CENTERED)
                // The wordmark, in the surrounding text's colour (the image uses currentColor)
                .story("Default", context -> wrap(logo()))
                // With a click handler, reported in the Actions addon
                .story("Clickable", LogoStories::clickable);
    }

    private static Widget clickable(final StoryContext context) {
        final Button logo = logo();
        final Spy onClick = context.fn(ON_CLICK);
        // As NavigationViewImpl.onLogoClick handles it
        logo.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event.getNativeEvent())) {
                onClick.call("logo clicked");
            }
        });
        return wrap(logo);
    }

    /// The logo, as `NavigationViewImpl`'s UiBinder template and constructor build it.
    private static Button logo() {
        final Button logo = new Button();
        logo.setStyleName("navigation-logo");
        logo.getElement().setInnerSafeHtml(SvgImageUtil.toSafeHtml(SvgImage.LOGO, "navigation-logo-image"));
        return logo;
    }

    /// `<div style={{width: 160, color: 'var(--text-color,#eee)'}}>`.
    private static Widget wrap(final Widget logo) {
        final FlowPanel panel = new FlowPanel();
        panel.getElement().getStyle().setProperty("width", "160px");
        panel.getElement().getStyle().setProperty("color", "var(--text-color,#eee)");
        panel.add(logo);
        return panel;
    }
}
