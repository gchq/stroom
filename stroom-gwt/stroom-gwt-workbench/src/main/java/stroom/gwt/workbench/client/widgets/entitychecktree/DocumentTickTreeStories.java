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


package stroom.gwt.workbench.client.widgets.entitychecktree;

import stroom.explorer.client.component.DocumentTickTreeController;
import stroom.explorer.client.component.ExplorerTree;
import stroom.explorer.client.component.ExplorerTreeController;
import stroom.explorer.shared.ExplorerNode;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.widgets.tree.ExplorerFixture;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Stories for Stroom's [DocumentTickTreeController]: an explorer tree with a tri-state tick box on
/// each node (e.g. for choosing the documents to export). The tree is fetched from an [ExplorerFixture].
public final class DocumentTickTreeStories {

    private static final String ON_CHECKED_CHANGE = "onCheckedChange";

    private DocumentTickTreeStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/EntityCheckTree", DocumentTickTreeStories.class)
                .layout(StoryLayout.CENTERED)
                // Tri-state: ticking a folder ticks its descendants and half-ticks its ancestor
                .story("TriState", DocumentTickTreeStories::harness)
                .withPlay(DocumentTickTreeStories::playTriState);
    }

    private static void playTriState(final Play play) {
        play.findByText("System");
        // Stroom opens the root on the first fetch (minDepth 1), so 'My Folder' is shown without
        // opening System
        play.findByText("My Folder");
        play.click(expander(play, "My Folder"));
        play.waitFor(() -> play.expect(play.getByText("Alpha")).toBeInTheDocument());

        // Tick the folder: it and both children are ticked; System is only half ticked, so isn't
        // in the ticked set
        play.click(play.getByText("My Folder"));
        play.waitFor(() -> play.expect(play.getByTestId("checked")).toHaveTextContent(TextMatch.regex("^a,b,f1$", "")));

        // Unticking a child half ticks the folder, which drops out of the ticked set
        play.click(play.getByText("Alpha"));
        play.waitFor(() -> play.expect(play.getByTestId("checked")).toHaveTextContent(TextMatch.regex("^b$", "")));
    }

    private static Query expander(final Play play, final String name) {
        return play.within(play.getByText(name).closest(".explorer-cell")).querySelector(".explorer-cell__expander");
    }

    /// The tree over the sorted uuids of the ticked nodes.
    private static Widget harness(final StoryContext context) {
        final ExplorerFixture fixture = new ExplorerFixture(
                ExplorerFixture.folder("System", "System",
                        ExplorerFixture.folder("My Folder",
                                ExplorerFixture.doc("Alpha", "Dictionary").withUuid("a"),
                                ExplorerFixture.doc("Beta", "Dictionary").withUuid("b"))
                                .withUuid("f1"))
                        .withUuid("sys"));
        final ScreenHarness harness = ScreenHarness.create(context, TreeFixtures.explorerRoutes(fixture).build());

        // In a panel, as the export dialog's template declares it in one
        final ExplorerTree explorerTree = new ExplorerTree();
        final SimplePanel view = new SimplePanel(explorerTree);
        final DocumentTickTreeController controller = new DocumentTickTreeController(
                () -> new ExplorerTreeController(harness.getRestFactory()));
        harness.own(controller);
        controller.attach(explorerTree);
        controller.refresh();

        final Label checked = new Label();
        checked.getElement().setAttribute("data-testid", "checked");
        final Style checkedStyle = checked.getElement().getStyle();
        checkedStyle.setProperty("padding", "4px");
        checkedStyle.setProperty("borderTop", "1px solid #555");
        checkedStyle.setProperty("fontSize", "12px");

        // Story-only adapter: the tree has no change event for its ticks (Stroom reads
        // getSelectedSet() when the dialog is closed), so read them after each click or key press
        final Spy onCheckedChange = context.fn(ON_CHECKED_CHANGE);
        final Runnable update = () -> Scheduler.get().scheduleDeferred(() -> {
            final List<String> uuids = new ArrayList<>();
            for (final ExplorerNode node : controller.getSelectedSet()) {
                uuids.add(node.getUuid());
            }
            Collections.sort(uuids);
            final String text = String.join(",", uuids);
            if (!text.equals(checked.getText())) {
                checked.setText(text);
                onCheckedChange.call(text);
            }
        });
        harness.addRegistration(view.asWidget().addDomHandler(event -> update.run(), MouseUpEvent.getType()));
        harness.addRegistration(view.asWidget().addDomHandler(event -> update.run(), KeyUpEvent.getType()));

        final FlowPanel tree = new FlowPanel();
        tree.getElement().getStyle().setProperty("flex", "1");
        tree.getElement().getStyle().setProperty("minHeight", "0");
        tree.add(view.asWidget());

        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("width", "360px");
        style.setProperty("height", "300px");
        style.setProperty("border", "1px solid var(--dialog__border-color, #555)");
        style.setProperty("display", "flex");
        style.setProperty("flexDirection", "column");
        frame.add(tree);
        frame.add(checked);
        harness.add(frame);
        return harness.asWidget();
    }
}
