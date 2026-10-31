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

import stroom.ai.impl.tools.DocumentAnalyses;
import stroom.ai.impl.tools.StroomTool;
import stroom.util.guice.GuiceUtil;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.OptionalBinder;

/// Binds the build tools, alongside the built-in ones (see
/// [stroom.ai.impl.tools.BuiltInToolsModule]): they are offered in the chat and over `/mcp` alike. Add a new
/// [StroomTool] and a single `.addBinding(...)` line here.
///
/// It also binds what the build tools share ([WriteGuard], [BuildRecords], [Consent], [BuildPlan]): what they know
/// of a build ([BuildFacts], read from Stroom by [StroomBuildFacts]), sample profiling ([SampleProfiling], bound to
/// the sample profiler), and what read_document (stroom-ai-impl) gives of an index doc and an XSLT
/// ([DocumentAnalysesImpl]).
public class BuildToolsModule extends AbstractModule {

    @Override
    protected void configure() {
        GuiceUtil.buildMultiBinder(binder(), StroomTool.class)
                .addBinding(FindPipelineTemplatesTool.class)
                .addBinding(DescribeTemplateTool.class)
                .addBinding(DescribeFeedTool.class)
                .addBinding(FindReferenceDataTool.class)
                .addBinding(FindElasticClustersTool.class)
                .addBinding(SummariseStreamsTool.class)
                .addBinding(ProcessingStatusTool.class)
                .addBinding(LocateEventTool.class)
                .addBinding(StartOnboardingTool.class)
                .addBinding(BuildStatusTool.class)
                .addBinding(CheckXsltTool.class)
                .addBinding(CheckEventsTool.class)
                .addBinding(WaitForProcessingTool.class)
                .addBinding(ProfileSampleTool.class)
                .addBinding(SurveyFeedTool.class)
                .addBinding(SetShapeHandlingTool.class)
                .addBinding(StepPipelineTool.class)
                .addBinding(StepSampleTool.class)
                .addBinding(StepRecordsTool.class)
                .addBinding(CompareOutputsTool.class)
                .addBinding(SummariseFixTool.class)
                .addBinding(CreateFeedTool.class)
                .addBinding(UploadSampleTool.class)
                .addBinding(RecordSourceNotesTool.class)
                .addBinding(CreateProcessorFilterTool.class)
                .addBinding(SetProcessorFilterEnabledTool.class)
                .addBinding(ReprocessStreamsTool.class)
                .addBinding(CreatePipelineTool.class)
                .addBinding(CopyPipelineTool.class)
                .addBinding(UpdatePipelineTool.class)
                .addBinding(DraftCefMappingTool.class)
                .addBinding(ReviewCoverageTool.class)
                .addBinding(GetFieldConventionsTool.class)
                .addBinding(DraftIndexMappingTool.class)
                .addBinding(CreateIndexDocTool.class)
                .addBinding(CreateIndexingPipelineTool.class)
                .addBinding(RenameIndexTool.class)
                .addBinding(ProposeIndexTemplateTool.class)
                .addBinding(CheckIndexTemplateTool.class)
                .addBinding(VerifyIndexTool.class)
                .addBinding(SaveTextConverterTool.class)
                .addBinding(SaveXsltTool.class)
                .addBinding(SaveDictionaryTool.class)
                .addBinding(DraftTranslationMappingTool.class)
                .addBinding(BuildTranslationXsltTool.class)
                .addBinding(BuildDataSplitterTool.class)
                .addBinding(BuildReferenceXsltTool.class)
                .addBinding(RebuildMappingTool.class)
                .addBinding(StartBuildTool.class)
                .addBinding(WriteDocumentationTool.class)
                .addBinding(PromoteBuildTool.class);

        bind(BuildFacts.class).to(StroomBuildFacts.class);
        OptionalBinder.newOptionalBinder(binder(), SampleProfiling.class)
                .setDefault().to(SampleProfiling.Profiled.class);
        // What read_document (stroom-ai-impl) gives of an index doc and an XSLT from the build tools' helpers.
        OptionalBinder.newOptionalBinder(binder(), DocumentAnalyses.class)
                .setBinding().to(DocumentAnalysesImpl.class);
    }
}
