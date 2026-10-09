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

package stroom.gwt.workbench.client.widgets.dialogs;

import stroom.gwt.workbench.client.widgets.StoryArgs;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.widget.popup.client.view.Dialog;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for the title bar of Stroom's [Dialog] (its icon, caption and busy spinner).
///
/// The title bar is part of Stroom's `Dialog` (`Dialog.ui.xml`), not a widget
/// of its own, so each story shows a whole (empty, non-modal) `Dialog` at the top left of the
/// canvas.
public final class DialogTitleStories {

    private static final int LEFT = 16;
    private static final int TOP = 16;

    // Arg names
    private static final String TITLE = "title";
    private static final String ICON_HTML = "iconHtml";
    private static final String BUSY = "busy";

    private DialogTitleStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Dialogs/DialogTitle", DialogTitleStories.class)
                .layout(StoryLayout.PADDED)
                .argType(ArgType.text(TITLE).description("The caption (GWT Dialog.setCaption)."))
                .argType(ArgType.select(ICON_HTML, "INFO", "QUESTION", "WARNING", "ERROR", "PASSWORD")
                        .description("The title bar's icon (GWT Dialog.setIcon).")
                        .typeName("SvgImage"))
                .argType(ArgType.bool(BUSY)
                        .description("Shows the title bar's spinner (a task running in the dialog's "
                                     + "TaskMonitorFactory).")
                        .defaultSummary("false"))
                .args(Args.of(TITLE, "Information"))
                // Plain title bar — no icon, not busy
                .story("Default", DialogTitleStories::fromArgs)
                .withArgs(Args.of(TITLE, "Edit Settings"))
                // With a leading icon in the title bar
                .story("WithIcon", DialogTitleStories::fromArgs)
                .withArgs(Args.of(TITLE, "Information", ICON_HTML, "INFO"))
                // Busy — the title-bar spinner (GWT SpinnerLarge) fades in
                .story("Busy", DialogTitleStories::fromArgs)
                .withArgs(Args.of(TITLE, "Loading data", ICON_HTML, "QUESTION", BUSY, true));
    }

    private static Widget fromArgs(final StoryContext context) {
        final Args args = context.getArgs();
        final Dialog dialog = new Dialog(action -> {
            // No buttons, so no actions to handle
        });
        // Not modal, so the workbench stays usable
        dialog.setModal(false);
        dialog.setCaption(args.getString(TITLE, ""));
        final SvgImage icon = StoryArgs.toSvgImage(args.getString(ICON_HTML));
        if (icon != null) {
            dialog.setIcon(icon);
        }
        dialog.setContent(new FlowPanel());
        if (args.getBoolean(BUSY)) {
            final TaskMonitor taskMonitor = dialog.createTaskMonitor();
            final Task task = new SimpleTask("Loading data");
            taskMonitor.onStart(task);
            context.addCleanUp(() -> taskMonitor.onEnd(task));
        }
        dialog.setPopupPosition(LEFT, TOP);
        dialog.show();
        context.addCleanUp(() -> dialog.forceHide(false));
        return new FlowPanel();
    }
}
