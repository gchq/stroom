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
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.form.client.component.FileChooser;
import stroom.widget.text.client.InlineLabel;
import stroom.widget.text.client.Label;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for [FileChooser]: a "Choose File" button that clicks a hidden file input, and a
/// label showing the chosen file's name.
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
                    style.setProperty("color", "var(--text-color--disabled)");
                    style.setProperty("fontSize", "var(--font-size-13)");
                    return padded(hint, new FileChooser());
                })
                .withPlay(play -> {
                    // A button described by the chosen file, which says none is chosen yet
                    final Query button = play.getByRole("button", "Choose File");
                    play.expect(button).toHaveAccessibleDescription("No file chosen");
                    play.expect(button).toHaveAttribute("aria-haspopup", "dialog");
                    // BEM (file-chooser.css)
                    play.expect(button).toHaveClass("file-chooser__button");
                    play.expect(play.getByText("No file chosen")).toHaveClass("file-chooser__name--empty");
                    play.expect(play.querySelector(StroomDom.FILE_INPUT)).toHaveClass("file-chooser__input");
                    play.expect(play.querySelector(StroomDom.FILE_INPUT)).not().toBeVisible();
                    // It is Stroom's markup throughout, no GWT widget's
                    play.expect(play.querySelectorAll(".file-chooser [class*='gwt-']")).toHaveLength(0);
                })
                // A callback reporting the chosen file name
                .story("WithCallback", context -> {
                    // FileChooser has no accept filter or button label, so the button says
                    // "Choose File" and any file can be chosen.
                    final FileChooser fileChooser = new FileChooser();
                    final InlineLabel selected = StoryPanels.note("Selected: —", "#dce4e5", "12px");
                    final Spy onFile = context.fn(ON_FILE);
                    fileChooser.setOnChange(() -> {
                        final String name = fileChooser.getFilename();
                        selected.setText("Selected: " + name);
                        onFile.call(name);
                    });
                    return padded(fileChooser, selected);
                })
                .withPlay(play -> {
                    // Choosing a file names it (without the browser's fake path), and tells the owner
                    play.upload(play.querySelector(StroomDom.FILE_INPUT), "stream.txt", "data", "text/plain");
                    play.waitFor(() -> play.expect(play.spy(ON_FILE)).toHaveBeenCalledWith("stream.txt"));
                    final Query button = play.getByRole("button", "Choose File");
                    play.expect(button).toHaveAccessibleDescription("stream.txt");
                    play.expect(play.querySelector(".file-chooser__name"))
                            .not().toHaveClass("file-chooser__name--empty");
                });
    }

    /// Equivalent of `<div style={{padding: 16, display: 'flex', flexDirection: 'column', gap: 16}}>`.
    private static FlowPanel padded(final Widget... widgets) {
        final FlowPanel panel = StoryPanels.column(16, widgets);
        panel.getElement().getStyle().setProperty("padding", "16px");
        return panel;
    }
}
