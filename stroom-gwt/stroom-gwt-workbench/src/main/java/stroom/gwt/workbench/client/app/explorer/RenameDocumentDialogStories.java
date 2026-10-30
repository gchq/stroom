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

package stroom.gwt.workbench.client.app.explorer;

import stroom.docref.DocRef;
import stroom.document.client.DocumentDialogScreens;
import stroom.document.client.component.NameEntry;
import stroom.document.client.component.RenameDocumentController;
import stroom.document.client.event.RenameDocumentEvent;
import stroom.document.client.event.ShowRenameDocumentDialogEvent;
import stroom.explorer.shared.ExplorerNode;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories of `App/Main/RenameDocumentDialog`, showing Stroom's real [RenameDocumentController] (a
/// document's Rename dialog) as [DocumentDialogScreens] opens it for `ShowRenameDocumentDialogEvent`:
/// one dialog for each document chosen, one after another.
///
/// The explorer renames the document when told by `RenameDocumentEvent`; the stories' handler
/// records the new name and closes the dialog, as the explorer does once the rename is saved.
public final class RenameDocumentDialogStories {

    /// The name of the spy recording each document's new name.
    static final String RENAMED = "renamed";

    private static final ExplorerNode COUNTRIES = node("dict-1", "Countries");
    private static final ExplorerNode CITIES = node("dict-2", "Cities");

    private RenameDocumentDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/RenameDocumentDialog", RenameDocumentDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The dialog starts with the document's name; Enter renames it
                .story("Rename", context -> render(context, List.of(COUNTRIES)))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query name = screen.findByLabelText("Name");
                    play.expect(screen.getByText("Rename Countries", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.expect(name).toHaveValue("Countries");
                    play.waitFor(() -> play.expect(name).toHaveFocus());
                    play.clear(name);
                    play.type(name, "Nations");
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(play.spy(RENAMED)).toHaveBeenCalledWith("dict-1", "Nations"));
                    play.waitFor(() -> play.expect(screen.queryByText("Rename Countries", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    expectNoProblems(play);
                })
                // OK with no name says so under the field and keeps the dialog open
                .story("NoName", context -> render(context, List.of(COUNTRIES)))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query name = screen.findByLabelText("Name");
                    play.clear(name);
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.getByText("You must provide a new name for Countries"))
                            .toBeVisible());
                    play.expect(name).toHaveFocus();
                    play.expect(play.spy(RENAMED)).not().toHaveBeenCalled();
                    // Typing a name takes the message away
                    play.type(name, "N");
                    play.waitFor(() -> play.expect(screen.queryByText("You must provide a new name for Countries"))
                            .toBeNull());
                    expectNoProblems(play);
                })
                // Two documents: each has a dialog of its own, the second once the first is closed,
                // whether it was renamed or not
                .story("RenameTwo", context -> render(context, List.of(COUNTRIES, CITIES)))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Rename Countries", StroomDom.DIALOG_TITLE);
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    screen.findByText("Rename Cities", StroomDom.DIALOG_TITLE);
                    play.expect(screen.queryByText("Rename Countries", StroomDom.DIALOG_TITLE)).toBeNull();
                    final Query name = screen.getByLabelText("Name");
                    play.expect(name).toHaveValue("Cities");
                    play.clear(name);
                    play.type(name, "Towns");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(RENAMED)).toHaveBeenCalledWith("dict-2", "Towns"));
                    play.waitFor(() -> play.expect(screen.queryByText("Rename Cities", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(play.spy(RENAMED)).toHaveBeenCalledTimes(1);
                    expectNoProblems(play);
                });
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context, final List<ExplorerNode> documents) {
        final ScreenHarness harness = ScreenHarness.create(context, RestFixtures.none());
        final Spy renamed = harness.fn(RENAMED);
        // As the explorer does once the rename is saved
        harness.addRegistration(harness.getEventBus().addHandler(RenameDocumentEvent.getType(), event -> {
            renamed.call(event.getExplorerNode().getUuid(), event.getDocName());
            event.getHidePopupRequestEvent().hide();
        }));
        // Only the Rename dialog is asked for
        new DocumentDialogScreens(harness.getEventBus(),
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> new RenameDocumentController(harness.getEventBus(), new NameEntry()),
                harness.getAppRoot());
        ShowRenameDocumentDialogEvent.fire(harness.getHasHandlers(), documents);
        return harness.asWidget();
    }

    private static ExplorerNode node(final String uuid, final String name) {
        return ExplorerNode.builder()
                .docRef(new DocRef("Dictionary", uuid, name))
                .build();
    }
}
