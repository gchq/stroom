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

package stroom.ai.impl.tools;

import stroom.ai.api.ElasticIndexTemplates;
import stroom.ai.api.ElasticIndexTemplates.ComponentTemplate;
import stroom.ai.api.ElasticIndexTemplates.IndexTemplate;
import stroom.ai.api.ElasticIndexTemplates.IndexTemplates;
import stroom.ai.api.ElasticIndexTemplates.OverlappingTemplate;
import stroom.ai.api.ElasticIndexTemplates.ReadException;
import stroom.ai.api.ElasticIndexTemplates.Reason;
import stroom.ai.api.ElasticIndexTemplates.Status;
import stroom.ai.impl.AiAuditLog;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.shared.FeedDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestElasticIndexTemplatesTool {

    private static final DocRef INDEX = new DocRef(ElasticIndexDoc.TYPE, "es-uuid", "ecs-keycloak-v1");
    private static final DocRef FEED = new DocRef(FeedDoc.TYPE, "feed-uuid", "KEYCLOAK");
    private static final String INDEX_LINK = "[ecs-keycloak-v1](?action=open-doc&docType=ElasticIndex"
                                             + "&docUuid=es-uuid)";

    private static final String INDEX_TEMPLATE_BODY = "{\"index_patterns\":[\"ecs-keycloak-*\"],"
                                                      + "\"composed_of\":[\"ecs@mappings\",\"keycloak-settings\"],"
                                                      + "\"priority\":200,\"template\":{\"settings\":{}}}";
    private static final String MAPPINGS_BODY = "{\"template\":{\"mappings\":{\"properties\":{\"user\":"
                                                + "{\"type\":\"keyword\"},\"ratio\":{\"type\":\"float\","
                                                + "\"null_value\":1.50}}}}}";
    private static final String SETTINGS_BODY = "{\"template\":{\"settings\":{\"number_of_shards\":1}}}";
    private static final String FORBIDDEN_MESSAGE = "action [cluster:monitor/component_template/get] is "
                                                    + "unauthorized for user [stroom] with effective roles "
                                                    + "[stroom_writer], this action is granted by the cluster "
                                                    + "privileges [manage_index_templates,monitor,manage,all]";

    private final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final ElasticIndexTemplates elasticIndexTemplates = Mockito.mock(ElasticIndexTemplates.class);
    @SuppressWarnings("rawtypes")
    private final Map<DocumentTypeName, DocumentActionHandler> handlers = new HashMap<>();
    private final Set<String> viewable = new HashSet<>();
    private ElasticIndexTemplatesTool tool;

    @BeforeEach
    void setUp() {
        final DocumentAccess documentAccess = new DocumentAccess(() -> explorerNodeService, securityContext,
                () -> handlers, auditLog);
        tool = new ElasticIndexTemplatesTool(documentAccess, () -> elasticIndexTemplates);
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.eq(DocumentPermission.VIEW)))
                .thenAnswer(invocation -> viewable.contains(invocation.<DocRef>getArgument(0).getUuid()));
        for (final DocRef docRef : List.of(INDEX, FEED)) {
            Mockito.when(explorerNodeService.getNodeByUuid(docRef.getUuid()))
                    .thenReturn(Optional.of(ExplorerNode.builder().docRef(docRef).build()));
        }
    }

    @Test
    void anIndexTheUserCantViewIsRefusedWithoutSayingIfItExists() {
        // It exists, but the user can't view it.
        assertThat(run("es-uuid")).isEqualTo(DocumentAccess.NO_PERMISSION);
        // It doesn't exist.
        assertThat(run("no-such-uuid")).isEqualTo(DocumentAccess.NO_PERMISSION);

        Mockito.verifyNoInteractions(elasticIndexTemplates, auditLog);
    }

    @Test
    void aViewCheckThatFailsRefuses() {
        Mockito.when(securityContext.hasDocumentPermission(INDEX, DocumentPermission.VIEW))
                .thenThrow(new RuntimeException("check failed"));

        assertThat(run("es-uuid")).isEqualTo(DocumentAccess.NO_PERMISSION);
        Mockito.verifyNoInteractions(elasticIndexTemplates, auditLog);
    }

    @Test
    void aDocumentThatIsntAnElasticIndexIsRefused() {
        // Not viewable: the same as one that doesn't exist.
        assertThat(run("feed-uuid")).isEqualTo(DocumentAccess.NO_PERMISSION);

        // Viewable: said to be the wrong type.
        viewable.add(FEED.getUuid());
        assertThat(run("feed-uuid"))
                .startsWith("Error:")
                .contains("is a Feed, not an Elastic Index document");
        Mockito.verifyNoInteractions(elasticIndexTemplates, auditLog);
    }

    @Test
    void aMissingUuidIsAnError() {
        assertThat(tool.execute(new ToolCall(null, ElasticIndexTemplatesTool.NAME, "{}"), ToolCalls.inChat(1))
                .text()).startsWith("Error: give the Elastic Index document's uuid");
        Mockito.verifyNoInteractions(elasticIndexTemplates, auditLog);
    }

    @Test
    void theTemplatesAreGivenAsDevToolsRequests() {
        viewable.add(INDEX.getUuid());
        Mockito.when(elasticIndexTemplates.read(INDEX)).thenReturn(new IndexTemplates(
                "ecs-keycloak-v1",
                "ecs-keycloak-v1-000003",
                null,
                new IndexTemplate(Status.OK, "ecs-keycloak", INDEX_TEMPLATE_BODY, List.of(), null),
                List.of(new ComponentTemplate("ecs@mappings", Status.OK, MAPPINGS_BODY, null),
                        new ComponentTemplate("keycloak-settings", Status.OK, SETTINGS_BODY, null))));

        final String result = run("es-uuid");

        assertThat(result)
                .startsWith("Elastic Index " + INDEX_LINK + "\n")
                .contains("Index name: `ecs-keycloak-v1`")
                .contains("Resolves to: `ecs-keycloak-v1-000003`")
                .contains("Index template that applies: `ecs-keycloak`")
                .doesNotContain("Overlapping")
                .contains("Nothing has been changed in Elasticsearch")
                .contains("(such as `created_date_millis`), which it rejects in a PUT request: it can't be "
                          + "applied as it is.")
                .doesNotContain("PUT _")
                .contains("""
                        ### Index template `ecs-keycloak`
                        ```json
                        {
                          "index_patterns": [
                            "ecs-keycloak-*"
                          ],
                          "composed_of": [
                            "ecs@mappings",
                            "keycloak-settings"
                          ],
                          "priority": 200,
                          "template": {
                            "settings": {}
                          }
                        }
                        ```
                        """)
                // Numbers are kept as Elasticsearch gave them.
                .contains("""
                        ### Component template `ecs@mappings`
                        ```json
                        {
                          "template": {
                            "mappings": {
                              "properties": {
                                "user": {
                                  "type": "keyword"
                                },
                                "ratio": {
                                  "type": "float",
                                  "null_value": 1.50
                                }
                              }
                            }
                          }
                        }
                        ```
                        """)
                .contains("""
                        ### Component template `keycloak-settings`
                        ```json
                        {
                          "template": {
                            "settings": {
                              "number_of_shards": 1
                            }
                          }
                        }
                        ```""");
    }

    @Test
    void eachSuccessfulReadIsLoggedAsAViewOfTheIndex() {
        viewable.add(INDEX.getUuid());
        Mockito.when(elasticIndexTemplates.read(INDEX)).thenReturn(templates(
                new IndexTemplate(Status.OK, "ecs-keycloak", INDEX_TEMPLATE_BODY, List.of(), null),
                List.of()));

        run("es-uuid");
        run("es-uuid");

        Mockito.verify(auditLog, Mockito.times(2)).documentViewed(ToolSession.chat(1), INDEX,
                "Elasticsearch index and component templates read by the elastic_index_templates tool");
        Mockito.verifyNoMoreInteractions(auditLog);
    }

    @Test
    void overlappingTemplatesAreListedAsNotApplying() {
        viewable.add(INDEX.getUuid());
        Mockito.when(elasticIndexTemplates.read(INDEX)).thenReturn(templates(
                new IndexTemplate(Status.OK, "ecs-keycloak", INDEX_TEMPLATE_BODY, List.of(
                        new OverlappingTemplate("ecs", List.of("ecs-*"), 100),
                        new OverlappingTemplate("catch-all", List.of("*", "logs-*"), 0)), null),
                List.of()));

        assertThat(run("es-uuid"))
                .contains("Index template that applies: `ecs-keycloak`\n"
                          + "Overlapping templates (they match the index too, but have a lower priority, so they "
                          + "don't apply):\n"
                          + "- `ecs` (index patterns: `ecs-*`; priority 100)\n"
                          + "- `catch-all` (index patterns: `*`, `logs-*`; priority 0)\n");
    }

    @Test
    void aComponentTemplateThatIsForbiddenIsGivenWithElasticsearchsReason() {
        viewable.add(INDEX.getUuid());
        Mockito.when(elasticIndexTemplates.read(INDEX)).thenReturn(templates(
                new IndexTemplate(Status.OK, "ecs-keycloak", INDEX_TEMPLATE_BODY, List.of(), null),
                List.of(new ComponentTemplate("ecs@mappings", Status.OK, MAPPINGS_BODY, null),
                        new ComponentTemplate("keycloak-settings", Status.FORBIDDEN, null, FORBIDDEN_MESSAGE),
                        new ComponentTemplate("gone", Status.NOT_FOUND, null,
                                "Component template 'gone' was not found"))));

        final String result = run("es-uuid");

        assertThat(result)
                .contains("### Index template `ecs-keycloak`\n```json\n{")
                .contains("### Component template `ecs@mappings`\n```json\n{")
                .contains("### Component template `keycloak-settings`\n"
                          + "This component template couldn't be read (FORBIDDEN): the cluster connection's "
                          + "credentials lack the privilege Elasticsearch names: " + FORBIDDEN_MESSAGE)
                .contains("### Component template `gone`\n"
                          + "This component template wasn't found (NOT_FOUND): Component template 'gone' was "
                          + "not found");
    }

    @Test
    void noIndexTemplateMatching() {
        viewable.add(INDEX.getUuid());
        Mockito.when(elasticIndexTemplates.read(INDEX)).thenReturn(templates(
                new IndexTemplate(Status.NOT_FOUND, null, null, List.of(),
                        "No index template matches 'ecs-keycloak-v1-000003'"),
                List.of()));

        assertThat(run("es-uuid"))
                .contains("Index template: none applies. No index template matches 'ecs-keycloak-v1-000003'")
                .doesNotContain("```");
        // The index was still read.
        Mockito.verify(auditLog).documentViewed(Mockito.any(), Mockito.eq(INDEX), Mockito.anyString());
    }

    @Test
    void anIndexTemplateThatIsForbidden() {
        viewable.add(INDEX.getUuid());
        Mockito.when(elasticIndexTemplates.read(INDEX)).thenReturn(templates(
                new IndexTemplate(Status.FORBIDDEN, null, null, List.of(), FORBIDDEN_MESSAGE),
                List.of()));

        assertThat(run("es-uuid"))
                .contains("Index template: couldn't be read (FORBIDDEN): the cluster connection's credentials "
                          + "lack the privilege Elasticsearch names: " + FORBIDDEN_MESSAGE)
                .doesNotContain("```");
    }

    @Test
    void anIndexNameThatCouldntBeResolved() {
        viewable.add(INDEX.getUuid());
        Mockito.when(elasticIndexTemplates.read(INDEX)).thenReturn(new IndexTemplates(
                "ecs-keycloak-v1",
                null,
                "action [indices:admin/resolve/index] is unauthorized for user [stroom]",
                new IndexTemplate(Status.OK, "ecs-keycloak", INDEX_TEMPLATE_BODY, List.of(), null),
                List.of()));

        assertThat(run("es-uuid"))
                .contains("Resolves to: unknown, as the index name couldn't be resolved: action "
                          + "[indices:admin/resolve/index] is unauthorized for user [stroom]\n"
                          + "The templates were matched against the index name itself.")
                .contains("### Index template `ecs-keycloak`\n```json\n{");
    }

    @Test
    void anIndexNameThatResolvesToNothingYet() {
        viewable.add(INDEX.getUuid());
        Mockito.when(elasticIndexTemplates.read(INDEX)).thenReturn(templates(
                new IndexTemplate(Status.OK, "ecs-keycloak", INDEX_TEMPLATE_BODY, List.of(), null),
                List.of()));

        assertThat(run("es-uuid"))
                .contains("Resolves to: no index or data stream exists for it yet, so the templates were matched "
                          + "against the index name itself.");
    }

    @Test
    void aClusterThatCantBeReached() {
        viewable.add(INDEX.getUuid());
        Mockito.when(elasticIndexTemplates.read(INDEX))
                .thenThrow(new ReadException(Reason.CLUSTER_UNAVAILABLE, "Connection refused"));

        assertThat(run("es-uuid")).isEqualTo("Error: the Elasticsearch cluster of " + INDEX_LINK
                                             + " couldn't be reached: Connection refused.");
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void anIndexThatIsntSetUp() {
        viewable.add(INDEX.getUuid());
        Mockito.when(elasticIndexTemplates.read(INDEX))
                .thenThrow(new ReadException(Reason.NOT_CONFIGURED, "Elasticsearch index name not specified"));

        assertThat(run("es-uuid")).isEqualTo("Error: the templates of " + INDEX_LINK + " can't be read, as the "
                                             + "document isn't fully set up: Elasticsearch index name not "
                                             + "specified.");
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void anIndexTheUserCanViewButNotReadIsRefusedAsNotFound() {
        viewable.add(INDEX.getUuid());
        Mockito.when(elasticIndexTemplates.read(INDEX))
                .thenThrow(new ReadException(Reason.NOT_FOUND, "Elastic Index es-uuid not found"));

        assertThat(run("es-uuid")).isEqualTo(DocumentAccess.NO_PERMISSION);
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void anUnexpectedFailureIsAnError() {
        viewable.add(INDEX.getUuid());
        Mockito.when(elasticIndexTemplates.read(INDEX)).thenThrow(new IllegalStateException("boom"));

        assertThat(run("es-uuid")).isEqualTo("Error: the templates of " + INDEX_LINK + " couldn't be read: boom.");
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void aBodyThatIsntJsonIsGivenAsItIs() {
        assertThat(ElasticIndexTemplatesTool.pretty("not json")).isEqualTo("not json");
        assertThat(ElasticIndexTemplatesTool.pretty(null)).isEqualTo("{}");
        assertThat(ElasticIndexTemplatesTool.pretty("{\"a\":[]}")).isEqualTo("{\n  \"a\": []\n}");
    }

    @Test
    void itIsReadOnlyReadsUntrustedContentAndChecksView() {
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.readsUntrustedContent()).isTrue();
        assertThat(tool.permissions().documentPermissions()).containsExactly(DocumentPermission.VIEW);
        assertThat(tool.permissions().appPermissions()).isEmpty();
        assertThat(tool.definition().general().description())
                .contains("For administrators")
                .contains("changes nothing in Elasticsearch")
                .contains("Needs View on the document")
                .doesNotContain("folder_contents");
    }

    private static IndexTemplates templates(final IndexTemplate indexTemplate,
                                            final List<ComponentTemplate> componentTemplates) {
        return new IndexTemplates("ecs-keycloak-v1", null, null, indexTemplate, componentTemplates);
    }

    private String run(final String uuid) {
        return tool.execute(new ToolCall(null, ElasticIndexTemplatesTool.NAME, "{\"uuid\":\"" + uuid + "\"}"),
                ToolCalls.inChat(1)).text();
    }
}
