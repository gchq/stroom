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

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Shows each story on the Stroom theme's background, the equivalent of the `ThemeFrame`
/// decorator in the React Storybook's `.storybook/preview.tsx`. The theme itself is selected by
/// the classes on the `<html>` element of iframe.html.
public class StroomThemeDecorator implements StoryDecorator {

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
