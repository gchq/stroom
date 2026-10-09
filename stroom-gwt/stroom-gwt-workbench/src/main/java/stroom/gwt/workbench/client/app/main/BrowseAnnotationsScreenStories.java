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

import stroom.annotation.client.BrowseAnnotationPresenter;
import stroom.annotation.client.EditAnnotationEvent;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/BrowseAnnotationsScreen`, showing Stroom's real [BrowseAnnotationPresenter]
/// (the 'Annotations' tab) with fake REST replies.
///
/// The annotations come from `POST /annotation/v1/findAnnotations`, and `onOpenAnnotation` is a spy
/// on Stroom's `EditAnnotationEvent`. The tab is opened as `AnnotationBrowsePlugin` does
/// (`refresh()`).
public final class BrowseAnnotationsScreenStories {

    /// The name of the spy recording the annotations opened (Stroom's `EditAnnotationEvent`).
    static final String ON_OPEN_ANNOTATION = "onOpenAnnotation";

    private static final String ANNOTATIONS = """
            {
              "values": [
                {"type": "Annotation", "uuid": "ann-1", "id": 1, "name": "Suspicious login", "createUser": "alice",
                  "createTimeMs": 1700000000000,
                  "status": {"id": 10, "uuid": "s-open", "type": "STATUS", "name": "Open", "style": "RED"},
                  "labels": [{"id": 20, "uuid": "l-p1", "type": "LABEL", "name": "P1", "style": "AMBER"}],
                  "assignedTo": {"uuid": "u-bob", "subjectId": "bob", "displayName": "Bob"}},
                {"type": "Annotation", "uuid": "ann-2", "id": 2, "name": "Routine review", "createUser": "carol",
                  "createTimeMs": 1700000000000}
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/annotation/v1/findAnnotations", RestReply.json(ANNOTATIONS))
            .build();

    private BrowseAnnotationsScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/BrowseAnnotationsScreen", BrowseAnnotationsScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The list loads with title and status/label lozenges; a double-click opens the editor
                .story("Browse", BrowseAnnotationsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("Suspicious login");
                    play.expect(play.getByText("Open")).toBeInTheDocument();
                    play.expect(play.getByText("P1")).toBeInTheDocument();
                    play.dblClick(play.getByText("Suspicious login"));
                    play.waitFor(() -> play.expect(play.spy(ON_OPEN_ANNOTATION)).toHaveBeenCalledWith("1"));
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.fn(ON_OPEN_ANNOTATION);
        harness.addRegistration(harness.getEventBus().addHandler(EditAnnotationEvent.getType(), event ->
                harness.spy(ON_OPEN_ANNOTATION, String.valueOf(event.getAnnotationId()))));
        harness.afterStartUp(() -> {
            // As AnnotationBrowsePlugin opens the tab
            final BrowseAnnotationPresenter presenter = injector.getBrowseAnnotationPresenter();
            presenter.refresh();
            harness.addContent(presenter);
        });
        return harness.asWidget();
    }
}
