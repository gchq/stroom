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
import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.AgentKnowledge;
import stroom.ai.impl.tools.ChatModelNames;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.ai.impl.tools.validation.XsltCompileCheck;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.shared.BuildInfo;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// The translation tools' world (as the Stroom MCP server's tests patch theirs): the in-memory explorer of
/// [BuildWorld], whose XSLTs, text converters and dictionaries are saved as Stroom saves them; Stroom holding the
/// event-logging 4.1.0 schema (the test resource); no AGENTS docs unless a test gives some; every XSLT compiling
/// unless a test says otherwise; and the translation tools wired on them.
final class TranslationWorld {

    /// The event-logging version this Stroom holds.
    static final String VERSION = "4.1.0";
    /// Its schema.
    static final EventSchema SCHEMA = schema();

    final BuildWorld world = new BuildWorld();
    final FakeBuildFacts facts = new FakeBuildFacts();
    final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
    final DocFinder docFinder = Mockito.mock(DocFinder.class);
    final XsltCompileCheck compileCheck = Mockito.mock(XsltCompileCheck.class);
    final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    final StreamReader streamReader = Mockito.mock(StreamReader.class);
    final SampleStreamTexts sampleStreamTexts = Mockito.mock(SampleStreamTexts.class);
    final PipelineStepping stepping = Mockito.mock(PipelineStepping.class);
    final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    final Consent consent = new Consent(() -> auditLog);
    final BuildMemory memory = new BuildMemory();
    final BuildPlan plan = new BuildPlan(facts, world.guard(), consent, memory);
    final CheckXsltTool checkXslt = new CheckXsltTool(heldSchemas, () -> docFinder, world.documentAccess,
            compileCheck);
    final EventVersions eventVersions = new EventVersions(heldSchemas, consent, memory, world::guard,
            world::records);
    final KeptCode keptCode = new KeptCode(eventVersions);
    final HandEditGate handEditGate = new HandEditGate(keptCode, consent);
    // The model each chat runs on: none unless a test says.
    final ChatModelNames chatModelNames = Mockito.mock(ChatModelNames.class);
    final XsltHistory history = new XsltHistory(securityContext, () -> new BuildInfo(0L, "7.13.0", 0L),
            () -> chatModelNames, Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC));
    final RequiredImports requiredImports = new RequiredImports(agentInstructions, world::guard, () -> docFinder,
            world.documentAccess);
    final LookupAgreements lookups = new LookupAgreements(consent);
    final TranslationDocs docs = new TranslationDocs(world.guard(), world.documentAccess, () -> plan, checkXslt,
            requiredImports, eventVersions, handEditGate, history, lookups, () -> Mockito.mock(AgentKnowledge.class));
    final TranslationBuilds builds = new TranslationBuilds(world.guard(), world.documentAccess, docs, compileCheck,
            streamReader);
    final SampleProfiling sampleProfiling = new SampleProfiling.Profiled();
    // No feed translated now, unless a test says what is.
    final CurrentTranslations currentTranslations = Mockito.mock(CurrentTranslations.class);
    final List<String> versions = new ArrayList<>(List.of(VERSION));
    // What the user answers each form with, by its question's start (`yes` or `no` for a confirmation or an
    // approval, else the choice picked); none: no forms.
    final Map<String, String> answers = new LinkedHashMap<>();
    // Each form's message, as the user was shown it, in order.
    final List<String> asked = new ArrayList<>();
    // The same messages, as the consent tests read them.
    final List<String> shown = asked;
    boolean forms;
    // As in the chat: a form's first asking stops the call (the run waits for the user), and the same call, made
    // again, gets the answer.
    boolean waitsForUser;
    private final Set<String> waited = new HashSet<>();
    private final ChatWorkspace chatWorkspace = Mockito.mock(ChatWorkspace.class);
    private final Map<String, String> files = new LinkedHashMap<>();

    TranslationWorld() {
        Mockito.when(heldSchemas.eventVersions()).thenAnswer(inv -> {
            if (versions.isEmpty()) {
                throw new stroom.ai.impl.tools.ToolRefusal(HeldSchemas.NO_EVENT_LOGGING);
            }
            return List.copyOf(versions);
        });
        Mockito.when(heldSchemas.eventSchema(anyString())).thenReturn(SCHEMA);
        Mockito.when(heldSchemas.translationVersion(any(), any())).thenAnswer(inv -> {
            final String kept = inv.getArgument(0);
            if (kept != null) {
                return kept;
            }
            final String written = HeldSchemas.eventLoggingVersion(inv.getArgument(1));
            return written != null
                    ? written
                    : versions.getFirst();
        });
        Mockito.when(heldSchemas.systemIds()).thenReturn(List.of());
        Mockito.when(compileCheck.compile(anyString())).thenReturn(new XsltCompileCheck.Compiled(List.of()));
        Mockito.when(securityContext.getUserIdentityForAudit()).thenReturn("jane");
        Mockito.when(agentInstructions.importsRequired(any(), anyString(), any(), any())).thenReturn(List.of());
        Mockito.when(agentInstructions.applicableInstructions(any(), anyString(), any()))
                .thenReturn(Map.of("instructions", List.of()));
        Mockito.when(docFinder.findByName(eq(XsltDoc.TYPE), anyString(), anyBoolean())).thenReturn(List.of());
        wireWorkspace();
    }

    /// @return build_translation_xslt, wired on the world.
    BuildTranslationXsltTool buildTool() {
        return new BuildTranslationXsltTool(docs, builds, eventVersions, requiredImports, consent,
                sampleStreamTexts, stepping, agentInstructions, () -> auditLog, currentTranslations,
                Clock.systemUTC());
    }

    /// @return draft_translation_mapping, wired on the world.
    DraftTranslationMappingTool draftTool() {
        return new DraftTranslationMappingTool(eventVersions, sampleStreamTexts, builds, sampleProfiling,
                currentTranslations);
    }

    /// @return save_xslt, wired on the world, with no plans drafted.
    SaveXsltTool saveXsltTool() {
        return saveXsltTool(Map.of());
    }

    /// @param drafted The plans drafted in the session, by index name.
    /// @return save_xslt, wired on the world, with the plans drafted given.
    SaveXsltTool saveXsltTool(final Map<String, FieldPlan> drafted) {
        final IndexingMemory memory = Mockito.mock(IndexingMemory.class);
        Mockito.when(memory.drafted(any())).thenReturn(drafted);
        return new SaveXsltTool(docs, requiredImports, memory, eventVersions, world.guard());
    }

    /// @return The context of a call from an MCP client, with a workspace, that shows forms only when [#forms]
    /// is set (answering each from [#answers], by the start of its question).
    ToolCallContext context() {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-session-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                .userQuestions(new UserQuestions() {
                    @Override
                    public boolean canAsk() {
                        return forms;
                    }

                    @Override
                    public ElicitResult ask(final String toolName,
                                            final String callId,
                                            final String intro,
                                            final Map<String, Object> form) {
                        asked.add(intro);
                        if (waitsForUser && waited.add(callId)) {
                            throw new AiChatRunManager.WaitingForUserException("Waiting for " + callId);
                        }
                        return answers.entrySet().stream()
                                .filter(answer -> intro.startsWith(answer.getKey()))
                                .findFirst()
                                .map(answer -> new ElicitResult(ElicitResult.Action.ACCEPT, isYesOrNo(form)
                                        ? Map.of("value", !"no".equals(answer.getValue()))
                                        : Map.of("choice", answer.getValue(), "value", true)))
                                .orElse(new ElicitResult(ElicitResult.Action.DECLINE, null));
                    }
                })
                .workspace(new SessionWorkspace(chatWorkspace, 1))
                .build();
    }

    /// @return The result of a call of the tool with the arguments, as JSON, its text as `error` when refused.
    Map<String, Object> call(final stroom.ai.impl.tools.StroomTool tool, final Object arguments) {
        final ToolResult result = tool.execute(new ToolCall("1", tool.definition().name(),
                JsonUtil.writeValueAsString(arguments, false)), context());
        if (result.isError()) {
            return Map.of("error", result.text());
        }
        return PortedToolCalls.json(result.text());
    }

    /// @return A document made in a build by the tools.
    DocRef inBuild(final String build, final String type, final String name) {
        return world.inBuild(build, type, name, null);
    }

    /// @return The XSLT as it is now.
    XsltDoc xslt(final String uuid) {
        return (XsltDoc) world.docs.get(uuid);
    }

    /// Changes an XSLT's code as a person does in Stroom's editor.
    void editByHand(final String uuid, final Function<String, String> edit) {
        final XsltDoc doc = xslt(uuid);
        world.docs.put(uuid, doc.copy().data(edit.apply(doc.getData())).updateUser("bob").build());
    }

    /// @return The files the session's workspace holds, by path.
    Map<String, String> files() {
        return files;
    }

    /// @return A mapping (`tests/test_xsltgen.py`'s `mapping()`): a Data Splitter's records, logons and the rest.
    static Map<String, Object> mapping() {
        return JsonUtil.readValue(MAPPING, Map.class);
    }

    /// @return The same, read.
    static TranslationMapping parsed() {
        return TranslationMapping.parse(mapping());
    }

    /// @return The mapping as the tools keep it.
    static Map<String, Object> kept(final TranslationMapping mapping) {
        return mapping.toJson(DumpMode.COMPACT);
    }

    /// The mapping of `tests/test_xsltgen.py`: BASE, LOGON and a rule for the rest.
    static final String MAPPING = """
            {"input": "data_splitter", "unmatched": "skip",
             "common": [{"path": "EventTime/TimeCreated", "field": "time"},
                        {"path": "EventSource/System/Name", "value": "Acme VPN"},
                        {"path": "EventSource/System/Environment", "value": "Dev"},
                        {"path": "EventSource/Generator", "value": "vpnd"},
                        {"path": "EventSource/Device/HostName", "field": "host", "default": "unknown"},
                        {"path": "EventSource/User/Id", "field": "user"}],
             "events": [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                         "fields": [{"path": "EventDetail/TypeId", "value": "Logon"},
                                    {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                                    {"path": "EventDetail/Authenticate/User/Id", "field": "user"},
                                    {"path": "EventDetail/Authenticate/Outcome/Success", "field": "result",
                                     "map": {"ok": "true", "fail": "false"}},
                                    {"path": "EventDetail/Authenticate/Data", "data_name": "session",
                                     "field": "sid"}]},
                        {"name": "other", "fields": [{"path": "EventDetail/TypeId", "field": "action"},
                                                     {"path": "EventDetail/Unknown/Data", "data_name": "action",
                                                      "field": "action"}]}]}""";

    // The session's workspace, as files kept in memory.
    private void wireWorkspace() {
        Mockito.when(chatWorkspace.write(Mockito.anyInt(), any(WorkspacePath.class), anyString(), any()))
                .thenAnswer(inv -> {
                    files.put(String.valueOf((Object) inv.getArgument(1)), inv.getArgument(2));
                    return files.size();
                });
        Mockito.when(chatWorkspace.find(Mockito.anyInt(), any(WorkspacePath.class))).thenAnswer(inv ->
                files.containsKey(String.valueOf((Object) inv.getArgument(1)))
                        ? Optional.of(1)
                        : Optional.empty());
        Mockito.when(chatWorkspace.readLines(Mockito.anyInt(), any(WorkspacePath.class), Mockito.anyLong(),
                Mockito.anyInt())).thenAnswer(inv ->
                List.of(files.get(String.valueOf((Object) inv.getArgument(1))).split("\n", -1)));
    }

    // Whether a form is a confirmation's or an approval's: a yes or no.
    private static boolean isYesOrNo(final Map<String, Object> form) {
        return form.get("properties") instanceof final Map<?, ?> properties && properties.containsKey("value");
    }

    private static EventSchema schema() {
        try (final InputStream in = TranslationWorld.class.getResourceAsStream("event-logging-v4.1.0.xsd")) {
            return EventSchema.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
