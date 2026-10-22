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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.tools.AcceptedErrors;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.SessionLimits;
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.docref.DocRef;
import stroom.pipeline.factory.ElementRegistry;
import stroom.pipeline.factory.ElementRegistryFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.util.time.StroomDuration;

import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

/// The stepping tools' tests' Stroom (as the Stroom MCP server's `tests/test_stepping.py` mocks it): the pipeline
/// ACME, `p-1`, whose template `t` adds a Data Splitter, a translation XSLT and a schema filter and which sets the
/// XSLT itself; streams stepped by a function of the stream and the step; and the build layer as mocks.
final class SteppingWorld {

    static final String UUID = "p-1";
    static final String TEMPLATE = "t";
    static final DocRef REF = new DocRef(PipelineDoc.TYPE, UUID, "ACME");

    final SteppingWalks steppingWalks = Mockito.mock(SteppingWalks.class);
    final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    final ElementRegistryFactory elementRegistryFactory = Mockito.mock(ElementRegistryFactory.class);
    final StreamSummaries streamSummaries = Mockito.mock(StreamSummaries.class);
    final AcceptedErrors acceptedErrors = Mockito.mock(AcceptedErrors.class);
    final CheckXsltTool checkXslt = Mockito.mock(CheckXsltTool.class);
    final StreamReader streamReader = Mockito.mock(StreamReader.class);
    final SampleStreamTexts sampleStreamTexts = Mockito.mock(SampleStreamTexts.class);
    final WriteGuard guard = Mockito.mock(WriteGuard.class);
    final KeptMappings keptMappings = Mockito.mock(KeptMappings.class);
    final BuildRecords buildRecords = Mockito.mock(BuildRecords.class);
    final BuildPlan plan = Mockito.mock(BuildPlan.class);
    final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    final List<String> said = new ArrayList<>();
    /// Each walk opened: the stream, and the code it was opened with.
    final List<Map.Entry<Long, Map<String, String>>> opened = new ArrayList<>();
    /// Each walk's steps, in the order made: `FIRST`, `LAST`, `FORWARD <record>` or `REFRESH <part>:<record>`.
    final List<String> steps = new ArrayList<>();
    final List<SteppingWalks.Walk> walks = new ArrayList<>();
    final Map<String, PipelineDoc> pipelines = new HashMap<>();

    /// What a step gives: of the stream, for the step type and the record index it steps to; null for no record.
    BiFunction<Long, StepLocation, SteppingResult> stepper = (stream, at) -> null;

    SteppingWorld() {
        pipeline(UUID, "ACME", TEMPLATE, new PipelineDataBuilder()
                .addProperty(new PipelineProperty("translationFilter", "xslt",
                        new PipelinePropertyValue(new DocRef(XsltDoc.TYPE, "x", "ACME")))));
        pipeline(TEMPLATE, "Event Data (Text)", null, new PipelineDataBuilder()
                .addElement(new PipelineElement("dsParser", "DSParser"))
                .addElement(new PipelineElement("translationFilter", "XSLTFilter"))
                .addElement(new PipelineElement("schemaFilter", "SchemaFilter"))
                .addLink("dsParser", "translationFilter")
                .addLink("translationFilter", "schemaFilter"));
        Mockito.when(documentAccess.findDocRef(anyString())).thenAnswer(inv -> Optional.ofNullable(
                pipelines.get((String) inv.getArgument(0))).map(PipelineDoc::asDocRef));
        Mockito.when(documentAccess.read(any(DocRef.class), Mockito.eq(PipelineDoc.class))).thenAnswer(inv ->
                Optional.ofNullable(pipelines.get(((DocRef) inv.getArgument(0)).getUuid())));
        Mockito.when(documentAccess.canViewData()).thenReturn(true);
        Mockito.when(securityContext.hasAppPermission(AppPermission.STEPPING_PERMISSION)).thenReturn(true);
        // A registry that declares no property types.
        Mockito.when(elementRegistryFactory.get()).thenReturn(Mockito.mock(ElementRegistry.class));
        Mockito.when(streamSummaries.errorTriage()).thenReturn(ErrorTriage.bundled());
        Mockito.when(acceptedErrors.acceptedFor(anyString())).thenReturn(List.of());
        Mockito.when(streamReader.find(any(), anyInt(), Mockito.anyBoolean())).thenReturn(ResultPage.empty());
        Mockito.when(guard.tags(any())).thenReturn(Set.of());
        Mockito.when(guard.copyOf(any())).thenReturn(Optional.empty());
        Mockito.when(keptMappings.keptMapping(anyString())).thenReturn(Optional.empty());
        Mockito.when(buildRecords.recordPassed(any(), anyString(), any(), any())).thenReturn(Optional.empty());
        Mockito.when(plan.buildOf(any())).thenReturn(Optional.empty());
        Mockito.when(plan.withNext(any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));
        Mockito.when(steppingWalks.open(any(), anyLong(), any(), any())).thenAnswer(inv -> walk(
                inv.getArgument(1), inv.getArgument(2)));
    }

    /// @return The stepping the tools share, on this world.
    PipelineStepping stepping() {
        return new PipelineStepping(steppingWalks, documentAccess, () -> securityContext,
                () -> elementRegistryFactory, streamSummaries, acceptedErrors, checkXslt, streamReader,
                sampleStreamTexts, guard, keptMappings, buildRecords, plan, auditLog);
    }

    /// @return A call's context from an MCP client, with its progress said to [#said].
    ToolCallContext context() {
        return contextBuilder().build();
    }

    /// @return A builder for a call's context from an MCP client, with its progress said to [#said].
    ToolCallContext.Builder contextBuilder() {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-session-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                .userQuestions(UserQuestions.NONE)
                .progress(said::add);
    }

    /// Adds a pipeline.
    void pipeline(final String uuid, final String name, final String parent, final PipelineDataBuilder data) {
        pipelines.put(uuid, PipelineDoc.builder()
                .uuid(uuid)
                .name(name)
                .parentPipeline(parent == null
                        ? null
                        : new DocRef(PipelineDoc.TYPE, parent))
                .pipelineData(data.build())
                .build());
    }

    /// Gives the template a reference loader for the feed.
    void reference(final String feed) {
        pipelines.computeIfPresent(TEMPLATE, (uuid, doc) -> doc.copy().pipelineData(new PipelineDataBuilder(
                        doc.getPipelineData())
                        .addPipelineReference(new PipelineReference("translationFilter", "pipelineReference",
                                new DocRef(PipelineDoc.TYPE, "loader", "Reference Loader"),
                                new DocRef("Feed", "f", feed), "Reference"))
                        .build())
                .build());
    }

    /// Streams of `count` records each, every record giving the output from the translation filter.
    void records(final int count, final String output) {
        stepper = (stream, at) -> at.getRecordIndex() < count
                ? step(stream, at.getPartIndex(), at.getRecordIndex(), output)
                : null;
    }

    private SteppingWalks.Walk walk(final long streamId, final Map<String, String> code) {
        opened.add(Map.entry(streamId, code == null
                ? Map.of()
                : code));
        final SteppingWalks.Walk walk = Mockito.mock(SteppingWalks.Walk.class);
        Mockito.when(walk.first()).thenAnswer(inv -> {
            steps.add("FIRST");
            return orNone(stepper.apply(streamId, new StepLocation(streamId, 0L, 0L)));
        });
        Mockito.when(walk.last()).thenAnswer(inv -> {
            steps.add("LAST");
            return orNone(stepper.apply(streamId, new StepLocation(streamId, 0L, 9L)));
        });
        Mockito.when(walk.forward(any())).thenAnswer(inv -> {
            final StepLocation from = inv.getArgument(0);
            steps.add("FORWARD " + from.getRecordIndex());
            return orNone(stepper.apply(streamId, new StepLocation(streamId, from.getPartIndex(),
                    from.getRecordIndex() + 1)));
        });
        Mockito.when(walk.refresh(any())).thenAnswer(inv -> {
            final StepLocation at = inv.getArgument(0);
            steps.add("REFRESH " + at.getPartIndex() + ":" + at.getRecordIndex());
            return orNone(stepper.apply(streamId, at));
        });
        walks.add(walk);
        return walk;
    }

    /// @return A step that found the record, its translation filter giving the output.
    static SteppingResult step(final long stream, final long part, final long record, final String output) {
        return step(stream, part, record, output, null);
    }

    /// @return A step that found the record, its translation filter giving the output, and the schema filter
    /// meeting the error (when not null).
    static SteppingResult step(final long stream,
                               final long part,
                               final long record,
                               final String output,
                               final String schemaError) {
        final Map<String, SharedElementData> elements = new LinkedHashMap<>();
        elements.put("translationFilter", new SharedElementData("<records/>", output, null, false, false, true));
        if (schemaError != null) {
            final Indicators indicators = new Indicators();
            indicators.add(new StoredError(Severity.ERROR, new DefaultLocation(5, 6), new ElementId("schemaFilter"),
                    schemaError));
            elements.put("schemaFilter", new SharedElementData(null, null, indicators, false, false, false));
        }
        return new SteppingResult("s", null, null, new StepLocation(stream, part, record),
                new SharedStepData(null, elements), null, true, null, false, true);
    }

    /// @return A step that found no record, with the error on the translation filter (when not null).
    static SteppingResult none(final String fatal) {
        final Map<String, SharedElementData> elements = new LinkedHashMap<>();
        if (fatal != null) {
            final Indicators indicators = new Indicators();
            indicators.add(new StoredError(Severity.FATAL_ERROR, new DefaultLocation(61, 5),
                    new ElementId("translationFilter"), fatal));
            elements.put("translationFilter", new SharedElementData(null, null, indicators, false, false, false));
        }
        return new SteppingResult("s", null, null, null, new SharedStepData(null, elements), null, false, null,
                false, true);
    }

    private static SteppingResult orNone(final SteppingResult result) {
        return result == null
                ? none(null)
                : result;
    }

    /// @return The session's limits with a query time limit, for a long call.
    static SessionLimits queryTimeLimit(final Duration limit) {
        final AiWorkspaceConfig config = Mockito.mock(AiWorkspaceConfig.class);
        Mockito.when(config.getQueryTimeLimit()).thenReturn(StroomDuration.of(limit));
        return SessionLimits.of(config);
    }
}
