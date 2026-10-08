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

import stroom.annotation.client.AnnotationPresenter;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.task.client.DefaultTaskMonitorFactory;

/// The fake annotation service (`AnnotationResource`, `/annotation/v1`) shared by the annotation
/// editor stories (`App/Main/AnnotationEditor`, `App/Annotations/decorateComment`): the React
/// stories' annotation 42 with its status, label and comment tags.
public final class AnnotationFixtures {

    /// The annotation the stories edit.
    public static final String ANNOTATION = """
            {"type": "Annotation", "uuid": "ann-42-uuid", "id": 42, "name": "Investigate alert",
              "subject": "High CPU", "description": "Initial notes.",
              "status": {"id": 10, "uuid": "s-open", "type": "STATUS", "name": "Open", "style": "RED"}}""";

    private static final String STATUS_TAGS = page(
            "{\"id\": 10, \"uuid\": \"s-open\", \"type\": \"STATUS\", \"name\": \"Open\", \"style\": \"RED\"}",
            "{\"id\": 11, \"uuid\": \"s-closed\", \"type\": \"STATUS\", \"name\": \"Closed\", \"style\": \"GREEN\"}");
    private static final String LABEL_TAGS = page(
            "{\"id\": 20, \"uuid\": \"l-p1\", \"type\": \"LABEL\", \"name\": \"P1\", \"style\": \"AMBER\"}");
    private static final String COMMENT_TAGS = page(
            "{\"id\": 30, \"uuid\": \"c-esc\", \"type\": \"COMMENT\", \"name\": \"Escalate\", "
                    + "\"tagText\": \"Escalating to on-call.\"}");

    private static final String TAGS_PATH = "/annotation/v1/findAnnotationTags";

    private AnnotationFixtures() {
        // Static utility
    }

    /// @param values The values, as JSON.
    /// @return A result page of the values, as JSON.
    public static String page(final String... values) {
        return "{\"values\": [" + String.join(", ", values) + "], \"pageResponse\": {\"offset\": 0, \"length\": "
                + values.length + ", \"total\": " + values.length + ", \"exact\": true}}";
    }

    /// @param id            The entry's id.
    /// @param type          The entry's type (an `AnnotationEntryType` name).
    /// @param user          The display name of the user who made the entry.
    /// @param time          The entry's time.
    /// @param value         The entry's (string) value.
    /// @param previousValue The entry's previous (string) value, or null.
    /// @return An annotation history entry, as JSON.
    public static String entry(final long id,
                               final String type,
                               final String user,
                               final long time,
                               final String value,
                               final String previousValue) {
        final String userRef = "{\"uuid\": \"u\", \"subjectId\": \"" + user.toLowerCase() + "\", \"displayName\": \""
                + user + "\"}";
        return "{\"id\": " + id + ", \"entryType\": \"" + type + "\", \"entryUser\": " + userRef
                + ", \"entryTime\": " + time + ", \"updateUser\": " + userRef + ", \"updateTime\": " + time
                + ", \"entryValue\": {\"type\": \"string\", \"value\": \"" + value + "\"}"
                + (previousValue == null
                ? ""
                : ", \"previousValue\": {\"type\": \"string\", \"value\": \"" + previousValue + "\"}")
                + "}";
    }

    /// Adds the routes the annotation editor needs: annotation 42, its history entries, the
    /// status/label/comment tags (routed by the criteria's tag type), its linked events and
    /// annotations (none) and the changes it sends (all succeed).
    ///
    /// @param builder The fixtures to add to.
    /// @param entries The annotation's history entries, as a JSON array.
    /// @return The builder.
    public static RestFixtures.Builder editorRoutes(final RestFixtures.Builder builder, final String entries) {
        return editorRoutes(builder, entries, "[]", page());
    }

    /// Adds the routes the annotation editor needs, as [#editorRoutes(RestFixtures.Builder, String)]
    /// does, with the given linked events and annotations.
    ///
    /// @param builder     The fixtures to add to.
    /// @param entries     The annotation's history entries, as a JSON array.
    /// @param events      The annotation's linked events, as a JSON array of event ids.
    /// @param annotations The annotations found (e.g. by the annotation chooser), as a result page.
    /// @return The builder.
    public static RestFixtures.Builder editorRoutes(final RestFixtures.Builder builder,
                                                    final String entries,
                                                    final String events,
                                                    final String annotations) {
        return builder
                .route(RequestMatcher.get("/annotation/v1").withQuery("annotationId=42"),
                        RestReply.json(ANNOTATION))
                .post("/annotation/v1/getAnnotationEntries", RestReply.json(entries))
                .route(RequestMatcher.post(TAGS_PATH).withBodyContaining("\"Status\""), RestReply.json(STATUS_TAGS))
                .route(RequestMatcher.post(TAGS_PATH).withBodyContaining("\"Label\""), RestReply.json(LABEL_TAGS))
                .route(RequestMatcher.post(TAGS_PATH).withBodyContaining("\"Comment\""),
                        RestReply.json(COMMENT_TAGS))
                .post(TAGS_PATH, RestReply.json(page()))
                .post("/annotation/v1/getLinkedEvents", RestReply.json(events))
                .post("/annotation/v1/findAnnotations", RestReply.json(annotations))
                .post("/annotation/v1/change", RestReply.json("true"))
                .post("/annotation/v1/fetchAnnotationEntry", RestReply.json(
                        "{\"id\": 2, \"entryType\": \"COMMENT\", "
                                + "\"entryValue\": {\"type\": \"string\", \"value\": \"Looking into it.\"}}"))
                .post("/annotation/v1/changeAnnotationEntry", RestReply.json("true"))
                .post("/annotation/v1/deleteAnnotationEntry", RestReply.json("true"));
    }

    /// Opens an annotation's tab as `AnnotationEditSupport` does for `EditAnnotationEvent`: fetches
    /// the annotation, reads it into a new `AnnotationPresenter` (editable) and shows it.
    ///
    /// @param injector     The story's injector.
    /// @param harness      The story's harness.
    /// @param annotationId The annotation's id.
    public static void open(final ContentScreenGinjector injector,
                            final ScreenHarness harness,
                            final long annotationId) {
        open(injector, harness, annotationId, false);
    }

    /// Opens an annotation as [#open(ContentScreenGinjector, ScreenHarness, long)] does, read only
    /// or not (as Stroom opens it for a user without Edit permission on it).
    ///
    /// @param injector     The screen's injector.
    /// @param harness      The story's harness.
    /// @param annotationId The annotation to open.
    /// @param readOnly     Whether the annotation is read only.
    public static void open(final ContentScreenGinjector injector,
                            final ScreenHarness harness,
                            final long annotationId,
                            final boolean readOnly) {
        injector.getAnnotationResourceClient().getAnnotationById(annotationId, annotation -> {
            final AnnotationPresenter presenter = injector.getAnnotationPresenter();
            presenter.read(annotation, readOnly);
            harness.addContent(presenter);
        }, new DefaultTaskMonitorFactory(harness.getHasHandlers()));
    }
}
