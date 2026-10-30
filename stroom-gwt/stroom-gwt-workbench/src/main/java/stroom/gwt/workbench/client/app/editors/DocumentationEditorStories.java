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

package stroom.gwt.workbench.client.app.editors;

import stroom.docref.DocRef;
import stroom.documentation.client.component.DocumentationEditor;
import stroom.documentation.shared.DocumentationDoc;
import stroom.documentation.shared.DocumentationResource;
import stroom.entity.client.component.DocumentationTabController;
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// The `App/Editors/DocumentationEditor` stories, showing Stroom's real [DocumentationEditor] (a
/// Documentation document's tab: Documentation and Permissions, with Save, Save As and Download)
/// with fake REST replies.
///
/// `DocumentationPlugin` fetches the document (`GET /documentation/v1/{uuid}`), which the story
/// does as the plugin does ([DocEditors#open]). The Permissions tab has its routes
/// ([DocEditors#permissionRoutes]), and Download is `POST /documentation/v1/download`.
public final class DocumentationEditorStories {

    private static final DocRef DOC_REF = new DocRef(DocumentationDoc.TYPE, "documentation-1", "My Notes");

    // DocumentationResource.fetch()
    private static final String DOC = """
            {"type": "Documentation", "uuid": "documentation-1", "name": "My Notes",
              "data": "# My Notes\\n\\nSome **markdown** documentation."}""";

    private static final RestFixtures FIXTURES = DocEditors.permissionRoutes(RestFixtures.builder())
            .get("/documentation/v1/documentation-1", RestReply.json(DOC))
            .post("/documentation/v1/download", RestReply.json(
                    "{\"resourceKey\": {\"key\": \"k1\", \"name\": \"my-notes.md\"}, \"messageList\": []}"))
            .build();

    // The preview is an iframe with no title, identified by its id (DocumentationTabController's
    // MARKDOWN_FRAME_ID)
    private static final String PREVIEW = "iframe#markdown-frame";

    // Stroom's Ace editor (its text area, ace_text-input, is transparent, so never visible)
    private static final String ACE_EDITOR = ".ace_editor";
    // The Help with Documentation button's name, which says that it opens a new browser tab
    private static final String HELP_BUTTON = "Help with Documentation (opens in new tab)";

    private DocumentationEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/DocumentationEditor", DocumentationEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("Default", context -> render(context, false))
                .withPlay(play -> {
                    // GWT tabs: Documentation (default-selected) and Permissions
                    play.waitFor(() -> play.expect(DocEditors.tab(play, "Documentation")).toBeInTheDocument());
                    play.expect(DocEditors.tab(play, "Permissions")).toBeInTheDocument();
                    // The toolbar carries Save / Save As / Download Documentation; Save starts disabled
                    play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("button", "Save As")).toBeInTheDocument();
                    play.expect(play.getByRole("button", "Download Documentation")).toBeInTheDocument();
                    // A non-blank document loads in preview mode, the preview being an iframe
                    play.waitFor(() -> play.expect(play.querySelector(PREVIEW)).toBeInTheDocument());
                    DocEditors.expectNoProblems(play);
                })
                // The markdown Edit Documentation / Help with Documentation buttons are on the
                // editor's toolbar (HasToolbar); Edit Documentation toggles into the edit view (an
                // Ace editor) and is marked 'on'. Help opens the docs in a new browser tab, which its
                // name says
                .story("EditModeToggle", context -> render(context, false))
                .withPlay(play -> {
                    final Query edit = play.findByRole("button", "Edit Documentation");
                    play.expect(play.getByRole("button", HELP_BUTTON)).toBeInTheDocument();
                    // Loads in preview mode (non-blank body): the Ace editor is hidden (it is declared,
                    // so it is in the page, but not shown or reachable), Edit not pressed
                    play.expect(edit).toHaveAttribute("aria-pressed", "false");
                    play.expect(play.querySelector(ACE_EDITOR)).not().toBeVisible();
                    play.click(edit);
                    play.waitFor(() -> play.expect(play.querySelector(ACE_EDITOR)).toBeVisible());
                    play.expect(edit).toHaveAttribute("aria-pressed", "true");
                    DocEditors.expectNoProblems(play);
                })
                // Typing in the Markdown makes the document changed, so Save is enabled
                .story("TypingEnablesSave", context -> render(context, false))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    play.click(play.findByRole("button", "Edit Documentation"));
                    play.waitFor(() -> play.expect(play.querySelector(ACE_EDITOR)).toBeVisible());
                    play.click(play.querySelector(".ace_content"));
                    play.waitFor(() -> play.expect(play.querySelector(".ace_text-input")).toHaveFocus());
                    play.keyboard("More");
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    DocEditors.expectNoProblems(play);
                })
                // Read only (no EDIT permission): the preview shows, the markdown toolbar is hidden
                // (getDocumentButtons() is empty) and Save is retitled
                .story("ReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    play.findByRole("button", "Save (read only)");
                    play.waitFor(() -> play.expect(play.querySelector(PREVIEW)).toBeInTheDocument());
                    play.expect(play.queryByRole("button", "Edit Documentation")).toBeNull();
                    play.expect(play.queryByRole("button", HELP_BUTTON)).toBeNull();
                    play.expect(play.getByRole("button", "Save (read only)"))
                            .toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context, final boolean readOnly) {
        final DocumentationResource resource = GWT.create(DocumentationResource.class);
        return DocEditors.render(context, FIXTURES, readOnly, (harness, injector) -> DocEditors.open(harness,
                DOC_REF,
                injector.getDocumentationEditor(),
                DocResource.of(
                        restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                        (restFactory, doc) -> restFactory.create(resource)
                                .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
