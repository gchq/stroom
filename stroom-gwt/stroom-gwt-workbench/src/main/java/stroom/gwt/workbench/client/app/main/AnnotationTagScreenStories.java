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


package stroom.gwt.workbench.client.app.main;

import stroom.annotation.client.AnnotationTagPresenter;
import stroom.annotation.shared.AnnotationTagType;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.Map;

/// Stories of `App/Main/AnnotationTagScreen`, showing Stroom's real [AnnotationTagPresenter] (the
/// 'Annotation Labels/Statuses/Comments' tabs, with their create and edit dialogs) with fake REST
/// replies.
///
/// The stories answer Stroom's `AnnotationResource`: `POST /annotation/v1/findAnnotationTags` (a
/// sequence where a tag is created), `POST /annotation/v1/createAnnotationTag` (replying with the
/// new tag) and `PUT /annotation/v1/updateAnnotationTag` (an echo), checked on the request spy, and
/// the permissions popup's `POST /permission/doc/v1/fetchDocumentUserPermissions` (no permissions).
/// The tab is opened as `AnnotationPlugin` does: label, tag type, `refresh()`.
public final class AnnotationTagScreenStories {

    private static final String FIND_PATH = "/annotation/v1/findAnnotationTags";
    private static final String CREATE_PATH = "/annotation/v1/createAnnotationTag";

    private static final String BUG = """
            {"id": 1, "uuid": "u-1", "type": "LABEL", "name": "Bug", "style": "RED"}""";
    private static final String FEATURE = """
            {"id": 100, "uuid": "u-101", "type": "LABEL", "name": "Feature", "style": "NONE"}""";
    private static final String OPEN = """
            {"id": 1, "uuid": "u-1", "type": "STATUS", "name": "Open"}""";
    private static final String TRIAGE = """
            {"id": 1, "uuid": "u-1", "type": "COMMENT", "name": "Triage", "tagText": "Needs triage"}""";

    private AnnotationTagScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/AnnotationTagScreen", AnnotationTagScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Labels: New → create → the edit dialog opens with a Style field
                .story("Labels", context -> render(context, AnnotationTagType.LABEL, "Annotation Labels",
                        RestReply.json(page(BUG)), RestReply.json(page(BUG, FEATURE))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Bug");
                    play.click(play.getByTitle("New"));
                    final Play create = screen.within(screen.findByText("Create New Label")
                            .closest(StroomDom.DIALOG));
                    play.type(create.getByLabelText("Name"), "Feature");
                    play.click(create.getByRole("button", StroomDom.button("OK")));
                    // GWT reopens the created tag in its editor (a bare "Edit <name>" caption) with
                    // the Style field
                    final Play edit = screen.within(screen.findByText("Edit Feature").closest(StroomDom.DIALOG));
                    play.expect(edit.getByText("Style")).toBeVisible();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(CREATE_PATH)
                                    .withJsonBodyContaining("{\"type\": \"LABEL\", \"name\": \"Feature\"}")
                                    .toSpyMatcher());
                    ContentStorySupport.expectNoProblems(play);
                })
                // Statuses: editing a tag offers the "Change Permissions" popup (a tag is a
                // permissioned document)
                .story("Permissions", context -> render(context, AnnotationTagType.STATUS, "Annotation Statuses",
                        RestReply.json(page(OPEN))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Open");
                    // Select the row, Edit: the editor has the permissions button
                    play.click(play.getByText("Open"));
                    play.click(play.getByTitle("Edit"));
                    final Play edit = screen.within(screen.findByText("Edit Status - Open").closest(StroomDom.DIALOG));
                    // GWT's button reads 'Change Permissions', as does the
                    // popup's caption, so the popup is found by its title
                    play.click(edit.getByRole("button", StroomDom.button("Change Permissions")));
                    play.waitFor(() -> play.expect(screen.getByText("Change Permissions", StroomDom.DIALOG_TITLE))
                            .toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/permission/doc/v1/fetchDocumentUserPermissions")
                                    .withJsonBodyContaining("{\"docRef\": {\"type\": \"AnnotationTag\", "
                                            + "\"uuid\": \"u-1\"}}")
                                    .toSpyMatcher()));
                    ContentStorySupport.expectNoProblems(play);
                })
                // Comments: the create dialog has the tag text ("Comment") text area
                .story("Comments", context -> render(context, AnnotationTagType.COMMENT, "Annotation Comments",
                        RestReply.json(page(TRIAGE))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Triage");
                    play.click(play.getByTitle("New"));
                    final Play create = screen.within(screen.findByText("Create New Comment")
                            .closest(StroomDom.DIALOG));
                    play.expect(create.getByLabelText("Comment")).toBeInTheDocument();
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static String page(final String... tags) {
        return "{\"values\": [" + String.join(", ", tags) + "], \"pageResponse\": {\"offset\": 0, \"length\": "
                + tags.length + ", \"total\": " + tags.length + ", \"exact\": true}}";
    }

    // The server's reply to createAnnotationTag: the new tag
    private static RestReply create(final RecordedRequest request) {
        final Map<?, ?> body = (Map<?, ?>) JsonValues.parse(request.getBody());
        return RestReply.json("{\"id\": 100, \"uuid\": \"u-101\", \"type\": \"" + body.get("type")
                + "\", \"name\": \"" + body.get("name") + "\", \"style\": \"NONE\"}");
    }

    private static Widget render(final StoryContext context,
                                 final AnnotationTagType type,
                                 final String label,
                                 final RestReply tags,
                                 final RestReply... more) {
        final RestFixtures fixtures = RestFixtures.builder()
                .post(FIND_PATH, tags, more)
                .post(CREATE_PATH, AnnotationTagScreenStories::create)
                .put("/annotation/v1/updateAnnotationTag", request -> RestReply.json(request.getBody()))
                .post("/permission/doc/v1/fetchDocumentUserPermissions", RestReply.json(
                        "{\"values\": [], \"pageResponse\": {\"offset\": 0, \"length\": 0, \"total\": 0, "
                                + "\"exact\": true}}"))
                .build();
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .build();
        harness.afterStartUp(() -> {
            // As AnnotationPlugin opens the tab
            final AnnotationTagPresenter presenter = injector.getAnnotationTagPresenter();
            presenter.setTabLabel(label);
            presenter.setAnnotationTagType(type);
            presenter.refresh();
            harness.addContent(presenter);
        });
        return harness.asWidget();
    }
}
