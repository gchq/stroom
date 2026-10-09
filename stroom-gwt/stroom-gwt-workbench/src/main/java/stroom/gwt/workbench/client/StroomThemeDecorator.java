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

package stroom.gwt.workbench.client;

import stroom.gwt.workbench.framework.client.story.StoryDecorator;
import stroom.gwt.workbench.framework.client.story.StoryTheme;
import stroom.ui.config.shared.Theme;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Shows each story on the Stroom theme's background, as a Storybook decorator would. The theme is
/// selected by the classes on the `<html>` element of iframe.html, which [#applyTheme(StoryTheme)]
/// sets to the workbench's chosen theme.
///
/// Some of Stroom's code doesn't follow the page's classes but the user's preferences (an Ace
/// editor's theme, a visualisation's or Markdown preview's frame), so stories give Stroom the same
/// theme with [#getStroomThemeName()], as `ScreenHarness` does for the user's preferences.
public class StroomThemeDecorator implements StoryDecorator {

    // The theme applied to the page, one story being shown per page
    private static StoryTheme theme = StoryTheme.DEFAULT;

    /// @return The name of the Stroom theme the stories are shown in, e.g. `Dark`, for Stroom's
    /// user preferences (`UserPreferences.theme`, `CurrentPreferences.setTheme`).
    public static String getStroomThemeName() {
        return toStroomTheme(theme).getThemeName();
    }

    /// Sets the page's Stroom theme class (e.g. `stroom-theme-light`) for the workbench's theme.
    ///
    /// @param theme The theme chosen in the workbench's toolbar.
    @Override
    public void applyTheme(final StoryTheme theme) {
        StroomThemeDecorator.theme = theme;
        final Element html = Document.get().getDocumentElement();
        for (final Theme stroomTheme : Theme.values()) {
            html.removeClassName(stroomTheme.getCssClass());
        }
        html.addClassName(toStroomTheme(theme).getCssClass());
    }

    private static Theme toStroomTheme(final StoryTheme theme) {
        return theme == StoryTheme.LIGHT
                ? Theme.LIGHT
                : Theme.DARK;
    }

    @Override
    public Widget decorate(final Widget story) {
        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("padding", "24px");
        style.setProperty("minHeight", "100vh");
        style.setProperty("background", "var(--app__background-color, #020202)");
        style.setProperty("color", "var(--text-color, #dce4e5)");
        frame.add(story);
        return frame;
    }
}
