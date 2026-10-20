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
import stroom.ai.api.ElasticIndexTemplates.Status;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.core.util.Separators.Spacing;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.stream.Collectors;

/// `elastic_index_templates` - for an administrator reviewing or reproducing an Elastic Index document's
/// Elasticsearch templates: the index template that applies to the document's index and each component template
/// it is composed of, read from the document's cluster, each given by name with its body as Elasticsearch returns
/// it. It only reads, and changes nothing in Elasticsearch.
///
/// The user needs View on the document, checked first: a document they can't view is refused without saying
/// whether it exists. The templates are then read by [ElasticIndexTemplates], which checks Read on the document
/// itself too. Each successful read is audited as a View of the document.
class ElasticIndexTemplatesTool extends AbstractBuiltInTool {

    static final String NAME = "elastic_index_templates";
    static final String UUID_ARG = "uuid";
    /// Why the document was viewed, as the audit log records it.
    static final String PURPOSE = "Elasticsearch index and component templates read by the " + NAME + " tool";

    // How the model is to give the templates to the user. Without it, a model has edited the bodies (taking
    // out fields Elasticsearch sets itself) and told the user the result could be applied as it was.
    static final String SHOW_VERBATIM = "Show each template to the user verbatim, with its name just above its "
                                        + "code block: don't add, remove or change anything in it, and don't "
                                        + "say whether or how it can be applied to Elasticsearch.";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexTemplatesTool.class);

    // Bodies are read as they are, numbers and all, and indented as Kibana's Dev Tools console shows them.
    // Nothing is added to or taken from them.
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();
    private static final ObjectWriter PRETTY_WRITER = MAPPER.writer(new DefaultPrettyPrinter()
            .withSeparators(Separators.createDefaultInstance()
                    .withObjectFieldValueSpacing(Spacing.AFTER)
                    .withObjectEmptySeparator("")
                    .withArrayEmptySeparator(""))
            .withObjectIndenter(new DefaultIndenter("  ", "\n"))
            .withArrayIndenter(new DefaultIndenter("  ", "\n")));

    private final DocumentAccess documentAccess;
    private final Provider<ElasticIndexTemplates> elasticIndexTemplatesProvider;

    @Inject
    ElasticIndexTemplatesTool(final DocumentAccess documentAccess,
                              final Provider<ElasticIndexTemplates> elasticIndexTemplatesProvider) {
        this.documentAccess = documentAccess;
        this.elasticIndexTemplatesProvider = elasticIndexTemplatesProvider;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .note("Stroom also checks Read on the Elastic Index document when it reads the templates, and "
                      + "reads them with the document's cluster connection, whose credentials need the "
                      + "privileges to read templates.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("For administrators reviewing or reproducing the Elasticsearch templates of an "
                                 + "Elastic Index document's index. Given the document's UUID, reads from its "
                                 + "Elasticsearch cluster the index template that applies to the index (the "
                                 + "matching template of highest priority, with any lower-priority templates that "
                                 + "also match, which don't apply), and each component template it is composed "
                                 + "of. Each is given by name with its JSON body exactly as Elasticsearch "
                                 + "returns it. " + SHOW_VERBATIM + " "
                                 + "It only reads, and changes nothing in Elasticsearch. Needs View on the "
                                 + "document. A part the cluster's credentials may not read is reported with "
                                 + "Elasticsearch's own reason, which names the missing privilege.")
                        .chatOnly(" If only the document's name is known, find its UUID with folder_contents.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(UUID_ARG, "The Elastic Index document's UUID.")
                        .required(UUID_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String uuid = stringArg(call, UUID_ARG);
        if (uuid == null) {
            return ToolResult.text("Error: give the Elastic Index document's uuid.");
        }
        final ExplorerNode node = documentAccess.find(uuid).orElse(null);
        if (node == null) {
            return ToolResult.text(DocumentAccess.NO_PERMISSION);
        }
        final DocRef docRef = node.getDocRef();
        final StroomLinks links = context.links();
        if (!ElasticIndexDoc.TYPE.equals(docRef.getType())) {
            return ToolResult.text("Error: " + links.document(docRef) + " is a " + docRef.getType()
                                   + ", not an Elastic Index document.");
        }

        final IndexTemplates templates;
        try {
            templates = elasticIndexTemplatesProvider.get().read(docRef);
        } catch (final ReadException e) {
            return switch (e.getReason()) {
                // The user may view it but not read it, or it has gone: not told apart.
                case NOT_FOUND -> ToolResult.text(DocumentAccess.NO_PERMISSION);
                case NOT_CONFIGURED -> ToolResult.text("Error: the templates of " + links.document(docRef)
                                                       + " can't be read, as the document isn't fully set up: "
                                                       + messageOf(e) + ".");
                case CLUSTER_UNAVAILABLE -> ToolResult.text("Error: the Elasticsearch cluster of "
                                                            + links.document(docRef) + " couldn't be reached: "
                                                            + messageOf(e) + ".");
            };
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to read the templates of {}", docRef), e);
            return ToolResult.text("Error: the templates of " + links.document(docRef) + " couldn't be read: "
                                   + messageOf(e) + ".");
        }
        if (templates == null || templates.indexTemplate() == null) {
            return ToolResult.text("Error: the templates of " + links.document(docRef) + " couldn't be read.");
        }

        documentAccess.logView(context.session(), docRef, PURPOSE);
        return ToolResult.text(describe(docRef, links, templates));
    }

    private static String describe(final DocRef docRef, final StroomLinks links, final IndexTemplates templates) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Elastic Index ").append(links.document(docRef)).append('\n');
        sb.append("Index name: `").append(NullSafe.string(templates.indexName())).append("`\n");
        appendResolved(sb, templates);

        final IndexTemplate indexTemplate = templates.indexTemplate();
        sb.append('\n');
        appendIndexTemplateSummary(sb, indexTemplate);

        if (indexTemplate.status() == Status.OK) {
            sb.append("\nNothing has been changed in Elasticsearch. Each body below is exactly as Elasticsearch "
                      + "returns it. ").append(SHOW_VERBATIM).append('\n');
            sb.append("\n### Index template `").append(indexTemplate.name()).append("`\n");
            appendBody(sb, indexTemplate.body());
            for (final ComponentTemplate componentTemplate : templates.componentTemplates()) {
                sb.append("\n### Component template `").append(componentTemplate.name()).append("`\n");
                if (componentTemplate.status() == Status.OK) {
                    appendBody(sb, componentTemplate.body());
                } else {
                    sb.append("This component template ")
                            .append(notRead(componentTemplate.status(), componentTemplate.message()))
                            .append('\n');
                }
            }
        }
        return sb.toString().stripTrailing();
    }

    private static void appendResolved(final StringBuilder sb, final IndexTemplates templates) {
        if (NullSafe.isNonBlankString(templates.resolvedName())) {
            sb.append("Resolves to: `").append(templates.resolvedName())
                    .append("` (the newest index or data stream the index name stands for)\n");
        } else if (NullSafe.isNonBlankString(templates.resolveMessage())) {
            sb.append("Resolves to: unknown, as the index name couldn't be resolved: ")
                    .append(templates.resolveMessage())
                    .append("\nThe templates were matched against the index name itself.\n");
        } else {
            sb.append("Resolves to: no index or data stream exists for it yet, so the templates were matched "
                      + "against the index name itself.\n");
        }
    }

    private static void appendIndexTemplateSummary(final StringBuilder sb, final IndexTemplate indexTemplate) {
        if (indexTemplate.status() != Status.OK) {
            if (indexTemplate.status() == Status.NOT_FOUND) {
                sb.append("Index template: none applies. ")
                        .append(NullSafe.nonBlankStringElse(indexTemplate.message(), "No index template matches."))
                        .append('\n');
            } else {
                sb.append("Index template: ").append(notRead(indexTemplate.status(), indexTemplate.message()))
                        .append('\n');
            }
            return;
        }
        sb.append("Index template that applies: `").append(indexTemplate.name()).append("`\n");
        final List<OverlappingTemplate> overlapping = indexTemplate.overlapping();
        if (!overlapping.isEmpty()) {
            sb.append("Overlapping templates (they match the index too, but have a lower priority, so they don't "
                      + "apply):\n");
            for (final OverlappingTemplate template : overlapping) {
                sb.append("- `").append(template.name()).append("` (index patterns: ")
                        .append(template.indexPatterns().stream()
                                .map(pattern -> "`" + pattern + "`")
                                .collect(Collectors.joining(", ")))
                        .append("; priority ").append(template.priority()).append(")\n");
            }
        }
    }

    private static void appendBody(final StringBuilder sb, final String body) {
        sb.append("```json\n")
                .append(pretty(body)).append('\n')
                .append("```\n");
    }

    // A part Elasticsearch didn't give, with its status and Elasticsearch's own reason.
    private static String notRead(final Status status, final String message) {
        final String reason = NullSafe.nonBlankStringElse(message, "no reason given");
        return switch (status) {
            case FORBIDDEN -> "couldn't be read (FORBIDDEN): the cluster connection's credentials lack the "
                              + "privilege Elasticsearch names: " + reason;
            case NOT_FOUND -> "wasn't found (NOT_FOUND): " + reason;
            case ERROR, OK -> "couldn't be read (" + status + "): " + reason;
        };
    }

    // The body indented, as Kibana's Dev Tools console shows it, or as it is if it isn't JSON.
    static String pretty(final String body) {
        if (NullSafe.isBlankString(body)) {
            return "{}";
        }
        try {
            final JsonNode node = MAPPER.readTree(body);
            return PRETTY_WRITER.writeValueAsString(node);
        } catch (final JsonProcessingException e) {
            LOGGER.debug("Template body is not JSON: {}", e.getMessage());
            return body.strip();
        }
    }

    private static String messageOf(final RuntimeException e) {
        return NullSafe.nonBlankStringElse(e.getMessage(), e.getClass().getSimpleName());
    }
}
