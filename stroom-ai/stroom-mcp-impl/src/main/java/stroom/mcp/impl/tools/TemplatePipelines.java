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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.InstructionRules.ComponentRule;
import stroom.ai.impl.tools.InstructionRules.ComponentRules;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.indexing.TemplateCheck;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// What propose_index_template and check_index_template read of the candidate indexing pipeline (the Stroom MCP
/// server's `tools/indexing.py` `_destination`, `_documents` and `_component_choices`): where it writes, the
/// documents it writes for the sample (by stepping it, as the user), and the component templates the user and their
/// standing instructions (AGENTS docs) include or exclude.
class TemplatePipelines {

    private final IndexTemplateAgreements agreements;
    private final PipelineStepping stepping;
    private final AgentInstructions instructions;

    /// @param agreements   Says where an Elasticsearch indexing pipeline writes.
    /// @param stepping     Steps the pipeline over the sample.
    /// @param instructions Reads the AGENTS docs' component template rules.
    @Inject
    TemplatePipelines(final IndexTemplateAgreements agreements,
                      final PipelineStepping stepping,
                      final AgentInstructions instructions) {
        this.agreements = agreements;
        this.stepping = stepping;
        this.instructions = instructions;
    }

    /// @param pipelineUuid The candidate Elasticsearch indexing pipeline.
    /// @return Where it writes: `index name` and `cluster`.
    /// @throws ToolRefusal if it isn't an Elasticsearch indexing pipeline with an indexName set, or can't be read.
    Map<String, Object> destination(final String pipelineUuid) {
        final Optional<Map<String, Object>> destination;
        try {
            destination = agreements.elasticDestination(pipelineUuid);
        } catch (final PipelineUnreadableException e) {
            throw new ToolRefusal("Pipeline " + pipelineUuid + " can't be read: " + e.getMessage());
        }
        if (destination.isEmpty() || NullSafe.isBlankString((String) destination.get().get("index name"))) {
            throw new ToolRefusal("That is not an Elasticsearch indexing pipeline with an indexName set. An example "
                                  + "index template the user gave before the indexing pipeline exists is used to "
                                  + "draft it: draft_index_mapping example_template= it names the fields after it; "
                                  + "once the indexing pipeline is made and stepped, propose_index_template with it "
                                  + "builds the index template.");
        }
        return destination.get();
    }

    /// The documents the indexing pipeline would send to Elasticsearch, by stepping it over Events (as the user),
    /// within the session's time limit for a long call.
    ///
    /// @param context      The call's context.
    /// @param pipelineUuid The indexing pipeline.
    /// @param streamIds    The streams to step.
    /// @param cap          The most records stepped.
    /// @param tool         The tool stepping, for the audit.
    /// @return The documents, and whether the time limit cut the stepping short.
    /// @throws ToolRefusal if stepping gives no documents.
    /// @throws stroom.ai.impl.workspace.ToolTimeoutException if the call is cancelled.
    Documents documents(final ToolCallContext context,
                        final String pipelineUuid,
                        final List<Long> streamIds,
                        final int cap,
                        final String tool) {
        stepping.checkCanStep();
        final SteppedPipeline pipeline = stepping.load(pipelineUuid);
        final List<String> outputs = pipeline.defaultOutputs();
        final PipelineStepping.Outputs stepped;
        try {
            stepped = PipelineStepping.withinTimeLimit(context, () -> stepping.outputs(context, pipeline, streamIds,
                    outputs.getLast(), null, cap));
        } finally {
            stepping.audit(context, streamIds, "Stepped stream(s) through " + pipeline.name() + " with the " + tool
                                               + " tool");
        }
        final List<Map<String, Object>> docs = new ArrayList<>();
        stepped.outputs().values().forEach(xml -> docs.addAll(TemplateCheck.jsonXmlDocuments(xml)));
        if (docs.isEmpty()) {
            throw new ToolRefusal("Stepping the indexing pipeline gave no documents; step_sample it first and fix "
                                  + "its XSLT");
        }
        return new Documents(docs, stepped.outputs().size(), stepped.stopped());
    }

    /// The components the user nominated, with those their standing instructions (an AGENTS doc's Component
    /// templates) include, and those the instructions exclude. The user's own nomination wins over an instruction
    /// to exclude it: their request takes precedence.
    ///
    /// @param context      The call's context.
    /// @param pipelineUuid The indexing pipeline, whose folders' AGENTS docs apply.
    /// @param nominated    The components the user nominated.
    /// @return What to include and exclude, with notes on the rules followed.
    ComponentChoices componentChoices(final ToolCallContext context,
                                      final String pipelineUuid,
                                      final List<String> nominated) {
        final ComponentRules rules = instructions.componentsRuled(context.session(),
                new DocRef(PipelineDoc.TYPE, pipelineUuid));
        final Set<String> include = new LinkedHashSet<>(nominated);
        rules.include().forEach(rule -> include.add(rule.name()));
        final List<String> exclude = rules.exclude().stream()
                .map(ComponentRule::name)
                .filter(name -> !nominated.contains(name))
                .toList();
        final List<String> notes = new ArrayList<>();
        rules.include().forEach(rule -> notes.add(note(rule, "include")));
        rules.exclude().stream()
                .filter(rule -> !nominated.contains(rule.name()))
                .forEach(rule -> notes.add(note(rule, "exclude")));
        return new ComponentChoices(include.stream().filter(name -> !exclude.contains(name)).toList(), exclude,
                notes);
    }

    private static String note(final ComponentRule rule, final String kind) {
        return "standing instructions (the AGENTS doc in " + NullSafe.nonBlankStringElse(rule.from(), "(root)")
               + ") " + kind + " component template '" + rule.name() + "'";
    }


    // --------------------------------------------------------------------------------


    /// The documents a pipeline writes for the sample.
    ///
    /// @param docs    The documents, as [TemplateCheck#jsonXmlDocuments(String)] reads them.
    /// @param records How many records were stepped.
    /// @param stopped True if the session's time limit cut the stepping short.
    record Documents(List<Map<String, Object>> docs, int records, boolean stopped) {

    }

    /// The component templates to include and exclude.
    ///
    /// @param include Those to compose: the user's, then their standing instructions'.
    /// @param exclude Those the instructions exclude, less those the user nominated.
    /// @param notes   What the instructions said.
    record ComponentChoices(List<String> include, List<String> exclude, List<String> notes) {

    }
}
