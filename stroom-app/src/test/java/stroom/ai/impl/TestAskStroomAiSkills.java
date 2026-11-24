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

package stroom.ai.impl;

import stroom.ai.api.AiService;
import stroom.ai.api.OpenAIModelStore;
import stroom.ai.impl.tools.skills.SkillCatalogue;
import stroom.ai.impl.tools.skills.SkillDoc;
import stroom.ai.impl.tools.skills.SkillInvocations;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatPollRequest;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AiToolCategoryGrant;
import stroom.ai.shared.AiToolPolicy;
import stroom.ai.shared.AiUserActionResult;
import stroom.ai.shared.AnswerAiElicitationRequest;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.docref.DocRef;
import stroom.documentation.impl.DocumentationStore;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.index.shared.LuceneIndexDoc;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.api.DocumentPermissionService;
import stroom.security.shared.DocumentPermission;
import stroom.test.AbstractAiIntegrationTest;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Skills on a real Stroom with only the chat model replaced (a scripted [FakeModelServer]): a skill proposed for a
/// folder with no SKILLS doc and created on apply, with its history entry; a change proposed to that doc, applied to
/// its editor unsaved, and refused once the doc has been saved with other text; the reverse diff for undo after a
/// save; `check_skill` on planted faults; discovery (only the skills the user can read, in the prompt); a template
/// filled and tried; a skill run by name with the slash command, its missing parameter asked in a form; and a broken
/// skill run so stopping, with the likely replacement and the fix offered.
class TestAskStroomAiSkills extends AbstractAiIntegrationTest {

    private static final String SKILL = """
            # Logons by user

            Counts a user's logons over a period.

            ## When to use
            - "how many times did jsmith log on?"
            - "count a user's logons this week"

            ## Parameters
            - user (required): the account.
            - days (default 7): how far back.

            ## Steps
            1. Count them:
               ```json
               {"tool": "run_stroomql", "arguments": {"query": "from Logons where UserId = '{user}' select \
            count()", "out": "/outputs/skills/logons-{days}.md"}}
               ```

            ## Output
            A summary in the chat.
            """;

    @Inject
    private AskStroomAIService askStroomAIService;
    @Inject
    private AiService aiService;
    @Inject
    private OpenAIModelStore modelStore;
    @Inject
    private ExplorerService explorerService;
    @Inject
    private ExplorerNodeService explorerNodeService;
    @Inject
    private DocumentationStore documentationStore;
    @Inject
    private UserActionService userActionService;
    @Inject
    private AiChatRunManager runManager;
    @Inject
    private AiToolPolicyService toolPolicyService;
    @Inject
    private DocumentPermissionService documentPermissionService;
    @Inject
    private SkillCatalogue skillCatalogue;

    private FakeModelServer model;
    private UserRef user;

    @BeforeEach
    void setUp() throws IOException {
        model = FakeModelServer.start();
        user = administrator();
        skillCatalogue.clearCache();
    }

    @AfterEach
    void tearDown() {
        model.close();
    }

    @Test
    void aSkillIsCreatedChangedRefusedOnceSavedSinceAndUndoneByTheReverseDiff() {
        toolPolicy("Everything", AiToolAccess.READ_WRITE, user);
        final DocRef folder = folder("Hunts");
        final DocRef modelRef = asUser(user, this::fakeModel);
        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());

        // A folder with no SKILLS doc: created on apply, with the history entry, as the user.
        model.callTool("activate_skills", "{}")
                .callTool("propose_skill", json(Map.of("folder", folder.getUuid(), "markdown", SKILL)))
                .answer("I've proposed the skill.");
        final AiChatMessage created = proposal(ask(chat, modelRef, "Save this as a skill"), 0);
        final UserActionRecord create = action(created);
        assertThat(create.action()).isEqualTo(UserActions.CREATE_INSTRUCTIONS);
        assertThat(create.name()).isEqualTo(SkillDoc.DOC_NAME);
        assertThat(asUser(user, () -> skillsDocIn(folder))).as("nothing changes until the user applies it").isEmpty();
        final AiUserActionResult done = asUser(user, () -> userActionService.doUserAction(chat.getId(),
                created.getId(), create.id()));
        assertThat(done.isDone()).as(done.getMessage()).isTrue();
        final DocRef skills = asUser(user, () -> skillsDocIn(folder)).orElseThrow();
        final SkillDoc first = SkillDoc.parse(text(skills));
        assertThat(first.isMarked()).isTrue();
        assertThat(first.skills()).extracting(SkillDoc.Skill::name).containsExactly("Logons by user");
        assertThat(first.history()).singleElement().satisfies(entry -> {
            assertThat(entry.version()).isEqualTo(1);
            assertThat(entry.how()).isEqualTo(SkillDoc.HOW_AGENT);
            assertThat(entry.author()).isEqualTo("admin");
            assertThat(entry.body()).isEqualTo(first.bodyHash());
        });

        // A change to it: applied to its editor, unsaved.
        final String changed = SKILL.replace("over a period.", "over a period, by day.");
        model.callTool("propose_skill", json(Map.of("folder", folder.getUuid(), "markdown", changed,
                        "change", "By day")))
                .answer("Proposed.");
        final AiChatMessage change = proposal(ask(chat, modelRef, "Make it by day"), 1);
        final UserActionRecord apply = action(change);
        assertThat(apply.action()).isEqualTo(UserActions.APPLY_DOCUMENTATION_CHANGE);
        assertThat(apply.doc()).isEqualTo(skills);
        assertThat(apply.request()).contains("change: \"By day\"", "over a period, by day.");
        final AiUserActionResult applied = asUser(user, () -> userActionService.doUserAction(chat.getId(),
                change.getId(), apply.id()));
        assertThat(applied.getMessage()).isEqualTo("Applied, not saved");
        assertThat(text(skills)).as("saved only by the user").isEqualTo(first.text());
        // The user saves it.
        save(skills, apply.request());

        // Undo after saving: the reverse diff, from the text kept in the chat, recorded in the history.
        model.callTool("propose_skill", json(Map.of("action", "undo"))).answer("Proposed the undo.");
        final AiChatMessage undo = proposal(ask(chat, modelRef, "Undo that change"), 2);
        final UserActionRecord reverse = action(undo);
        assertThat(reverse.action()).isEqualTo(UserActions.APPLY_DOCUMENTATION_CHANGE);
        final SkillDoc undone = SkillDoc.parse(reverse.request());
        assertThat(undone.body()).isEqualTo(first.body());
        assertThat(undone.history()).extracting(SkillDoc.HistoryEntry::change)
                .containsExactly(first.history().getFirst().change(), "By day", "Undid version 2");

        // Refused once the doc has been saved with other text since it was proposed.
        save(skills, apply.request() + "\nEdited by hand meanwhile.\n");
        final AiUserActionResult refused = asUser(user, () -> userActionService.doUserAction(chat.getId(),
                undo.getId(), reverse.id()));
        assertThat(refused.isDone()).isFalse();
        assertThat(refused.getMessage()).isEqualTo(UserActionService.DOCUMENTATION_CHANGED);
    }

    @Test
    void checkSkillFindsThePlantedFaults() {
        // Every category but administration, so config_properties is one the policies deny.
        asUser(user, () -> toolPolicyService.create(AiToolPolicy.builder()
                .name("All but administration")
                .members(List.of(user))
                .grants(Arrays.stream(AiToolCategory.values())
                        .filter(category -> category != AiToolCategory.ADMINISTRATION)
                        .map(category -> AiToolCategoryGrant.of(category, AiToolAccess.READ_WRITE))
                        .toList())
                .build()));
        final DocRef folder = folder("Faults");
        final String broken = """
                # Broken hunt

                Hunts for something.

                ## When to use
                - "hunt for it"
                - "find the broken thing"

                ## Steps
                1. A tool that isn't one:
                   ```json
                   {"tool": "search_event", "arguments": {}}
                   ```
                2. Off its schema, and from an index that doesn't exist:
                   ```json
                   {"tool": "run_stroomql", "arguments": {"query": "from \\"Gone Index\\" where Host = '{host}'", \
                "limt": 3}}
                   ```
                3. A tool the policies deny:
                   ```json
                   {"tool": "config_properties", "arguments": {}}
                   ```
                """;
        final String saved = SkillDoc.parse("").withSkill(broken, new SkillDoc.HistoryEntry(1, "2026-11-23",
                "admin", SkillDoc.HOW_AGENT, "Broken hunt", "Created", null));
        skillsDoc(folder, saved + "\nA line added by hand.\n");
        final DocRef modelRef = asUser(user, this::fakeModel);
        model.callTool("activate_skills", "{}")
                .callTool("check_skill", json(Map.of("name", "Broken hunt")))
                .answer("It has problems.");

        final List<AiChatMessage> messages = ask(asUser(user, () -> askStroomAIService.createChat()), modelRef,
                "Check the Broken hunt skill");

        assertThat(result(messages, "check_skill").text()).contains("Skill 'Broken hunt' (folder /Faults)",
                "Problem (undeclared_parameter): {host} is used but not listed",
                "Problem (unknown_tool): Step 1's example call uses 'search_event'",
                "Problem (call_off_schema): Step 2's example call doesn't fit run_stroomql's parameters: 'limt' "
                + "isn't a parameter",
                "Problem (missing_data_source): Step 2's example call's query is from 'Gone Index'",
                "Problem (tool_not_allowed): Step 3's example call uses 'config_properties'",
                "Problem (hand_edit): The doc has been changed by hand since its latest history entry (version 1)",
                "propose_skill action=record");
    }

    @Test
    void theModelIsGivenOnlyTheSkillsTheUserCanRead() {
        final UserRef analyst = user("analyst", Set.of());
        toolPolicy("Everything", AiToolAccess.READ_WRITE, analyst);
        final DocRef readable = folder("Readable");
        final DocRef secret = folder("Secret");
        final DocRef readableDoc = skillsDoc(readable, SkillDoc.parse("").withSkill(SKILL, null));
        skillsDoc(secret, SkillDoc.parse("").withSkill(SKILL.replace("# Logons by user", "# Secret hunt")
                .replace("Counts a user's logons", "Hunts in secret"), null));
        asUser(user, () -> {
            documentPermissionService.setPermission(readable, analyst, DocumentPermission.VIEW);
            documentPermissionService.setPermission(readableDoc, analyst, DocumentPermission.VIEW);
        });
        final DocRef modelRef = asUser(user, this::fakeModel);
        asUser(user, () -> documentPermissionService.setPermission(modelRef, analyst, DocumentPermission.USE));
        model.answer("Here you are.");

        final AiChat chat = asUser(analyst, () -> askStroomAIService.createChat());
        asUser(analyst, () -> askStroomAIService.askStroomAi(new AskStroomAiRequest(chat,
                AskStroomAiConfig.builder().modelRef(modelRef).build(), null, "count a user's logons this week",
                AiAgentMode.AGENT)));
        final List<AiChatMessage> messages = answered(analyst, chat.getId(), 0);

        final String system = systemPrompt(model.agentRequests().getFirst());
        assertThat(system).contains(SkillCatalogue.HEADING, "- Logons by user (/Readable): Counts a user's logons "
                                                            + "over a period.")
                .doesNotContain("Secret hunt");
        assertThat(messages).filteredOn(message -> message.getMessageType() == AiMessageType.SKILLS_OFFERED)
                .singleElement()
                .satisfies(message -> assertThat(message.getMessage()).contains("logons by user"));
    }

    @Test
    void aTemplateIsFilledAndTriedBeforeSaving() {
        toolPolicy("Everything", AiToolAccess.READ_WRITE, user);
        final String filled = templateFilled();
        final DocRef modelRef = asUser(user, this::fakeModel);
        model.callTool("activate_skills", "{}")
                .callTool("propose_skill", json(Map.of("action", "template", "name", "who-of-group-did-x")))
                .callTool("propose_skill", json(Map.of("action", "trial", "markdown", filled, "parameters",
                        Map.of("group", "Finance"))))
                .answer("Tried it.");

        final List<AiChatMessage> messages = ask(asUser(user, () -> askStroomAIService.createChat()), modelRef,
                "Make me a skill for who of a group did something, and try it on Finance");

        assertThat(results(messages, "propose_skill").getFirst().text())
                .contains("Fill each [[...]] placeholder", "[[identity index]]");
        assertThat(results(messages, "propose_skill").get(1).text())
                .contains("Trial run of the draft skill 'Who of a group did X' (not saved; kept at "
                          + "/outputs/skills/who-of-a-group-did-x.md) with group=Finance, days=7 (default)",
                        "Follow these steps now", "Group = 'Finance'")
                .doesNotContain("unfilled_placeholder", "[[");
    }

    @Test
    void aSkillRunByNameAsksForItsMissingParameterInAForm() {
        toolPolicy("Everything", AiToolAccess.READ_WRITE, user);
        final DocRef folder = folder("Logons");
        index(folder, "Logons");
        skillsDoc(folder, SkillDoc.parse("").withSkill(SKILL, null));
        final DocRef modelRef = asUser(user, this::fakeModel);
        model.callTool("find_skills", json(Map.of("name", "logons-by-user", "arguments", Map.of("days", "3"))))
                .answer("Counted.");

        final List<AiChatMessage> messages = ask(asUser(user, () -> askStroomAIService.createChat()), modelRef,
                "/skill:logons-by-user days=3", Map.of("user", "jsmith"));

        // The model is given the request to run it, with its tools on from the start.
        final Map<String, Object> first = model.agentRequests().getFirst();
        assertThat(JsonUtil.writeValueAsString(first.get("messages"))).contains(SkillInvocations.EXPANDED_PREFIX
                                                                                 + "The user asked to run the skill "
                                                                                 + "'logons-by-user'");
        assertThat(JsonUtil.writeValueAsString(first.get("tools"))).contains("\"find_skills\"");
        // The missing parameter was asked in a form, and the skill came back with both values.
        assertThat(messages).filteredOn(message -> message.getMessageType() == AiMessageType.ELICITATION)
                .singleElement()
                .satisfies(message -> assertThat(JsonUtil.readValue(message.getMessage(), ElicitationRecord.class)
                        .schema()).extractingByKey("required").isEqualTo(List.of("user")));
        assertThat(results(messages, "find_skills").getLast().text())
                .contains("Run it with user=jsmith, days=3", "UserId = 'jsmith'", "/outputs/skills/logons-3.md");
    }

    @Test
    void aBrokenSkillRunByNameStopsSayingWhatIsBrokenWithTheLikelyReplacementAndTheFixOffered() {
        toolPolicy("Everything", AiToolAccess.READ_WRITE, user);
        final DocRef folder = folder("Hunts");
        // The skill reads the index Logons, renamed Logons-V2 since.
        index(folder, "Logons-V2");
        skillsDoc(folder, SkillDoc.parse("").withSkill(SKILL, null));
        final DocRef modelRef = asUser(user, this::fakeModel);
        model.callTool("find_skills", json(Map.of("name", "logons-by-user", "arguments", Map.of("user", "jsmith"))))
                .answer("It can't run as written: the index Logons is now Logons-V2.");

        final List<AiChatMessage> messages = ask(asUser(user, () -> askStroomAIService.createChat()), modelRef,
                "/skill:logons-by-user user=jsmith");

        assertThat(JsonUtil.writeValueAsString(model.agentRequests().getFirst().get("messages")))
                .contains("unless it says to stop");
        assertThat(results(messages, "find_skills").getLast().text())
                .contains("Stop: don't run this skill", "query is from 'Logons', which doesn't exist",
                        "Likely replacement: 'Logons-V2'", "The user can edit its skills doc",
                        "propose_skill {\"doc\":", "\"action\":\"remove\"",
                        "The skill as saved, to correct (not to run)")
                .doesNotContain("Run it with");
        assertThat(messages).noneMatch(message -> message.getMessageType() == AiMessageType.ELICITATION);
    }

    // The "who of a group did X" template with its placeholders filled.
    private static String templateFilled() {
        return """
                # Who of a group did X

                Finds which members of a group did something in a period.

                ## When to use
                - "which users in team A accessed abc.com?"
                - "who in the Finance group downloaded a file yesterday?"

                ## Parameters
                - group (required): the group's name.
                - days (default 7): how many days back from now.

                ## Steps
                1. Find the group's members:
                   ```json
                   {"tool": "run_stroomql", "arguments": {"query": "from \\"Identity\\" where Group = '{group}' \
                select Account", "out": "/outputs/skills/group-members.md"}}
                   ```
                2. Find the downloads in the period:
                   ```json
                   {"tool": "run_stroomql", "arguments": {"query": "from \\"Proxy\\" where Action = 'Download' \
                and EventTime > now() - {days}d select EventTime, Account, Url", "out": \
                "/outputs/skills/group-actions.md"}}
                   ```
                3. Keep the actions of the group's members:
                   ```json
                   {"tool": "table", "arguments": {"path": "/outputs/skills/group-actions.md", "join": {"path": \
                "/outputs/skills/group-members.md", "on": ["Account"]}}}
                   ```

                ## Output
                A table in the chat.
                """;
    }

    private DocRef folder(final String name) {
        return asUser(user, () -> explorerService.create(ExplorerConstants.FOLDER_TYPE, name,
                explorerNodeService.getRoot(), PermissionInheritance.DESTINATION).getDocRef());
    }

    // A Lucene index in the folder, made as the user, which a skill's query can be from.
    private void index(final DocRef folder, final String name) {
        asUser(user, () -> explorerService.create(LuceneIndexDoc.TYPE, name,
                explorerNodeService.getNode(folder).orElseThrow(), PermissionInheritance.DESTINATION));
    }

    private DocRef skillsDoc(final DocRef folder, final String text) {
        final DocRef doc = asUser(user, () -> explorerService.create(DocumentationDoc.TYPE, SkillDoc.DOC_NAME,
                explorerNodeService.getNode(folder).orElseThrow(), PermissionInheritance.DESTINATION).getDocRef());
        save(doc, text);
        return doc;
    }

    // Saves the doc's text, as the user does with Stroom's Save.
    private void save(final DocRef doc, final String text) {
        asUser(user, () -> documentationStore.writeDocument(documentationStore.readDocument(doc).copy()
                .data(text).build()));
        skillCatalogue.clearCache();
    }

    private String text(final DocRef doc) {
        return asUser(user, () -> documentationStore.readDocument(doc)).getData();
    }

    private Optional<DocRef> skillsDocIn(final DocRef folder) {
        return explorerNodeService.getChildren(folder).stream()
                .map(ExplorerNode::getDocRef)
                .filter(child -> DocumentationDoc.TYPE.equals(child.getType())
                                 && SkillDoc.DOC_NAME.equals(child.getName()))
                .findFirst();
    }

    // The n-th proposal made with propose_skill in the chat (from 0).
    private static AiChatMessage proposal(final List<AiChatMessage> messages, final int index) {
        return messages.stream()
                .filter(message -> message.getMessageType() == AiMessageType.TOOL_RESULT
                                   && "propose_skill".equals(JsonUtil.readValue(message.getMessage(),
                        ToolResultRecord.class).tool()))
                .toList()
                .get(index);
    }

    private static UserActionRecord action(final AiChatMessage proposal) {
        final ToolResultRecord result = JsonUtil.readValue(proposal.getMessage(), ToolResultRecord.class);
        assertThat(result.userActions()).as(result.text()).hasSize(1);
        return result.userActions().getFirst();
    }

    private static ToolResultRecord result(final List<AiChatMessage> messages, final String tool) {
        return results(messages, tool).getFirst();
    }

    private static List<ToolResultRecord> results(final List<AiChatMessage> messages, final String tool) {
        return messages.stream()
                .filter(message -> message.getMessageType() == AiMessageType.TOOL_RESULT)
                .map(message -> JsonUtil.readValue(message.getMessage(), ToolResultRecord.class))
                .filter(record -> tool.equals(record.tool()))
                .toList();
    }

    private static String json(final Map<String, Object> arguments) {
        return JsonUtil.writeValueAsString(arguments, false);
    }

    @SuppressWarnings("unchecked")
    private static String systemPrompt(final Map<String, Object> request) {
        final List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
        return String.valueOf(messages.getFirst().get("content"));
    }

    private List<AiChatMessage> ask(final AiChat chat, final DocRef modelRef, final String question) {
        return ask(chat, modelRef, question, Map.of());
    }

    // Asks the question as the user, answering any form the run stops for with the answers given, and gives the
    // chat's messages once it is answered.
    private List<AiChatMessage> ask(final AiChat chat,
                                    final DocRef modelRef,
                                    final String question,
                                    final Map<String, String> formAnswers) {
        final long answersBefore = answers(chat.getId());
        asUser(user, () -> askStroomAIService.askStroomAi(new AskStroomAiRequest(chat,
                AskStroomAiConfig.builder().modelRef(modelRef).build(), null, question, AiAgentMode.AGENT)));
        final Set<Integer> handled = new HashSet<>();
        final Instant until = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(until)) {
            final List<AiChatMessage> messages = asUser(user, () -> {
                askStroomAIService.pollMessages(chat.getId(), new AiChatPollRequest(0));
                return aiService.getMessages(chat.getId());
            });
            for (final AiChatMessage message : messages) {
                if (message.getMessageType() == AiMessageType.ELICITATION && handled.add(message.getId())
                    && JsonUtil.readValue(message.getMessage(), ElicitationRecord.class).status()
                       == ElicitationRecord.Status.PENDING) {
                    asUser(user, () -> askStroomAIService.answerElicitation(new AnswerAiElicitationRequest(chat,
                            message.getId(), "ACCEPT", formAnswers)));
                }
            }
            if (answers(chat.getId()) > answersBefore && !runManager.isRunningHere(chat.getId())) {
                assertThat(messages).as("The run failed")
                        .noneMatch(message -> message.getMessageType() == AiMessageType.ERROR);
                return asUser(user, () -> aiService.getMessages(chat.getId()));
            }
            sleep();
        }
        throw new AssertionError("No answer within 2 minutes");
    }

    private List<AiChatMessage> answered(final UserRef asker, final int chatId, final int answers) {
        final Instant until = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(until)) {
            final List<AiChatMessage> messages = asUser(asker, () -> aiService.getMessages(chatId));
            if (messages.stream().filter(message -> message.getMessageType() == AiMessageType.AI_RESPONSE
                                                   || message.getMessageType() == AiMessageType.ERROR)
                        .count() > answers && !runManager.isRunningHere(chatId)) {
                assertThat(messages).as("The run failed")
                        .noneMatch(message -> message.getMessageType() == AiMessageType.ERROR);
                return messages;
            }
            sleep();
        }
        throw new AssertionError("No answer within 2 minutes");
    }

    private long answers(final int chatId) {
        return asUser(user, () -> aiService.getMessages(chatId)).stream()
                .filter(message -> message.getMessageType() == AiMessageType.AI_RESPONSE
                                   || message.getMessageType() == AiMessageType.ERROR)
                .count();
    }

    private static void sleep() {
        try {
            Thread.sleep(100);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted", e);
        }
    }

    // A model doc pointing at the fake model server, with tools turned on.
    private DocRef fakeModel() {
        final DocRef ref = modelStore.createDocument("Fake model");
        final OpenAIModelDoc doc = modelStore.readDocument(ref).copy()
                .baseUrl(model.baseUrl())
                .apiKey("key")
                .modelId("fake")
                .toolsEnabled(true)
                .build();
        modelStore.writeDocument(doc);
        return ref;
    }
}
