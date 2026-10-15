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

package stroom.gwt.workbench.client.app.index;

import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.editors.DocEditors;
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.search.elastic.client.presenter.ElasticIndexPresenter;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexResource;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// The `App/Index/ElasticIndexEditor` stories, showing Stroom's real [ElasticIndexPresenter] (an
/// Elastic index's tab: Settings, Fields, Documentation and Permissions) with fake REST replies.
///
/// As `ElasticIndexPlugin` does, the story fetches the document (`GET /elasticIndex/v1/{uuid}`) and
/// reads it into the editor ([DocEditors#open]). Testing the index is
/// `POST /elasticIndex/v1/testIndex`, and the Permissions tab has its own routes.
public final class ElasticIndexEditorStories {

    private static final DocRef DOC_REF = new DocRef(ElasticIndexDoc.TYPE, "el-events", "Events (Elastic)");

    // ElasticIndexResource.fetch(): the index
    private static final String DOC = """
            {"type": "ElasticIndex", "uuid": "el-events", "name": "Events (Elastic)",
              "description": "# Elastic events index",
              "clusterRef": {"type": "ElasticCluster", "uuid": "c1", "name": "Prod Cluster"},
              "indexName": "stroom-events-*", "searchScrollSize": 1000, "searchSlices": 1,
              "fields": [
                {"fldName": "EventTime", "fldType": "DATE", "nativeType": "date", "indexed": true},
                {"fldName": "UserId", "fldType": "TEXT", "nativeType": "keyword", "indexed": true}]}""";

    private static final RestFixtures FIXTURES = DocEditors.docSelectionRoutes(
                    DocEditors.permissionRoutes(RestFixtures.builder()))
            .get("/elasticIndex/v1/el-events", RestReply.json(DOC))
            .put("/elasticIndex/v1/el-events", request -> RestReply.json(request.getBody()))
            .post("/elasticIndex/v1/testIndex", RestReply.json(
                    "{\"ok\": true, \"message\": \"Connected to 3 nodes.\"}"))
            .build();

    private ElasticIndexEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Index/ElasticIndexEditor", ElasticIndexEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Settings (default), Fields (read only), Documentation and Permissions
                .story("Default", ElasticIndexEditorStories::render)
                .withPlay(play -> {
                    for (final String label : new String[]{"Settings", "Fields", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
                    }
                    // Settings is the default tab, grouped under <h2> headings
                    play.waitFor(() -> play.expect(play.getByRole("heading", "General settings"))
                            .toBeInTheDocument());
                    play.expect(play.getByRole("heading", "Search settings")).toBeInTheDocument();
                    play.expect(play.getByRole("heading", "Vector search")).toBeInTheDocument();
                    play.expect(play.getByText("Index name or pattern", "label")).toBeInTheDocument();
                    play.expect(play.getByRole("button", StroomDom.button("Test Connection"))).toBeInTheDocument();
                    // Fields lists the mapping's fields
                    play.click(DocEditors.tab(play, "Fields"));
                    play.waitFor(() -> play.expect(play.getByText("EventTime")).toBeInTheDocument());
                    play.expect(play.getByText("UserId")).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // The Settings tab (the default) as an editable partner for SettingsReadOnly
                .story("SettingsEditable", context -> render(context, false))
                .withPlay(play -> {
                    waitForSettings(play);
                    DocEditors.expectNoProblems(play);
                })
                // The Settings tab (the default) when the user may only view the index
                .story("SettingsReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    waitForSettings(play);
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                })
                // The Fields tab as an editable partner for FieldsReadOnly (the fields come from the
                // index's mapping, so are never edited here)
                .story("FieldsEditable", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    DocEditors.expectNoProblems(play);
                })
                // The Fields tab when the user may only view the index
                .story("FieldsReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    openFields(play);
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                });
    }

    // Waits for the Settings tab's form
    private static void waitForSettings(final Play play) {
        play.waitFor(() -> play.expect(play.getByRole("heading", "General settings")).toBeInTheDocument());
        play.expect(play.getByText("Index name or pattern", "label")).toBeInTheDocument();
    }

    // Opens the Fields tab and waits for its fields
    private static void openFields(final Play play) {
        play.waitFor(() -> play.expect(DocEditors.tab(play, "Fields")).toBeInTheDocument());
        play.click(DocEditors.tab(play, "Fields"));
        play.waitFor(() -> play.expect(play.getByText("EventTime")).toBeInTheDocument());
    }

    private static Widget render(final StoryContext context) {
        return render(context, false);
    }

    private static Widget render(final StoryContext context, final boolean readOnly) {
        final ElasticIndexResource resource = GWT.create(ElasticIndexResource.class);
        return DocEditors.render(context, FIXTURES, readOnly, (harness, injector) -> DocEditors.open(harness,
                DOC_REF,
                injector.getElasticIndexPresenter(),
                DocResource.of(
                        restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                        (restFactory, doc) -> restFactory.create(resource)
                                .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
