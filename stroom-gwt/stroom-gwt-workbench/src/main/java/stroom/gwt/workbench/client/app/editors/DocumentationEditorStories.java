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
import stroom.documentation.client.presenter.DocumentationPresenter;
import stroom.documentation.shared.DocumentationDoc;
import stroom.documentation.shared.DocumentationResource;
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Editors/DocumentationEditor` in the React Storybook, showing Stroom's real
/// [DocumentationPresenter] (a Documentation document's tab: Documentation and Permissions, with
/// Save, Save As and Download) with fake REST replies.
///
/// The React story passes the document and its seams as props; in Stroom, `DocumentationPlugin`
/// fetches the document (`GET /documentation/v1/{uuid}`), which the story does as the plugin does
/// ([DocEditors#open]). `docPermission` → the Permissions tab's routes
/// ([DocEditors#permissionRoutes]) and `download` → `POST /documentation/v1/download`.
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

    // Differs from React: the preview is an iframe with no title, identified by its id
    // (MarkdownEditPresenter's MARKDOWN_FRAME_ID)
    private static final String PREVIEW = "iframe#markdown-frame";

    // Differs from React: Stroom's Ace editor has a text area with the class ace_text-input too
    private static final String ACE_INPUT = ".ace_text-input";

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
                    // The toolbar carries Save / Save As / Download; Save starts disabled
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    play.expect(play.getByRole("button", "Save As")).toBeInTheDocument();
                    play.expect(play.getByRole("button", "Download")).toBeInTheDocument();
                    // A non-blank document loads in preview mode, the preview being an iframe
                    play.waitFor(() -> play.expect(play.querySelector(PREVIEW)).toBeInTheDocument());
                    DocEditors.expectNoProblems(play);
                })
                // The markdown Edit/Help buttons are on the editor's toolbar (HasToolbar); Edit
                // toggles into the edit view (an Ace editor) and is marked 'on'
                .story("EditModeToggle", context -> render(context, false))
                .withPlay(play -> {
                    final Query edit = play.findByRole("button", "Edit");
                    // Loads in preview mode (non-blank body): no Ace editor, Edit not pressed
                    play.expect(edit).not().toHaveClass("on");
                    play.expect(play.querySelector(ACE_INPUT)).toBeNull();
                    play.click(edit);
                    play.waitFor(() -> play.expect(play.querySelector(ACE_INPUT)).not().toBeNull());
                    play.expect(edit).toHaveClass("on");
                    DocEditors.expectNoProblems(play);
                })
                // Read only (no EDIT permission): the preview shows, the markdown toolbar is hidden
                // (getToolbars() is empty) and Save is retitled
                .story("ReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    play.findByRole("button", "Save is not available as this document is read only");
                    play.waitFor(() -> play.expect(play.querySelector(PREVIEW)).toBeInTheDocument());
                    play.expect(play.queryByRole("button", "Edit")).toBeNull();
                    play.expect(play.queryByRole("button", "Documentation help")).toBeNull();
                    play.expect(play.getByRole("button", "Save is not available as this document is read only"))
                            .toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context, final boolean readOnly) {
        final DocumentationResource resource = GWT.create(DocumentationResource.class);
        return DocEditors.render(context, FIXTURES, readOnly, (harness, injector) -> DocEditors.open(harness,
                DOC_REF,
                injector.getDocumentationPresenter(),
                DocResource.of(
                        restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                        (restFactory, doc) -> restFactory.create(resource)
                                .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
