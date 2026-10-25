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

package stroom.ai.impl.dao;

import stroom.ai.impl.AiDao;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.AiDbModule;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatStatus;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.DocumentReference;
import stroom.ai.shared.DocumentReferenceContext;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.ai.shared.GeneralTableContext;
import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiDaoImpl {

    private static final List<String> TABLES = List.of(
            "ai_chat_file_chunk",
            "ai_chat_run",
            "ai_memory",
            "ai_tool_approval",
            "ai_chat_message",
            "ai_chat_attachment",
            "ai_chat");

    @Inject
    private AiDao aiDao;
    @Inject
    private AiDbConnProvider aiDbConnProvider;

    @BeforeEach
    void setUp() throws SQLException {
        final Injector injector = Guice.createInjector(
                new DbTestModule(),
                new AiDbModule(),
                new AiDaoModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(WordListProvider.class).toInstance(Mockito.mock(WordListProvider.class));
                        bind(CollectionService.class).toInstance(Mockito.mock(CollectionService.class));
                        bind(DocFinder.class).toInstance(Mockito.mock(DocFinder.class));
                    }
                });
        injector.injectMembers(this);

        try (final Connection connection = aiDbConnProvider.getConnection()) {
            DbTestUtil.clearTables(connection, TABLES);
        }
    }

    @Test
    void testADocumentReferenceSaysWhichDocumentItIs() {
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final DocRef dictionary = new DocRef("Dictionary", "dict-uuid", "Malicious IPs");
        final AiChatAttachment reference = aiDao.createAttachment(chat.getId(), AiAttachmentType.DOCUMENT,
                JsonUtil.writeValueAsString(new DocumentReferenceContext(List.of(
                        new DocumentReference(dictionary, "/Security/Lookups")))));
        // A table's context isn't a document reference, so isn't read as one.
        final AiChatAttachment table = aiDao.createAttachment(chat.getId(), AiAttachmentType.GENERAL,
                JsonUtil.writeValueAsString(new GeneralTableContext("t", List.of("a"), List.of())));
        aiDao.updateAttachmentStatus(reference.getId(), AiAttachmentStatus.READY, null,
                "Dictionary 'Malicious IPs'", null, false);

        final AiChatAttachment read = aiDao.getAttachment(reference.getId()).orElseThrow();
        assertThat(read.getAttachmentType()).isEqualTo(AiAttachmentType.DOCUMENT);
        assertThat(read.getStatus()).isEqualTo(AiAttachmentStatus.READY);
        assertThat(read.getDocRef()).isEqualTo(dictionary);
        assertThat(read.getDocRef().getName()).isEqualTo("Malicious IPs");
        assertThat(read.getDocPath()).isEqualTo("/Security/Lookups");
        assertThat(aiDao.getAttachmentsByChatId(chat.getId()))
                .extracting(AiChatAttachment::getId, AiChatAttachment::getDocRef)
                .containsExactly(
                        Tuple.tuple(reference.getId(), dictionary),
                        Tuple.tuple(table.getId(), null));
    }

    @Test
    void testStoreMessage_assignsTurns() {
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());

        final AiChatMessage attachment = aiDao.storeMessage(chat.getId(), AiMessageType.ATTACHMENT, "table");
        final AiChatMessage question1 = aiDao.storeMessage(chat.getId(), AiMessageType.USER_MESSAGE, "q1");
        final AiChatMessage thinking1 = aiDao.storeMessage(chat.getId(), AiMessageType.THINKING, "hmm");
        final AiChatMessage answer1 = aiDao.storeMessage(chat.getId(), AiMessageType.AI_RESPONSE, "a1");
        final AiChatMessage question2 = aiDao.storeMessage(chat.getId(), AiMessageType.USER_MESSAGE, "q2");
        final AiChatMessage answer2 = aiDao.storeMessage(chat.getId(), AiMessageType.AI_RESPONSE, "a2");

        // Anything before the first question has no turn.
        assertThat(attachment.getTurnId()).isZero();
        // A question starts its own turn, and what follows belongs to it.
        assertThat(question1.getTurnId()).isEqualTo(question1.getId());
        assertThat(thinking1.getTurnId()).isEqualTo(question1.getId());
        assertThat(answer1.getTurnId()).isEqualTo(question1.getId());
        assertThat(question2.getTurnId()).isEqualTo(question2.getId());
        assertThat(answer2.getTurnId()).isEqualTo(question2.getId());

        // What is read back matches what was stored.
        assertThat(aiDao.getMessages(chat.getId()))
                .containsExactly(attachment, question1, thinking1, answer1, question2, answer2);
    }

    @Test
    void testStoreMessage_turnsAreKeptPerChat() {
        final AiChat chat1 = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChat chat2 = aiDao.createChat(UserRef.builder().uuid("user1").build());

        final AiChatMessage question1 = aiDao.storeMessage(chat1.getId(), AiMessageType.USER_MESSAGE, "q1");
        final AiChatMessage question2 = aiDao.storeMessage(chat2.getId(), AiMessageType.USER_MESSAGE, "q2");
        final AiChatMessage answer1 = aiDao.storeMessage(chat1.getId(), AiMessageType.AI_RESPONSE, "a1");

        assertThat(answer1.getTurnId()).isEqualTo(question1.getId());
        assertThat(question2.getTurnId()).isEqualTo(question2.getId());
    }

    @Test
    void testStoreMessage_tokenCount() {
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());

        final AiChatMessage message = aiDao.storeMessage(chat.getId(), AiMessageType.USER_MESSAGE, "x".repeat(400));
        final AiChatMessage empty = aiDao.storeMessage(chat.getId(), AiMessageType.ERROR, null);

        assertThat(message.getTokenCount()).isEqualTo(100);
        // A null message, e.g. from an exception without one, is stored as empty.
        assertThat(empty.getTokenCount()).isZero();
        assertThat(empty.getMessage()).isEmpty();
        assertThat(aiDao.getMessages(chat.getId()).getFirst().getTokenCount()).isEqualTo(100);
    }

    @Test
    void testGetMessagesSince_inIdOrder() {
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChatMessage first = aiDao.storeMessage(chat.getId(), AiMessageType.USER_MESSAGE, "q1");
        final AiChatMessage second = aiDao.storeMessage(chat.getId(), AiMessageType.AI_RESPONSE, "a1");
        final AiChatMessage third = aiDao.storeMessage(chat.getId(), AiMessageType.AI_RESPONSE, "a2");

        assertThat(aiDao.getMessagesSince(chat.getId(), first.getId()))
                .extracting(AiChatMessage::getId)
                .containsExactly(second.getId(), third.getId());
    }

    @Test
    void testRecordUsage_addsUpTotalsAndKeepsTheLastCall() {
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());

        aiDao.recordUsage(chat.getId(), "model1", 128_000, 1_000, 50);
        aiDao.recordUsage(chat.getId(), "model1", 128_000, 1_500, 70);

        assertThat(aiDao.getUsage(chat.getId())).isEqualTo(
                new stroom.ai.impl.AiDao.ChatUsage("model1", 128_000, 1_500, 2_500, 120));
    }

    @Test
    void testRecordModel_keepsTheTokenTotals() {
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        aiDao.recordUsage(chat.getId(), "model1", 128_000, 1_000, 50);

        aiDao.recordModel(chat.getId(), "model2", 8_000);

        assertThat(aiDao.getUsage(chat.getId())).isEqualTo(
                new stroom.ai.impl.AiDao.ChatUsage("model2", 8_000, 1_000, 1_000, 50));
    }

    @Test
    void testGetUsage_unknownChat() {
        assertThat(aiDao.getUsage(-1)).isEqualTo(new stroom.ai.impl.AiDao.ChatUsage(null, null, null, 0, 0));
    }

    @Test
    void testChatStatusesAndReading() {
        final AiChat older = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChat newer = aiDao.createChat(UserRef.builder().uuid("user1").build());
        aiDao.createChat(UserRef.builder().uuid("user2").build());
        aiDao.storeMessage(older.getId(), AiMessageType.USER_MESSAGE, "q");
        final AiChatMessage answer = aiDao.storeMessage(older.getId(), AiMessageType.AI_RESPONSE, "a");
        final long version = aiDao.getStatusVersion("user1");

        final List<AiChatStatus> statuses = aiDao.getChatStatuses("user1", 10);

        // Only the user's own chats, most recently changed first.
        assertThat(statuses).extracting(AiChatStatus::getChatId)
                .containsExactly(older.getId(), newer.getId());
        assertThat(statuses.getFirst().isUnread()).isTrue();
        assertThat(statuses.getLast().isUnread()).isFalse();
        assertThat(statuses.getFirst().getRunStatus()).isNull();

        aiDao.markRead(older.getId(), answer.getId());
        assertThat(aiDao.getChatStatuses("user1", 10).getFirst().getLastOpenedMs()).isPositive();

        assertThat(aiDao.getChatStatuses("user1", 10).getFirst().isUnread()).isFalse();
        // Reading changes the version, so the client hears about it.
        assertThat(aiDao.getStatusVersion("user1")).isNotEqualTo(version);
        // Marking an earlier message read does not go backwards.
        aiDao.markRead(older.getId(), 1);
        assertThat(aiDao.getChatStatuses("user1", 10).getFirst().isUnread()).isFalse();
    }

    @Test
    void eachStatusHasItsChatsNewestForm() {
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChat none = aiDao.createChat(UserRef.builder().uuid("user1").build());
        aiDao.storeMessage(chat.getId(), AiMessageType.APPROVAL_REQUEST, "approve?");
        final AiChatMessage question = aiDao.storeMessage(chat.getId(), AiMessageType.ELICITATION, "which?");
        aiDao.storeMessage(chat.getId(), AiMessageType.AI_RESPONSE, "thanks");
        aiDao.storeMessage(none.getId(), AiMessageType.AI_RESPONSE, "a");

        final Map<Integer, Integer> forms = aiDao.getChatStatuses("user1", 10).stream()
                .collect(Collectors.toMap(AiChatStatus::getChatId, AiChatStatus::getFormMessageId));

        // A chat waiting again, with another form, is told from one still waiting with the same.
        assertThat(forms).containsEntry(chat.getId(), question.getId())
                .containsEntry(none.getId(), 0);
    }

    @Test
    void aNewChatIsTheLastOpened() {
        final AiChat earlier = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChatMessage answer = aiDao.storeMessage(earlier.getId(), AiMessageType.AI_RESPONSE, "a");
        aiDao.markRead(earlier.getId(), answer.getId());

        // Nothing has been read in the new chat yet, but it is open, so a page refresh shows it again.
        final AiChat created = aiDao.createChat(UserRef.builder().uuid("user1").build());

        final Map<Integer, Long> lastOpened = aiDao.getChatStatuses("user1", 10).stream()
                .collect(Collectors.toMap(AiChatStatus::getChatId, AiChatStatus::getLastOpenedMs));
        assertThat(lastOpened.get(created.getId()))
                .isPositive()
                .isGreaterThanOrEqualTo(lastOpened.get(earlier.getId()));
    }

    @Test
    void agentModeIsKeptAndChangesTheStatusVersion() {
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        assertThat(aiDao.getAgentMode(chat.getId())).isEqualTo(AiAgentMode.AGENT);
        final long version = aiDao.getStatusVersion("user1");

        aiDao.setAgentMode(chat.getId(), AiAgentMode.PLAN);

        assertThat(aiDao.getAgentMode(chat.getId())).isEqualTo(AiAgentMode.PLAN);
        assertThat(aiDao.getChatStatuses("user1", 10))
                .singleElement()
                .extracting(AiChatStatus::getAgentMode)
                .isEqualTo(AiAgentMode.PLAN);
        // So the list of chats, and the user's other browser tabs, show the new mode.
        assertThat(aiDao.getStatusVersion("user1")).isNotEqualTo(version);
    }

    @Test
    void markAllReadClearsEveryUnreadMarkOfTheUsersChatsOnly() {
        final AiChat first = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChat second = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChat others = aiDao.createChat(UserRef.builder().uuid("user2").build());
        for (final AiChat chat : List.of(first, second, others)) {
            aiDao.storeMessage(chat.getId(), AiMessageType.USER_MESSAGE, "q");
            aiDao.storeMessage(chat.getId(), AiMessageType.AI_RESPONSE, "a");
        }
        assertThat(aiDao.getChatStatuses("user1", 10)).allMatch(AiChatStatus::isUnread);
        final long version = aiDao.getStatusVersion("user1");
        final List<Long> lastOpened = aiDao.getChatStatuses("user1", 10).stream()
                .map(AiChatStatus::getLastOpenedMs)
                .toList();

        assertThat(aiDao.markAllRead("user1")).isEqualTo(2);

        assertThat(aiDao.getChatStatuses("user1", 10)).noneMatch(AiChatStatus::isUnread);
        // When each was last opened is left alone.
        assertThat(aiDao.getChatStatuses("user1", 10)).extracting(AiChatStatus::getLastOpenedMs)
                .containsExactlyElementsOf(lastOpened);
        assertThat(aiDao.getStatusVersion("user1")).isNotEqualTo(version);
        assertThat(aiDao.getChatStatuses("user2", 10)).singleElement().matches(AiChatStatus::isUnread);
        // Nothing left to mark.
        assertThat(aiDao.markAllRead("user1")).isZero();
    }


    @Test
    void automaticTitlesStopOnceTheUserNamesTheChat() {
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final long updated = aiDao.getChatStatuses("user1", 10).getFirst().getUpdateTimeMs();

        final long version = aiDao.getStatusVersion("user1");
        assertThat(aiDao.setAutoTitle(chat.getId(), "Firewall errors")).isTrue();
        // So the browser hears about the new title.
        assertThat(aiDao.getStatusVersion("user1")).isNotEqualTo(version);
        assertThat(aiDao.getChatStatuses("user1", 10).getFirst())
                .extracting(AiChatStatus::getTitle, AiChatStatus::getUpdateTimeMs)
                .containsExactly("Firewall errors", updated);

        assertThat(aiDao.isTitleSetByUser(chat.getId())).isFalse();
        aiDao.updateChatTitle(chat.getId(), "My name for it");
        assertThat(aiDao.isTitleSetByUser(chat.getId())).isTrue();
        assertThat(aiDao.setAutoTitle(chat.getId(), "Something else")).isFalse();
        assertThat(aiDao.getChatStatuses("user1", 10).getFirst().getTitle()).isEqualTo("My name for it");

        // Clearing the chat lets it be named automatically again.
        aiDao.resetChatTitle(chat.getId());
        assertThat(aiDao.getChatStatuses("user1", 10).getFirst().getTitle()).isEqualTo(AiDao.DEFAULT_CHAT_TITLE);
        assertThat(aiDao.setAutoTitle(chat.getId(), "Disk space")).isTrue();
    }


    @Test
    void searchFindsChatsByTitleOrQuestion() {
        final AiChat byTitle = aiDao.createChat(UserRef.builder().uuid("user1").build());
        aiDao.updateChatTitle(byTitle.getId(), "Firewall errors");
        final AiChat byQuestion = aiDao.createChat(UserRef.builder().uuid("user1").build());
        aiDao.storeMessage(byQuestion.getId(), AiMessageType.USER_MESSAGE, "Why is FIREWALL fw1 slow?");
        aiDao.storeMessage(byQuestion.getId(), AiMessageType.AI_RESPONSE, "an answer");
        aiDao.storeMessage(byQuestion.getId(), AiMessageType.USER_MESSAGE, "and the firewall on fw2?");
        final AiChat byAnswerOnly = aiDao.createChat(UserRef.builder().uuid("user1").build());
        aiDao.storeMessage(byAnswerOnly.getId(), AiMessageType.USER_MESSAGE, "what failed?");
        aiDao.storeMessage(byAnswerOnly.getId(), AiMessageType.AI_RESPONSE, "the firewall failed");
        final AiChat othersChat = aiDao.createChat(UserRef.builder().uuid("user2").build());
        aiDao.updateChatTitle(othersChat.getId(), "Firewall too");

        final List<AiDao.ChatSearchMatch> matches = aiDao.searchChats("user1", "firewall", 10);

        // Ignoring case; only the user's own chats; not answers.
        assertThat(matches).extracting(AiDao.ChatSearchMatch::chatId)
                .containsExactlyInAnyOrder(byTitle.getId(), byQuestion.getId());
        // The latest matching question is given, and none where only the title matched.
        assertThat(matches).filteredOn(match -> match.chatId() == byQuestion.getId())
                .singleElement()
                .extracting(AiDao.ChatSearchMatch::question)
                .isEqualTo("and the firewall on fw2?");
        assertThat(matches).filteredOn(match -> match.chatId() == byTitle.getId())
                .singleElement()
                .extracting(AiDao.ChatSearchMatch::question)
                .isNull();
        assertThat(aiDao.searchChats("user1", "firewall", 1)).hasSize(1);
    }

    @Test
    void searchTreatsWildcardsAsText() {
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        aiDao.storeMessage(chat.getId(), AiMessageType.USER_MESSAGE, "disk 100% full");

        assertThat(aiDao.searchChats("user1", "100%", 10)).hasSize(1);
        assertThat(aiDao.searchChats("user1", "1_0", 10)).isEmpty();
        assertThat(aiDao.searchChats("user1", "%", 10)).hasSize(1);
        assertThat(aiDao.searchChats("user1", "!", 10)).isEmpty();
    }


    @Test
    void aLateSteerBecomesAQuestionOfItsOwn() {
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChatMessage question = aiDao.storeMessage(chat.getId(), AiMessageType.USER_MESSAGE, "first");
        aiDao.storeMessage(chat.getId(), AiMessageType.AI_RESPONSE, "answer");
        final AiChatMessage steer = aiDao.storeMessage(chat.getId(), AiMessageType.STEER_MESSAGE, "and fw2?");
        assertThat(steer.getTurnId()).isEqualTo(question.getId());

        aiDao.askAsQuestion(steer.getId(), "and fw2?");

        final AiChatMessage asked = aiDao.getMessages(chat.getId()).getLast();
        assertThat(asked.getId()).isEqualTo(steer.getId());
        assertThat(asked.getMessageType()).isEqualTo(AiMessageType.USER_MESSAGE);
        assertThat(asked.getTurnId()).isEqualTo(steer.getId());
        // What follows belongs to its turn.
        assertThat(aiDao.storeMessage(chat.getId(), AiMessageType.AI_RESPONSE, "fw2 too").getTurnId())
                .isEqualTo(steer.getId());
    }

    @Test
    void queuedSteersChangeTypeOnlyFromTheGivenTypesAndInTheirOwnChat() {
        final Set<AiMessageType> queued = Set.of(AiMessageType.QUEUED_STEER_MESSAGE,
                AiMessageType.SEND_NOW_STEER_MESSAGE);
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChat other = aiDao.createChat(UserRef.builder().uuid("user1").build());
        aiDao.storeMessage(chat.getId(), AiMessageType.USER_MESSAGE, "first");
        final AiChatMessage steer = aiDao.storeMessage(chat.getId(), AiMessageType.QUEUED_STEER_MESSAGE, "fw2?");
        final AiChatMessage later = aiDao.storeMessage(chat.getId(), AiMessageType.QUEUED_STEER_MESSAGE, "fw3?");
        final AiChatMessage elsewhere = aiDao.storeMessage(other.getId(), AiMessageType.QUEUED_STEER_MESSAGE, "x");

        assertThat(aiDao.hasMessageOfType(chat.getId(), AiMessageType.SEND_NOW_STEER_MESSAGE)).isFalse();
        // Not in that chat, so not changed.
        assertThat(aiDao.changeMessageType(chat.getId(), elsewhere.getId(),
                Set.of(AiMessageType.QUEUED_STEER_MESSAGE), AiMessageType.SEND_NOW_STEER_MESSAGE)).isFalse();
        assertThat(aiDao.changeMessageType(chat.getId(), steer.getId(),
                Set.of(AiMessageType.QUEUED_STEER_MESSAGE), AiMessageType.SEND_NOW_STEER_MESSAGE)).isTrue();
        assertThat(aiDao.hasMessageOfType(chat.getId(), AiMessageType.SEND_NOW_STEER_MESSAGE)).isTrue();
        // No longer of the type it is to be changed from.
        assertThat(aiDao.changeMessageType(chat.getId(), steer.getId(),
                Set.of(AiMessageType.QUEUED_STEER_MESSAGE), AiMessageType.SEND_NOW_STEER_MESSAGE)).isFalse();

        assertThat(aiDao.changeMessageTypes(chat.getId(), queued, AiMessageType.STEER_MESSAGE)).isEqualTo(2);
        assertThat(aiDao.getMessages(chat.getId()))
                .filteredOn(message -> message.getId() == steer.getId() || message.getId() == later.getId())
                .extracting(AiChatMessage::getMessageType)
                .containsOnly(AiMessageType.STEER_MESSAGE);
        assertThat(aiDao.hasMessageOfType(other.getId(), AiMessageType.QUEUED_STEER_MESSAGE)).isTrue();
    }

    @Test
    void anMcpSessionsChatIsHiddenFromTheUsersChats() {
        final UserRef user = UserRef.builder().uuid("user1").build();
        final AiChat chat = aiDao.createChat(user);
        final AiChat hidden = aiDao.createSessionChat(user, "session-1");

        assertThat(aiDao.findSessionChat("session-1")).contains(hidden.getId());
        assertThat(aiDao.findSessionChat("session-2")).isEmpty();
        assertThat(aiDao.listChats(user, new FindAiChatHistoryCriteria(null, null, null)).getValues())
                .extracting(AiChat::getId)
                .containsExactly(chat.getId());
        assertThat(aiDao.getChatStatuses("user1", 10))
                .extracting(AiChatStatus::getChatId)
                .containsExactly(chat.getId());
        // Its files count towards the user's workspace, so it is one of their chats there.
        assertThat(aiDao.getChatIds("user1")).containsExactlyInAnyOrder(chat.getId(), hidden.getId());
    }

    @Test
    void anMcpSessionsChatIsIdleOnlyIfNotUsedSince() throws InterruptedException {
        final UserRef user = UserRef.builder().uuid("user1").build();
        final AiChat hidden = aiDao.createSessionChat(user, "session-1");
        aiDao.createChat(user);
        final long created = hidden.getUpdateTimeMs();

        // Ordinary chats are never taken for an MCP session's.
        assertThat(aiDao.findSessionChatsNotUpdatedSince(created + 1)).containsExactly(hidden.getId());
        assertThat(aiDao.findSessionChatsNotUpdatedSince(created)).isEmpty();

        // Used too recently to be marked again.
        Thread.sleep(5);
        aiDao.touchChat(hidden.getId(), 60_000);
        assertThat(aiDao.findSessionChatsNotUpdatedSince(created + 1)).containsExactly(hidden.getId());
        aiDao.touchChat(hidden.getId(), 0);
        assertThat(aiDao.findSessionChatsNotUpdatedSince(created + 1)).isEmpty();
    }
}
