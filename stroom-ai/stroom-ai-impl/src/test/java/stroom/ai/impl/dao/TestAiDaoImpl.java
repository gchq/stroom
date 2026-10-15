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
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatStatus;
import stroom.ai.shared.AiMessageType;
import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocFinder;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.shared.UserRef;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

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

        assertThat(aiDao.markAllRead("user1")).isEqualTo(2);

        assertThat(aiDao.getChatStatuses("user1", 10)).noneMatch(AiChatStatus::isUnread);
        // When each was last opened is left alone.
        assertThat(aiDao.getChatStatuses("user1", 10)).allMatch(status -> status.getLastOpenedMs() == 0);
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

}
