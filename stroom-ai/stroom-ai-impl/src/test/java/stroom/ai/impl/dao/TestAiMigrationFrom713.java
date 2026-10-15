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
import stroom.ai.shared.AiChatStatus;
import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocFinder;
import stroom.test.common.util.db.DbTestUtil;
import stroom.test.common.util.db.UniqueDbTestModule;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/// Upgrades a database holding chats saved by Stroom 7.13 to the latest AI schema, and checks the chats survive
/// and can be read. The 7.13 data is loaded by a migration of its own, after the last 7.13 migration
/// (V07_13_00_002) and before the first 7.14 one.
class TestAiMigrationFrom713 {

    private static final String TEST_DATA = """
            INSERT INTO ai_chat (id, version, create_time_ms, create_user, update_time_ms, update_user, user_uuid,
                                 title)
            VALUES (1, 1, 1000, 'bob', 2000, 'bob', 'user1', 'Firewall errors'),
                   (2, 1, 1000, 'bob', 3000, 'bob', 'user1', 'Disk space'),
                   (3, 1, 1000, 'fred', 1500, 'fred', 'user2', 'Fred''s chat');
            INSERT INTO ai_chat_attachment (id, fk_ai_chat_id, create_time_ms, update_time_ms, status,
                                            attachment_type, description, row_count)
            VALUES (10, 1, 1000, 1000, 1, 0, 'Firewall events', 42);
            -- Types: 0 question, 1 answer, 3 error, 7 attachment.
            INSERT INTO ai_chat_message (id, fk_ai_chat_id, create_time_ms, message_type, fk_attachment_id, message)
            VALUES (100, 1, 1000, 7, 10, 'Firewall events'),
                   (101, 1, 1001, 0, NULL, 'How many errors?'),
                   (102, 1, 1002, 1, NULL, '42 errors.'),
                   (103, 1, 1003, 0, NULL, 'Which hosts?'),
                   (104, 1, 1004, 3, NULL, 'The model failed.'),
                   (105, 2, 1005, 0, NULL, 'Is the disk full?'),
                   (106, 2, 1006, 1, NULL, 'No.');
            """;

    private Injector injector;

    @BeforeEach
    void setUp(@TempDir final Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("V07_13_00_003__test_data_from_7_13.sql"), TEST_DATA);
        injector = Guice.createInjector(
                new UniqueDbTestModule(),
                new AiDbModule() {
                    // Every migration is run, as an upgrade would, with the 7.13 data loaded on the way.
                    @Override
                    protected List<String> getFlyWayLocations() {
                        return mergeLocations(super.getFlyWayLocations(), "filesystem:" + tempDir.toAbsolutePath());
                    }
                },
                new AiDaoModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(WordListProvider.class).toInstance(Mockito.mock(WordListProvider.class));
                        bind(CollectionService.class).toInstance(Mockito.mock(CollectionService.class));
                        bind(DocFinder.class).toInstance(Mockito.mock(DocFinder.class));
                    }
                });
    }

    @AfterEach
    void tearDown() {
        DbTestUtil.dropThreadTestDatabase(false);
    }

    @Test
    void chatsFrom713SurviveTheUpgrade() throws SQLException {
        // Each message is given the turn of the question it follows; anything before the first has none.
        assertThat(rows("SELECT id, turn_id FROM ai_chat_message ORDER BY id")).containsExactly(
                "100,0", "101,101", "102,101", "103,103", "104,103", "105,105", "106,105");
        // New chat columns have their defaults.
        assertThat(rows("SELECT id, agent_mode, auto_approve, total_input_tokens, total_output_tokens, "
                        + "COALESCE(last_read_message_id, 'null') FROM ai_chat ORDER BY id"))
                .containsExactly("1,0,0,0,0,null", "2,0,0,0,0,null", "3,0,0,0,0,null");
        // The attachment is kept, with no file yet.
        assertThat(rows("SELECT id, fk_ai_chat_id, description, COALESCE(path, 'null') FROM ai_chat_attachment"))
                .containsExactly("10,1,Firewall events,null");
        // New tables are there, and empty.
        for (final String table : List.of("ai_memory", "ai_chat_run", "ai_tool_approval", "ai_chat_file_chunk")) {
            assertThat(rows("SELECT COUNT(*) FROM " + table)).containsExactly("0");
        }
        // The old user index is replaced by one that also sorts by update time.
        assertThat(rows("SELECT DISTINCT index_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'ai_chat' ORDER BY index_name"))
                .contains("idx_ai_chat_user_uuid_update_time")
                .doesNotContain("idx_ai_chat_user_uuid");
    }

    @Test
    void stroomCanReadTheUpgradedChats() {
        final AiDao aiDao = injector.getInstance(AiDao.class);

        // Most recently changed first, and only the user's own; answered chats show as unread.
        assertThat(aiDao.getChatStatuses("user1", 10))
                .extracting(AiChatStatus::getTitle, AiChatStatus::isUnread)
                .containsExactly(
                        tuple("Disk space", true),
                        tuple("Firewall errors", true));
        assertThat(aiDao.getMessages(1))
                .extracting(message -> message.getTurnId())
                .containsExactly(0, 101, 101, 103, 103);
        // Marking a chat read works on old chats.
        aiDao.markAllRead("user1");
        assertThat(aiDao.getChatStatuses("user1", 10)).noneMatch(AiChatStatus::isUnread);
    }

    private List<String> rows(final String sql) throws SQLException {
        final List<String> rows = new ArrayList<>();
        try (final Connection connection = injector.getInstance(AiDbConnProvider.class).getConnection();
                final Statement statement = connection.createStatement();
                final ResultSet resultSet = statement.executeQuery(sql)) {
            final int columns = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                final List<String> values = new ArrayList<>();
                for (int i = 1; i <= columns; i++) {
                    values.add(resultSet.getString(i));
                }
                rows.add(String.join(",", values));
            }
        }
        return rows;
    }
}
