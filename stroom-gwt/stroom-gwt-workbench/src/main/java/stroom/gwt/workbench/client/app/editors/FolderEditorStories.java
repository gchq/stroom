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
import stroom.explorer.shared.ExplorerConstants;
import stroom.folder.client.FolderPresenter;
import stroom.folder.client.FolderRootPresenter;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// The `App/Editors/FolderEditor` stories, showing Stroom's real [FolderPresenter] (a folder's tab:
/// Data, Processors, Active Tasks and Permissions) and, for the System root, [FolderRootPresenter],
/// with fake REST replies.
///
/// As `FolderPlugin` (and `FolderRootPlugin`) do, the story reads the folder's DocRef into the
/// presenter and shows it; there is no document to fetch. The Data tab is Stroom's real
/// `MetaPresenter` (`POST /meta/v1/findMetaRow`, no streams), the Processors tab asks
/// `POST /processorFilter/v1/find` (checked with the request spy), Active Tasks
/// `POST /processorTask/v1/find` and `summary`, and the Permissions tab has its own routes. The
/// user is `ADMINISTRATOR` (the harness's default), which implies View Data and Manage Processors.
public final class FolderEditorStories {

    private static final DocRef FOLDER = new DocRef(ExplorerConstants.FOLDER_TYPE, "folder-uuid-1", "My Folder");

    private static final String EMPTY_PAGE = """
            {"values": [], "pageResponse": {"offset": 0, "length": 0, "total": 0, "exact": true}}""";

    private static final String FIND_PROCESSOR_FILTERS = "/processorFilter/v1/find";

    private static final RestFixtures FIXTURES = DocEditors.permissionRoutes(RestFixtures.builder())
            .post("/meta/v1/find", RestReply.json(EMPTY_PAGE))
            .post(FIND_PROCESSOR_FILTERS, RestReply.json(EMPTY_PAGE))
            .post("/processorTask/v1/find", RestReply.json(EMPTY_PAGE))
            .post("/processorTask/v1/summary", RestReply.json(EMPTY_PAGE))
            .build();

    private FolderEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/FolderEditor", FolderEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A folder has four tabs: Data, Processors, Active Tasks and Permissions
                .story("Folder", context -> render(context, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(DocEditors.tab(play, "Data")).toBeInTheDocument());
                    play.expect(DocEditors.tab(play, "Processors")).toBeInTheDocument();
                    play.expect(DocEditors.tab(play, "Active Tasks")).toBeInTheDocument();
                    play.expect(DocEditors.tab(play, "Permissions")).toBeInTheDocument();
                    // Data is the default tab.
                    // It is Stroom's real data browser (MetaPresenter), so the play checks its
                    // stream list's Feed column
                    play.waitFor(() -> play.expect(play.getAllByText("Feed").nth(0)).toBeVisible());
                    // Processors: an administrator may edit but not add or duplicate (a folder has
                    // no single pipeline)
                    play.click(DocEditors.tab(play, "Processors"));
                    play.expect(play.findByTitle("Edit Processor")).toBeInTheDocument();
                    play.expect(play.queryByTitle("Add Processor")).toBeNull();
                    play.expect(play.queryByTitle("Duplicate Processor")).toBeNull();
                    play.click(DocEditors.tab(play, "Permissions"));
                    DocEditors.expectNoProblems(play);
                })
                // The System root (FolderRootPresenter)
                .story("SystemRoot", context -> render(context, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(DocEditors.tab(play, "Data")).toBeInTheDocument());
                    play.expect(DocEditors.tab(play, "Permissions")).toBeInTheDocument();
                    // Stroom's FolderRootPresenter also shows the Processors and Active Tasks tabs
                    // to users who may manage processors
                    play.expect(DocEditors.tab(play, "Processors")).toBeInTheDocument();
                    play.expect(DocEditors.tab(play, "Active Tasks")).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // The Processors tab queries on ProcessorFields' 'Processor Pipeline', not 'Pipeline'
                .story("ProcessorsTabQueriesTheProcessorPipelineField", context -> render(context, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(DocEditors.tab(play, "Processors")).toBeInTheDocument());
                    play.click(DocEditors.tab(play, "Processors"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(FIND_PROCESSOR_FILTERS)
                                    .withBody("an expression on 'Processor Pipeline' and 'Analytic Rule', "
                                              + "not 'Pipeline'", FolderEditorStories::queriesProcessorPipeline)
                                    .toSpyMatcher()));
                    DocEditors.expectNoProblems(play);
                });
    }

    // Whether a processor filter request's expression is on 'Processor Pipeline' and 'Analytic
    // Rule', and not on 'Pipeline'
    private static boolean queriesProcessorPipeline(final String body) {
        final List<String> ids = fieldIds(JsonValues.parse(body), new ArrayList<>());
        return ids.contains("Processor Pipeline")
               && ids.contains("Analytic Rule")
               && !ids.contains("Pipeline");
    }

    // Every 'field' anywhere in a nested expression
    private static List<String> fieldIds(final Object node, final List<String> ids) {
        if (node instanceof final Map<?, ?> map) {
            if (map.get("field") instanceof final String field) {
                ids.add(field);
            }
            for (final Object value : map.values()) {
                fieldIds(value, ids);
            }
        } else if (node instanceof final List<?> list) {
            for (final Object item : list) {
                fieldIds(item, ids);
            }
        }
        return ids;
    }

    private static Widget render(final StoryContext context, final boolean systemRoot) {
        return DocEditors.render(context, FIXTURES, false, (harness, injector) -> {
            // As FolderPlugin.showDocument (and FolderRootPlugin) do
            if (systemRoot) {
                final FolderRootPresenter presenter = injector.getFolderRootPresenter();
                presenter.read();
                harness.addContent(presenter);
            } else {
                final FolderPresenter presenter = injector.getFolderPresenter();
                presenter.read(FOLDER);
                harness.addContent(presenter);
            }
        });
    }
}
