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

package stroom.gwt.workbench.client.widgets.inputs;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.form.client.CustomFileUpload;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// Stories for [CustomFileUpload], matching `Widgets/Inputs/FileChooser` in the React Storybook.
///
/// The React `FileChooser` is a port of [CustomFileUpload]: a "Choose File" button that clicks
/// a hidden file input, and a label showing the chosen file's name.
public final class FileChooserStories {

    private static final String ON_FILE = "onFile";

    private FileChooserStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/FileChooser", FileChooserStories.class)
                .layout(StoryLayout.CENTERED)
                // Basic file upload widget - click the button to select a file
                .story("Basic", context -> {
                    final Label hint = new Label("Click the button to select a file");
                    final Style style = hint.getElement().getStyle();
                    style.setProperty("color", "var(--text__color--muted)");
                    style.setProperty("fontSize", "var(--font-size)");
                    return padded(hint, fileUpload());
                })
                // With an accept filter and a callback reporting the chosen file name
                .story("WithCallback", context -> {
                    // Differs from React: CustomFileUpload has no accept filter or button label
                    // (React's accept=".json,.xml" and label="Choose config"), so the button says
                    // "Choose File" and any file can be chosen.
                    final CustomFileUpload fileUpload = fileUpload();
                    final InlineLabel selected = StoryPanels.note("Selected: —", "#dce4e5", "12px");
                    // GWT has no onFile callback: Stroom's dialogs read getFilename() when they
                    // submit. The hidden input's change event bubbles to the widget, so it is
                    // read here when it changes.
                    final Spy onFile = context.fn(ON_FILE);
                    fileUpload.addDomHandler(event -> {
                        final String name = baseName(fileUpload.getFilename());
                        selected.setText("Selected: " + name);
                        onFile.call(name);
                    }, ChangeEvent.getType());
                    return padded(fileUpload, selected);
                });
    }

    private static CustomFileUpload fileUpload() {
        // Its constructor is private, as Stroom only creates it with UiBinder (GWT.create)
        return GWT.create(CustomFileUpload.class);
    }

    /// Equivalent of `<div style={{padding: 16, display: 'flex', flexDirection: 'column', gap: 16}}>`.
    private static FlowPanel padded(final Widget... widgets) {
        final FlowPanel panel = StoryPanels.column(16, widgets);
        panel.getElement().getStyle().setProperty("padding", "16px");
        return panel;
    }

    /// The file name without any path, as [CustomFileUpload] shows it.
    private static String baseName(final String fileName) {
        if (fileName == null) {
            return "";
        }
        final int index = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return fileName.substring(index + 1);
    }
}
