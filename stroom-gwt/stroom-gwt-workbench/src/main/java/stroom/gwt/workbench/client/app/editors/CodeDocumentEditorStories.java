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

import stroom.aws.s3.shared.S3ConfigDoc;
import stroom.aws.s3.shared.S3ConfigResource;
import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.kafka.shared.KafkaConfigResource;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterResource;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.XsltResource;
import stroom.script.shared.ScriptDoc;
import stroom.script.shared.ScriptResource;
import stroom.xmlschema.shared.XmlSchemaDoc;
import stroom.xmlschema.shared.XmlSchemaResource;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories matching `App/Editors/CodeDocumentEditor` in the React Storybook, showing Stroom's real
/// code document editors with fake REST replies: `XsltPresenter`, `TextConverterPresenter`,
/// `ScriptPresenter`, `KafkaConfigPresenter`, `S3ConfigPresenter` and `XMLSchemaPresenter`.
///
/// As their plugins do, each story fetches its document (`GET /xslt/v1/{uuid}`,
/// `/textConverter/v1/...`, `/script/v1/...`, `/kafkaConfig/v1/...`, `/s3/v1/...`,
/// `/xmlSchema/v1/...`) and reads it into the editor ([DocEditors#open]). React's seams: `download`
/// → `POST /kafkaConfig/v1/download` (and S3's), XML Schema's `validate` →
/// `POST /xmlSchema/v1/validate`, `docPermission` → the Permissions tab's routes.
public final class CodeDocumentEditorStories {

    // Differs from React: Stroom's Ace editor's text area, which React's port copies
    private static final String ACE_INPUT = ".ace_text-input";

    // Differs from React: a FormGroup gives its control the group's identity as its id
    // (XMLSchemaSettingsViewImpl.ui.xml), not React's '<name>-input'
    private static final String NAMESPACE_URI = "#xmlSchemaSettingsNamespaceURI";

    private static final String DOWNLOAD_REPLY = """
            {"resourceKey": {"key": "k1", "name": "NAME"}, "messageList": []}""";

    private static final String XSLT = """
            {"type": "XSLT", "uuid": "xslt-1", "name": "My XSLT", "data": DATA,
              "description": "# XSLT docs"}""";

    private static final String TEXT_CONVERTER = """
            {"type": "TextConverter", "uuid": "tc-1", "name": "My TC", "converterType": "DATA_SPLITTER",
              "data": "<dataSplitter/>", "description": "# TC docs"}""";

    private static final String SCRIPT = """
            {"type": "Script", "uuid": "script-1", "name": "My Script", "data": "function main() {}",
              "dependencies": [{"type": "Script", "uuid": "base-1", "name": "Base Script"}],
              "description": "# Script docs"}""";

    private static final String KAFKA = """
            {"type": "KafkaConfig", "uuid": "kafka-1", "name": "My Kafka",
              "data": "bootstrap.servers=localhost:9092\\nacks=all", "description": "# Kafka docs"}""";

    private static final String S3 = """
            {"type": "S3Config", "uuid": "s3-1", "name": "My S3",
              "data": "region=eu-west-2\\nbucket=my-bucket", "description": "# S3 docs"}""";

    private static final String SCHEMA = """
            {"type": "XMLSchema", "uuid": "schema-1", "name": "My Schema",
              "namespaceURI": "http://example.com/ns", "systemId": "my-schema", "schemaGroup": "group-a",
              "data": "<xs:schema/>", "description": "# Schema docs"}""";

    private CodeDocumentEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/CodeDocumentEditor", CodeDocumentEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("Xslt", context -> xslt(context, "\"<xsl:stylesheet version=\\\"2.0\\\"/>\""))
                .withPlay(play -> {
                    expectTabs(play, "XSLT", "Documentation", "Permissions");
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                })
                .story("TextConverter", CodeDocumentEditorStories::textConverter)
                .withPlay(play -> {
                    expectTabs(play, "Conversion", "Settings", "Documentation", "Permissions");
                    // Conversion (code) is the default tab; Settings holds the converter type
                    play.click(DocEditors.tab(play, "Settings"));
                    play.expect(play.findByText("Converter Type", "label")).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                .story("Script", CodeDocumentEditorStories::script)
                .withPlay(play -> {
                    expectTabs(play, "Script", "Settings", "Documentation", "Permissions");
                    // Script (code) is the default tab; Settings lists the dependencies
                    play.click(DocEditors.tab(play, "Settings"));
                    play.expect(play.findByText("Base Script")).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // Config (default), Documentation and Permissions, and a Download toolbar button
                .story("KafkaConfig", CodeDocumentEditorStories::kafka)
                .withPlay(play -> {
                    expectTabs(play, "Config", "Documentation", "Permissions");
                    play.expect(play.getByRole("button", "Download")).toBeInTheDocument();
                    play.waitFor(() -> play.expect(play.querySelector(ACE_INPUT)).not().toBeNull());
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                })
                .story("S3Config", CodeDocumentEditorStories::s3)
                .withPlay(play -> {
                    expectTabs(play, "Config", "Documentation", "Permissions");
                    play.expect(play.getByRole("button", "Download")).toBeInTheDocument();
                    play.waitFor(() -> play.expect(play.querySelector(ACE_INPUT)).not().toBeNull());
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                })
                .story("XmlSchema", context -> schema(context, false))
                .withPlay(play -> {
                    expectTabs(play, "Graphical", "Text", "Settings", "Documentation", "Permissions");
                    // The schema's validity indicator is on the toolbar
                    play.waitFor(() -> play.expect(play.getByRole("button", "Schema is valid")).toBeInTheDocument());
                    // Graphical is the default tab; Settings has the schema's fields
                    play.click(DocEditors.tab(play, "Settings"));
                    play.expect(play.findByText("Namespace URI", "label")).toBeInTheDocument();
                    play.expect(play.getByText("System Id", "label")).toBeInTheDocument();
                    final Query save = play.getByRole("button", "Save");
                    play.expect(save).toHaveClass("disabled");
                    play.type(play.querySelector(NAMESPACE_URI), "/v2");
                    // Differs from React: the text box reports its change when it loses the focus
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    DocEditors.expectNoProblems(play);
                })
                // Read only (no EDIT permission): the code editor and settings are read only
                .story("ReadOnly", context -> schema(context, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(DocEditors.tab(play, "Text")).toBeInTheDocument());
                    play.click(DocEditors.tab(play, "Text"));
                    play.waitFor(() -> play.expect(play.querySelector(ACE_INPUT)).not().toBeNull());
                    play.expect(play.querySelector(ACE_INPUT)).toHaveAttribute("readonly");
                    play.click(DocEditors.tab(play, "Settings"));
                    play.waitFor(() -> play.expect(play.querySelector(NAMESPACE_URI)).toBeDisabled());
                    play.expect(play.getByRole("button", "Save is not available as this document is read only"))
                            .toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                })
                // The Format action is on the editor's context menu (getFormatAction()); it
                // reformats the XML, which makes the document dirty
                .story("XsltFormatAction", context -> xslt(context, "\"<a><b>1</b><c><d>2</d></c></a>\""))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.waitFor(() -> play.expect(play.querySelector(".ace_content")).toBeInTheDocument());
                    play.expect(save).toHaveClass("disabled");
                    // Differs from React: the menu opens on a secondary mousedown (EditorViewImpl),
                    // on the page's body
                    play.rightClick(play.querySelector(".ace_content"));
                    play.click(play.screen().findByText("Format", StroomDom.MENU_ITEM_TEXT));
                    final Value<List<String>> lines = play.querySelectorAll(".ace_line").textContents();
                    play.waitFor(() -> play.expect("the editor's value", () -> String.join("\n", lines.get()))
                            .toMatch("\n  <b>1</b>"));
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    DocEditors.expectNoProblems(play);
                });
    }

    // The editor's sub-tabs, in order.
    private static void expectTabs(final Play play, final String... labels) {
        for (final String label : labels) {
            play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
        }
    }

    private static RestFixtures.Builder fixtures(final String path, final String doc) {
        return DocEditors.permissionRoutes(RestFixtures.builder())
                .get(path, RestReply.json(doc))
                .put(path, request -> RestReply.json(request.getBody()));
    }

    private static Widget xslt(final StoryContext context, final String data) {
        final DocRef docRef = new DocRef(XsltDoc.TYPE, "xslt-1", "My XSLT");
        final XsltResource resource = GWT.create(XsltResource.class);
        return DocEditors.render(context, fixtures("/xslt/v1/xslt-1", XSLT.replace("DATA", data)).build(), false,
                (harness, injector) -> DocEditors.open(harness, docRef, injector.getXsltPresenter(),
                        DocResource.of(
                                restFactory -> restFactory.create(resource).method(res -> res.fetch(docRef.getUuid())),
                                (restFactory, doc) -> restFactory.create(resource)
                                        .method(res -> res.update(doc.getUuid(), doc)))));
    }

    private static Widget textConverter(final StoryContext context) {
        final DocRef docRef = new DocRef(TextConverterDoc.TYPE, "tc-1", "My TC");
        final TextConverterResource resource = GWT.create(TextConverterResource.class);
        return DocEditors.render(context, fixtures("/textConverter/v1/tc-1", TEXT_CONVERTER).build(), false,
                (harness, injector) -> DocEditors.open(harness, docRef, injector.getTextConverterPresenter(),
                        DocResource.of(
                                restFactory -> restFactory.create(resource).method(res -> res.fetch(docRef.getUuid())),
                                (restFactory, doc) -> restFactory.create(resource)
                                        .method(res -> res.update(doc.getUuid(), doc)))));
    }

    private static Widget script(final StoryContext context) {
        final DocRef docRef = new DocRef(ScriptDoc.TYPE, "script-1", "My Script");
        final ScriptResource resource = GWT.create(ScriptResource.class);
        return DocEditors.render(context, fixtures("/script/v1/script-1", SCRIPT).build(), false,
                (harness, injector) -> DocEditors.open(harness, docRef, injector.getScriptPresenter(),
                        DocResource.of(
                                restFactory -> restFactory.create(resource).method(res -> res.fetch(docRef.getUuid())),
                                (restFactory, doc) -> restFactory.create(resource)
                                        .method(res -> res.update(doc.getUuid(), doc)))));
    }

    private static Widget kafka(final StoryContext context) {
        final DocRef docRef = new DocRef(KafkaConfigDoc.TYPE, "kafka-1", "My Kafka");
        final KafkaConfigResource resource = GWT.create(KafkaConfigResource.class);
        final RestFixtures fixtures = fixtures("/kafkaConfig/v1/kafka-1", KAFKA)
                .post("/kafkaConfig/v1/download",
                        RestReply.json(DOWNLOAD_REPLY.replace("NAME", "my-kafka.properties")))
                .build();
        return DocEditors.render(context, fixtures, false,
                (harness, injector) -> DocEditors.open(harness, docRef, injector.getKafkaConfigPresenter(),
                        DocResource.of(
                                restFactory -> restFactory.create(resource).method(res -> res.fetch(docRef.getUuid())),
                                (restFactory, doc) -> restFactory.create(resource)
                                        .method(res -> res.update(doc.getUuid(), doc)))));
    }

    private static Widget s3(final StoryContext context) {
        final DocRef docRef = new DocRef(S3ConfigDoc.TYPE, "s3-1", "My S3");
        final S3ConfigResource resource = GWT.create(S3ConfigResource.class);
        final RestFixtures fixtures = fixtures("/s3/v1/s3-1", S3)
                .post("/s3/v1/download", RestReply.json(DOWNLOAD_REPLY.replace("NAME", "my-s3.properties")))
                .build();
        return DocEditors.render(context, fixtures, false,
                (harness, injector) -> DocEditors.open(harness, docRef, injector.getS3ConfigPresenter(),
                        DocResource.of(
                                restFactory -> restFactory.create(resource).method(res -> res.fetch(docRef.getUuid())),
                                (restFactory, doc) -> restFactory.create(resource)
                                        .method(res -> res.update(doc.getUuid(), doc)))));
    }

    private static Widget schema(final StoryContext context, final boolean readOnly) {
        final DocRef docRef = new DocRef(XmlSchemaDoc.TYPE, "schema-1", "My Schema");
        final XmlSchemaResource resource = GWT.create(XmlSchemaResource.class);
        // React's validate seam: the schema is valid
        final RestFixtures fixtures = fixtures("/xmlSchema/v1/schema-1", SCHEMA)
                .post("/xmlSchema/v1/validate", RestReply.json("{\"ok\": true}"))
                .build();
        return DocEditors.render(context, fixtures, readOnly,
                (harness, injector) -> DocEditors.open(harness, docRef, injector.getXMLSchemaPresenter(),
                        DocResource.of(
                                restFactory -> restFactory.create(resource).method(res -> res.fetch(docRef.getUuid())),
                                (restFactory, doc) -> restFactory.create(resource)
                                        .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
