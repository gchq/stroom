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

import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.pipeline.shared.XsltDoc;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XsltTransformer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;

/// Edits made by hand in Stroom's editor survive the agent's next change (the Stroom MCP server's
/// `tests/test_hand_edits.py`, its tool cases; the pure `utils/handedit.py` ones are `TestHandEdits`'). Asked for by
/// the user: an indexing XSLT generated from its plan, edited by hand to write another ECS field
/// (http.request.body.bytes), was regenerated from the plan at the agent's next change, and the edit was gone. Now a
/// save whose code undoes an edit made since the server saved the XSLT is refused, saying what the edit changed,
/// until it is carried into the plan (or the user says to drop it).
class TestHandEditGate {

    private static final List<Map<String, Object>> FIELDS = List.of(
            field("StreamId", "id", "@StreamId"),
            field("EventId", "id", "@EventId"),
            field("@timestamp", "date", "EventTime/TimeCreated"),
            field("user.name", "keyword", "EventSource/User/Id"),
            field("url.original", "keyword", "EventDetail/*/Resource/URL"),
            field("http.request.method", "keyword", "EventDetail/*/Resource/HTTPMethod"));
    private static final Map<String, Object> ACTION = field("event.action", "keyword", "EventDetail/*/Action");
    private static final Map<String, Object> BYTES = field("http.request.body.bytes", "long",
            "EventDetail/*/Resource/InboundSize");
    private static final Map<String, Object> VIEW_BYTES = field("http.request.body.bytes", "long",
            "EventDetail/View/Resource/InboundSize");
    // The user's edit, in their own style: no xsl:if, and inside the request object the generator wrote.
    private static final String METHOD = "<string key=\"method\"><xsl:value-of "
                                         + "select=\"EventDetail/*/Resource/HTTPMethod\"/></string>";
    private static final String EDITED_IN = METHOD + "<map key=\"body\"><number key=\"bytes\"><xsl:value-of "
                                            + "select=\"EventDetail/View/Resource/InboundSize\"/></number></map>";
    private static final String EVENTS = """
            <Events xmlns="event-logging:3"><Event StreamId="7" EventId="1">
            <EventTime><TimeCreated>2026-10-11T09:00:00.000Z</TimeCreated></EventTime>
            <EventSource><User><Id>alice</Id><Name>Alice Smith</Name></User></EventSource>
            <EventDetail><View><Action>View</Action><Resource><URL>https://intranet/a</URL><HTTPMethod>GET</HTTPMethod>
            <InboundSize>512</InboundSize></Resource></View></EventDetail></Event></Events>""";
    private static final String BUILD = "acme-v1";
    private static final String NAME = "ACME-INDEX-XSLT";

    private final TranslationWorld world = new TranslationWorld();
    private final SaveXsltTool tool = world.saveXsltTool();
    private final String uuid = world.inBuild(BUILD, XsltDoc.TYPE, NAME).getUuid();

    @Test
    void aFieldAddedByHandIsKeptUntilCarriedIntoThePlan() {
        save(plan());
        editByHand(METHOD, EDITED_IN);
        final String edited = code();
        // The agent's next change, from the plan as it was saved: the edit would be gone. Refused, saying what it
        // was.
        final String said = refused(save(plan(ACTION), Map.of("change", "event.action, asked for by the user")));
        assertThat(said).contains("was edited by hand since the server saved it (by jane)")
                .contains("no longer writes number bytes")
                .contains("no longer reads EventDetail/View/Resource/InboundSize")
                .contains("+ <number key=\"bytes\">")
                .contains("carry it into the index plan")
                .contains("discard_hand_edit");
        assertThat(code()).isEqualTo(edited);
        // Carried into the plan, in the generator's own style, the change saves, and the field is still written.
        assertSaved(save(plan(ACTION, VIEW_BYTES)));
        final String out = run(code());
        assertThat(out).contains("<number key=\"bytes\">512</number>").contains("<string key=\"action\">View</string>");
        // Saved since: the next change is the agent's own again.
        assertSaved(save(plan(ACTION)));
        assertThat(code()).doesNotContain("bytes");
    }

    @Test
    void aFieldRemovedOrASourceChangedByHandIsKeptToo() {
        save(plan());
        final String url = code();
        final int start = url.indexOf("<xsl:if test=\"EventDetail/*/Resource/URL\">");
        editByHand(url.substring(start, url.indexOf("</xsl:if>", start) + "</xsl:if>".length()), "");
        assertThat(refused(save(plan(ACTION)))).containsPattern("writes map url again.*writes string original again");
        final List<Map<String, Object>> withoutUrl = FIELDS.stream()
                .filter(f -> !"url.original".equals(f.get("name")))
                .toList();
        assertSaved(save(plan(withoutUrl, ACTION)));
        assertThat(code()).doesNotContain("Resource/URL");
        // A field's source changed by hand: the same name written, from another element.
        editByHand("\"EventSource/User/Id\"", "\"EventSource/User/Name\"");
        editByHand("\"EventSource/User/Id\"", "\"EventSource/User/Name\"");
        assertThat(refused(save(plan(withoutUrl)))).containsPattern(
                "no longer reads EventSource/User/Name.*reads EventSource/User/Id again");
        assertSaved(save(plan(withSource(withoutUrl, "user.name", "EventSource/User/Name"))));
        assertThat(run(code())).contains("<string key=\"name\">Alice Smith</string>");
    }

    @Test
    void theUserCanSayToDropTheirEdit() {
        save(plan());
        editByHand(METHOD, EDITED_IN);
        assertSaved(save(plan(ACTION), Map.of("discard_hand_edit", true)));
        assertThat(code()).doesNotContain("bytes");
    }

    @Test
    void noEditByHandNoRefusal() {
        // Changes the agent makes one after another are its own: only an edit since the server's last save is kept.
        assertSaved(save(plan()));
        assertSaved(save(plan(ACTION)));
        assertSaved(save(plan(BYTES)));
        assertThat(code()).doesNotContain("action").contains("bytes");
    }

    @Test
    void anXsltWrittenByTheAgentKeepsAnEditMadeByHandSince() {
        // No plan to regenerate from: everything the XSLT writes and reads now must still be written and read.
        final String hand = SaveXsltTool.generated(stroom.ai.impl.tools.indexing.FieldPlan.fromJson(plan()));
        assertSaved(saveCode(hand));
        editByHand(METHOD, EDITED_IN);
        assertThat(refused(saveCode(hand))).contains("no longer writes number bytes");
        assertSaved(saveCode(code().replace("<map key=\"url\">", "<map key=\"url\"><!-- kept -->")));
    }

    @Test
    void aTranslationEditedByHandIsKeptThroughBuildTranslationXslt() {
        final TranslationMapping mapping = TranslationWorld.parsed();
        final String generated = generate(mapping);
        assertSaved(update(generated, mapping, false, null));
        editByHand("</Authenticate>", "<Data Name=\"device_class\" Value=\"{data[@name='class']/@value}\"/>"
                                      + "</Authenticate>");
        final ToolRefusal refused = refusal(() -> update(generated, mapping, false, null));
        assertThat(refused.getMessage()).contains("no longer writes Data device_class")
                .contains("rebuild_mapping uuid='" + uuid + "'");
        // rebuild_mapping carries the edit into the mapping, proven on the sample: its save is not refused.
        assertSaved(update(generated, mapping, true, null));
    }

    // A field the agent's change changes that the user edited by hand too: theirs to decide, field by field (asked
    // for by the user: "keep their hand edit or overwrite it with the proposed field").

    @Test
    void aFieldChangedByHandAndByTheAgentIsTheUsersToDecide() {
        save(plan());
        editByHand("\"EventSource/User/Id\"", "\"EventSource/User/Name\"");
        editByHand("\"EventSource/User/Id\"", "\"EventSource/User/Name\"");
        final String edited = code();
        // The agent's change: user.name from the user's email. Asked, not refused or saved.
        final Map<String, Object> asked = save(plan(email()));
        assertThat(asked).containsEntry("status", "needs_guidance").doesNotContainKey("uuid");
        assertThat(code()).isEqualTo(edited);
        // First, one question: overwrite the XSLT with the change, or decide field by field (asked for by the
        // user).
        final Map<?, ?> upfront = (Map<?, ?>) asked.get("hand_edit");
        assertThat((String) upfront.get("question")).contains("changes 1 field the edit changed too: user.name");
        assertThat(upfront.get("options")).isEqualTo(Map.of("overwrite", "Overwrite the XSLT with the change",
                "per_field", "Decide field by field"));
        assertThat((String) asked.get("hint")).contains("hand_edit_choices={'*': 'overwrite'}");
        final List<?> collisions = (List<?>) asked.get("hand_edit_collisions");
        assertThat(collisions).hasSize(1);
        final Map<?, ?> question = (Map<?, ?>) collisions.getFirst();
        assertThat(question.get("field")).isEqualTo("user.name");
        assertThat(question.get("options")).isEqualTo(Map.of("keep", "Keep my hand edit", "overwrite",
                "Use the proposed field"));
        assertThat((String) question.get("question")).contains("added EventSource/User/Name").contains("by jane")
                .contains("proposes user.name (keyword) from EventSource/User/Email")
                .contains("the plan had user.name (keyword) from EventSource/User/Id");
        // They keep their edit: the change to it is left out, and the edit carried, before it saves.
        final String kept = refused(save(plan(email()), Map.of("hand_edit_choices", Map.of("user.name", "keep"))));
        assertThat(kept).contains("The user keeps their edit of user.name")
                .contains("no longer reads EventSource/User/Name");
        assertSaved(save(plan(withSource(FIELDS, "user.name", "EventSource/User/Name"), ACTION)));
        assertThat(run(code())).contains("<string key=\"name\">Alice Smith</string>");
    }

    @Test
    void theUserOverwritesTheirEditWithTheProposedField() {
        save(plan());
        editByHand("\"EventSource/User/Id\"", "\"EventSource/User/Name\"");
        editByHand("\"EventSource/User/Id\"", "\"EventSource/User/Name\"");
        assertThat(save(plan(email()))).containsEntry("status", "needs_guidance");
        assertSaved(save(plan(email()), Map.of("hand_edit_choices", Map.of("user.name", "overwrite"))));
        assertThat(code()).contains("EventSource/User/Email").doesNotContain("User/Name");
    }

    @Test
    void onlyTheCollidingFieldIsOverwrittenTheRestOfTheEditIsKept() {
        save(plan());
        editByHand("\"EventSource/User/Id\"", "\"EventSource/User/Name\"");
        editByHand("\"EventSource/User/Id\"", "\"EventSource/User/Name\"");
        editByHand(METHOD, EDITED_IN);
        // Asked in a form the client shows: the user's answer comes back in the same call.
        world.forms = true;
        world.answers.put("XSLT '" + NAME + "' was edited by hand", "Decide field by field");
        world.answers.put("XSLT '" + NAME + "', field user.name", "Use the proposed field");
        final String said = refused(save(plan(email())));
        assertThat(choicesAsked()).hasSize(2);
        assertThat(choicesAsked().getLast()).contains("field user.name");
        assertThat(said).contains("no longer writes number bytes");
        assertThat(said.split("What the edit")[0]).doesNotContain("User/Name");
        // Answered once for the XSLT as it is: carrying the rest isn't asked again.
        assertSaved(save(plan(email(), VIEW_BYTES)));
        assertThat(choicesAsked()).hasSize(2);
        final String out = run(code());
        assertThat(out).contains("<number key=\"bytes\">512</number>").doesNotContain("Alice Smith");
    }

    @Test
    void aConstantChangedByHandAndInTheMappingIsAskedAbout() {
        final TranslationMapping mapping = TranslationWorld.parsed();
        final TranslationMapping prod = withCommonValue("EventSource/System/Environment", "Prod");
        assertSaved(update(generate(mapping), mapping, false, null));
        editByHand("<Environment>Dev</Environment>", "<Environment>Test</Environment>");
        final Map<String, Object> asked = update(generate(prod), prod, false, null);
        final List<?> collisions = (List<?>) asked.get("hand_edit_collisions");
        assertThat(collisions).hasSize(1);
        final Map<?, ?> question = (Map<?, ?>) collisions.getFirst();
        assertThat(question.get("field")).isEqualTo("common: EventSource/System/Environment");
        assertThat((String) question.get("question")).contains("added Environment = Test");
        // Another change, to a field the edit left alone: no question, but the edit must still be carried.
        final TranslationMapping other = withCommonValue("EventSource/Generator", "vpnd2");
        assertThat(refusal(() -> update(generate(other), other, false, null)).getMessage())
                .contains("no longer writes Environment = Test");
        assertSaved(update(generate(prod), prod, false, Map.of("common: EventSource/System/Environment",
                "overwrite")));
        assertThat(code()).contains("<Environment>Prod</Environment>");
    }

    @Test
    void aKeyRenamedByHandIsLinkedToItsField() {
        // Asked for by the user: a key renamed by hand (method -> verb) is still the field it was: where the agent's
        // change is to that field, the user is asked about it, not just refused.
        save(plan());
        editByHand("<string key=\"method\">", "<string key=\"verb\">");
        final List<Map<String, Object>> upper = withSource(FIELDS, "http.request.method",
                "upper-case(EventDetail/*/Resource/HTTPMethod)");
        final Map<String, Object> asked = save(plan(upper));
        final List<?> collisions = (List<?>) asked.get("hand_edit_collisions");
        assertThat(collisions).hasSize(1);
        final Map<?, ?> question = (Map<?, ?>) collisions.getFirst();
        assertThat(question.get("field")).isEqualTo("http.request.method");
        assertThat((String) question.get("question")).contains("added string verb");
        // Kept: the change to it left out, the rename carried into the plan.
        assertThat(refused(save(plan(upper), Map.of("hand_edit_choices", Map.of("http.request.method", "keep")))))
                .contains("The user keeps their edit of http.request.method");
        final List<Map<String, Object>> verb = FIELDS.stream()
                .map(f -> "http.request.method".equals(f.get("name"))
                        ? with(f, "name", "http.request.verb")
                        : f)
                .toList();
        assertSaved(save(plan(verb)));
        assertThat(run(code())).contains("<string key=\"verb\">GET</string>");
    }

    @Test
    void aKeyRenamedByHandWithNoChangeToItsFieldIsCarried() {
        save(plan());
        editByHand("<string key=\"method\">", "<string key=\"verb\">");
        assertThat(refused(save(plan(ACTION)))).containsPattern("no longer writes string verb.*writes string method "
                                                                + "again");
    }

    @Test
    void theUserOverwritesTheXsltInOneStep() {
        // Asked for by the user: one choice up front to overwrite the XSLT, rather than one a field. Every hand edit
        // goes, the fields the change changes and the rest alike.
        save(plan());
        editByHand("\"EventSource/User/Id\"", "\"EventSource/User/Name\"");
        editByHand("\"EventSource/User/Id\"", "\"EventSource/User/Name\"");
        editByHand(METHOD, EDITED_IN);
        world.forms = true;
        world.answers.put("XSLT '" + NAME + "' was edited by hand", "Overwrite the XSLT with the change");
        assertSaved(save(plan(email())));
        assertThat(choicesAsked()).hasSize(1);
        assertThat(choicesAsked().getFirst()).contains("(the edit changed 3 other things as well)");
        assertThat(code()).contains("EventSource/User/Email").doesNotContain("User/Name").doesNotContain("bytes");
    }

    @Test
    void overwritingInOneStepFromTheChat() {
        save(plan());
        editByHand("\"EventSource/User/Id\"", "\"EventSource/User/Name\"");
        editByHand("\"EventSource/User/Id\"", "\"EventSource/User/Name\"");
        assertThat(save(plan(email()))).containsEntry("status", "needs_guidance");
        assertSaved(save(plan(email()), Map.of("hand_edit_choices", Map.of("*", "overwrite"))));
        assertThat(code()).contains("EventSource/User/Email");
    }

    // --- helpers ---

    private Map<String, Object> save(final Map<String, Object> indexPlan) {
        return save(indexPlan, Map.of());
    }

    private Map<String, Object> save(final Map<String, Object> indexPlan, final Map<String, Object> more) {
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("build", BUILD);
        args.put("name", NAME);
        args.put("index_plan", indexPlan);
        args.put("uuid", uuid);
        args.putAll(more);
        return world.call(tool, args);
    }

    private Map<String, Object> saveCode(final String code) {
        return world.call(tool, Map.of("build", BUILD, "name", NAME, "code", code, "uuid", uuid));
    }

    private Map<String, Object> update(final String code,
                                       final TranslationMapping mapping,
                                       final boolean discard,
                                       final Map<String, String> choices) {
        return world.docs.updateXslt(new ToolCall("1", BuildTranslationXsltTool.NAME, "{}"), world.context(), uuid,
                code, null, TranslationDocs.Source.of(mapping), null, discard, choices);
    }

    private static ToolRefusal refusal(final Runnable save) {
        try {
            save.run();
        } catch (final ToolRefusal e) {
            return e;
        }
        throw new AssertionError("Not refused");
    }

    private static String refused(final Map<String, Object> result) {
        assertThat(result).containsKey("error");
        return String.valueOf(result.get("error"));
    }

    private static void assertSaved(final Map<String, Object> result) {
        assertThat(result).doesNotContainKey("error").doesNotContainKey("status").containsKey("uuid");
    }

    // The questions the user was asked in forms, in turn.
    private List<String> choicesAsked() {
        final List<String> asked = new ArrayList<>();
        Mockito.mockingDetails(world.auditLog).getInvocations().stream()
                .filter(invocation -> "processed".equals(invocation.getMethod().getName())
                                      && "choice".equals(invocation.getArgument(2)))
                .forEach(invocation -> asked.add(String.valueOf((Object) invocation.getArgument(3))));
        return asked;
    }

    private String code() {
        return world.xslt(uuid).getData();
    }

    // As the user edits in Stroom's editor: old as they see it, matched whatever the whitespace between its
    // elements.
    private void editByHand(final String old, final String now) {
        final String pattern = Pattern.compile("(?<=>)\\s*(?=<)").splitAsStream(old)
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s*"));
        final XsltDoc doc = world.xslt(uuid);
        final Matcher matcher = Pattern.compile(pattern).matcher(doc.getData());
        assertThat(matcher.find()).as(old).isTrue();
        final String data = doc.getData().substring(0, matcher.start()) + now + doc.getData().substring(matcher.end());
        world.world.docs.put(uuid, doc.copy().data(data).updateUser("jane").build());
    }

    private static String generate(final TranslationMapping mapping) {
        return XsltGenerator.generate(mapping, TranslationWorld.SCHEMA, TranslationWorld.VERSION).xslt();
    }

    @SuppressWarnings("unchecked")
    private static TranslationMapping withCommonValue(final String path, final String value) {
        final Map<String, Object> mapping = TranslationWorld.mapping();
        final List<Map<String, Object>> common = new ArrayList<>();
        for (final Object entry : (List<?>) mapping.get("common")) {
            final Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) entry);
            if (path.equals(copy.get("path"))) {
                copy.put("value", value);
            }
            common.add(copy);
        }
        mapping.put("common", common);
        return TranslationMapping.parse(mapping);
    }

    private static List<Map<String, Object>> email() {
        return withSource(FIELDS, "user.name", "EventSource/User/Email");
    }

    private static List<Map<String, Object>> withSource(final List<Map<String, Object>> fields,
                                                        final String name,
                                                        final String source) {
        return fields.stream()
                .map(f -> name.equals(f.get("name"))
                        ? with(f, "source", source)
                        : f)
                .toList();
    }

    private static Map<String, Object> with(final Map<String, Object> field, final String key, final Object value) {
        final Map<String, Object> copy = new LinkedHashMap<>(field);
        copy.put(key, value);
        return copy;
    }

    @SafeVarargs
    private static Map<String, Object> plan(final Map<String, Object>... more) {
        return plan(FIELDS, more);
    }

    @SafeVarargs
    private static Map<String, Object> plan(final List<Map<String, Object>> fields, final Map<String, Object>... more) {
        final List<Map<String, Object>> all = new ArrayList<>(fields);
        all.addAll(List.of(more));
        final Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("backend", "elasticsearch");
        plan.put("index_name", "ecs-acme-v1");
        plan.put("time_field", "@timestamp");
        plan.put("convention", "ecs");
        plan.put("fields", all);
        return plan;
    }

    private static Map<String, Object> field(final String name, final String type, final String source) {
        final Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("type", type);
        field.put("source", source);
        return field;
    }

    // The XSLT run over the sample Events.
    private static String run(final String xslt) {
        try {
            final Processor processor = new Processor(false);
            final XsltTransformer transformer = processor.newXsltCompiler()
                    .compile(new StreamSource(new StringReader(xslt))).load();
            transformer.setSource(new StreamSource(new StringReader(EVENTS)));
            final StringWriter out = new StringWriter();
            final Serializer serializer = processor.newSerializer(out);
            transformer.setDestination(serializer);
            transformer.transform();
            return out.toString();
        } catch (final SaxonApiException e) {
            throw new IllegalStateException(e);
        }
    }
}
