/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.ai.impl.tools.chat.AskUserTool;
import stroom.ai.impl.tools.chat.ReadTurnTool;
import stroom.ai.impl.tools.chat.SearchHistoryTool;
import stroom.ai.impl.tools.chat.TaskListTool;
import stroom.ai.impl.tools.config.ConfigPropertiesTool;
import stroom.ai.impl.tools.documents.DocumentAnalyses;
import stroom.ai.impl.tools.documents.DocumentDocumentationTool;
import stroom.ai.impl.tools.documents.DocumentInfoTool;
import stroom.ai.impl.tools.documents.DocumentLinksTool;
import stroom.ai.impl.tools.documents.ElasticIndexTemplatesTool;
import stroom.ai.impl.tools.documents.FindInContentTool;
import stroom.ai.impl.tools.documents.FolderContentsTool;
import stroom.ai.impl.tools.documents.ReadDocumentTool;
import stroom.ai.impl.tools.instructions.AgentKnowledge;
import stroom.ai.impl.tools.instructions.GetInstructionsTool;
import stroom.ai.impl.tools.instructions.InstructionEvidenceTool;
import stroom.ai.impl.tools.instructions.MemoryTool;
import stroom.ai.impl.tools.processing.ExplainProcessingTool;
import stroom.ai.impl.tools.processing.ExplainRetentionTool;
import stroom.ai.impl.tools.processing.ProcessorTasksTool;
import stroom.ai.impl.tools.processing.RetentionFacts;
import stroom.ai.impl.tools.processing.ServerTasksTool;
import stroom.ai.impl.tools.processing.StroomRetentionFacts;
import stroom.ai.impl.tools.processing.VolumesTool;
import stroom.ai.impl.tools.propose.ListVisualisationsTool;
import stroom.ai.impl.tools.propose.PreviewExtractionTool;
import stroom.ai.impl.tools.propose.ProposeCodeChangeTool;
import stroom.ai.impl.tools.propose.ProposeDashboardChangeTool;
import stroom.ai.impl.tools.propose.ProposeDashboardTool;
import stroom.ai.impl.tools.propose.ProposeDocumentationChangeTool;
import stroom.ai.impl.tools.propose.ProposeExtractionPipelineTool;
import stroom.ai.impl.tools.propose.ProposeInstructionsTool;
import stroom.ai.impl.tools.propose.ProposeQueryChangeTool;
import stroom.ai.impl.tools.propose.ProposeQueryTool;
import stroom.ai.impl.tools.propose.ProposeVisualisationTool;
import stroom.ai.impl.tools.query.CurrentResultsTool;
import stroom.ai.impl.tools.query.DescribeDataSourceTool;
import stroom.ai.impl.tools.query.EsqlQueryTool;
import stroom.ai.impl.tools.query.FetchEventsTool;
import stroom.ai.impl.tools.query.ListDataSourcesTool;
import stroom.ai.impl.tools.query.RunStroomQlTool;
import stroom.ai.impl.tools.query.SearchEventsTool;
import stroom.ai.impl.tools.query.ValidateStroomQlTool;
import stroom.ai.impl.tools.sequence.SequenceQueryTool;
import stroom.ai.impl.tools.skills.CheckSkillTool;
import stroom.ai.impl.tools.skills.FindSkillsTool;
import stroom.ai.impl.tools.skills.ProposeSkillTool;
import stroom.ai.impl.tools.streams.StreamMetaTool;
import stroom.ai.impl.tools.streams.XsltLookupsTool;
import stroom.ai.impl.tools.time.GetCurrentDateTimeTool;
import stroom.ai.impl.tools.time.GetCurrentDateTool;
import stroom.ai.impl.tools.time.GetCurrentTimeTool;
import stroom.ai.impl.tools.time.GetUtcTimeOffsetTool;
import stroom.ai.impl.tools.timeline.ActivityTimelineTool;
import stroom.ai.impl.tools.workspace.DeleteFileTool;
import stroom.ai.impl.tools.workspace.GanttChartTool;
import stroom.ai.impl.tools.workspace.GrepTool;
import stroom.ai.impl.tools.workspace.JqTool;
import stroom.ai.impl.tools.workspace.LsTool;
import stroom.ai.impl.tools.workspace.ReadFileTool;
import stroom.ai.impl.tools.workspace.StrReplaceTool;
import stroom.ai.impl.tools.workspace.SummariseColumnTool;
import stroom.ai.impl.tools.workspace.TableTool;
import stroom.ai.impl.tools.workspace.WcTool;
import stroom.ai.impl.tools.workspace.WriteFileTool;
import stroom.ai.impl.tools.workspace.XPathTool;
import stroom.util.guice.GuiceUtil;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.OptionalBinder;

/**
 * Binds the built-in chat tools. Add a new {@link StroomTool} implementation and a single
 * {@code .addBinding(...)} line here.
 */
public class BuiltInToolsModule extends AbstractModule {

    @Override
    protected void configure() {
        GuiceUtil.buildMultiBinder(binder(), StroomTool.class)
                .addBinding(GetCurrentDateTool.class)
                .addBinding(GetCurrentTimeTool.class)
                .addBinding(GetCurrentDateTimeTool.class)
                .addBinding(GetUtcTimeOffsetTool.class)
                .addBinding(ReadTurnTool.class)
                .addBinding(SearchHistoryTool.class)
                .addBinding(MemoryTool.class)
                .addBinding(LsTool.class)
                .addBinding(ReadFileTool.class)
                .addBinding(GrepTool.class)
                .addBinding(WcTool.class)
                .addBinding(WriteFileTool.class)
                .addBinding(StrReplaceTool.class)
                .addBinding(DeleteFileTool.class)
                .addBinding(JqTool.class)
                .addBinding(XPathTool.class)
                .addBinding(TaskListTool.class)
                .addBinding(AskUserTool.class)
                .addBinding(FetchEventsTool.class)
                .addBinding(SearchEventsTool.class)
                .addBinding(TableTool.class)
                .addBinding(PipelineTool.class)
                .addBinding(RunStroomQlTool.class)
                .addBinding(EsqlQueryTool.class)
                .addBinding(SequenceQueryTool.class)
                .addBinding(ActivityTimelineTool.class)
                .addBinding(SummariseColumnTool.class)
                .addBinding(DocumentInfoTool.class)
                .addBinding(ReadDocumentTool.class)
                .addBinding(DocumentDocumentationTool.class)
                .addBinding(FolderContentsTool.class)
                .addBinding(GetInstructionsTool.class)
                .addBinding(InstructionEvidenceTool.class)
                .addBinding(ProposeInstructionsTool.class)
                .addBinding(FindSkillsTool.class)
                .addBinding(CheckSkillTool.class)
                .addBinding(ProposeSkillTool.class)
                .addBinding(CurrentResultsTool.class)
                .addBinding(ListDataSourcesTool.class)
                .addBinding(DescribeDataSourceTool.class)
                .addBinding(ValidateStroomQlTool.class)
                .addBinding(ProposeQueryTool.class)
                .addBinding(ProposeDashboardTool.class)
                .addBinding(ProposeQueryChangeTool.class)
                .addBinding(ProposeCodeChangeTool.class)
                .addBinding(StreamMetaTool.class)
                .addBinding(ProcessorTasksTool.class)
                .addBinding(ExplainProcessingTool.class)
                .addBinding(ExplainRetentionTool.class)
                .addBinding(ServerTasksTool.class)
                .addBinding(DocumentLinksTool.class)
                .addBinding(FindInContentTool.class)
                .addBinding(XsltLookupsTool.class)
                .addBinding(ProposeDashboardChangeTool.class)
                .addBinding(PreviewExtractionTool.class)
                .addBinding(ProposeExtractionPipelineTool.class)
                .addBinding(ListVisualisationsTool.class)
                .addBinding(GanttChartTool.class)
                .addBinding(ProposeVisualisationTool.class)
                .addBinding(ProposeDocumentationChangeTool.class)
                .addBinding(ConfigPropertiesTool.class)
                .addBinding(VolumesTool.class)
                .addBinding(ElasticIndexTemplatesTool.class);

        bind(RetentionFacts.class).to(StroomRetentionFacts.class);

        // The guides and conventions bundled for agents, bound by the module that bundles them (stroom-mcp-impl).
        OptionalBinder.newOptionalBinder(binder(), AgentKnowledge.class);
        // What read_document says of an index or an XSLT beyond its content, bound by stroom-mcp-impl.
        OptionalBinder.newOptionalBinder(binder(), DocumentAnalyses.class);
        // The state of the build a session is working on, bound by stroom-mcp-impl.
        OptionalBinder.newOptionalBinder(binder(), BuildProgress.class);
    }
}
