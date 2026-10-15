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

package stroom.config.global.impl.dao;

import stroom.ai.shared.AskStroomAiConfig;
import stroom.config.global.impl.db.GlobalConfigDbConnProvider;
import stroom.config.impl.db.jooq.tables.Preferences;
import stroom.docref.DocRef;
import stroom.test.common.util.db.DbTestUtil;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(GlobalConfigTestModule.class)
class TestUserPreferencesDaoImpl {

    private static final UserRef ADMIN = UserRef.builder().uuid("admin-uuid").subjectId("admin").build();
    private static final UserRef USER_A = UserRef.builder().uuid("a-uuid").subjectId("a").build();
    private static final UserRef USER_B = UserRef.builder().uuid("b-uuid").subjectId("b").build();
    private static final AskStroomAiConfig STALE_CONFIG = AskStroomAiConfig.builder()
            .modelRef(new DocRef("OpenAIModel", "old-model-uuid", "Old model"))
            .build();

    @Inject
    UserPreferencesDaoImpl userPreferencesDao;
    @Inject
    GlobalConfigDbConnProvider globalConfigDbConnProvider;

    @BeforeEach
    void setUp() throws SQLException {
        try (final Connection connection = globalConfigDbConnProvider.getConnection()) {
            DbTestUtil.clearTables(connection, List.of(Preferences.PREFERENCES.getName()));
        }
    }

    @Test
    void clearAskStroomAiConfigs_resetsEveryoneIncludingTheDefaults() {
        userPreferencesDao.update(USER_A, UserPreferences.builder()
                .theme("Light")
                .askStroomAiConfig(STALE_CONFIG)
                .build());
        // User B has never used the chat.
        userPreferencesDao.update(USER_B, UserPreferences.builder().theme("Dark").build());
        userPreferencesDao.updateDefault(ADMIN, UserPreferences.builder()
                .askStroomAiConfig(STALE_CONFIG)
                .build());

        final int changed = userPreferencesDao.clearAskStroomAiConfigs(ADMIN);

        assertThat(changed).isEqualTo(2);
        assertThat(userPreferencesDao.fetch(USER_A).orElseThrow().getAskStroomAiConfig()).isNull();
        assertThat(userPreferencesDao.fetchDefault().orElseThrow().getAskStroomAiConfig()).isNull();
        // Everything else is left as it was.
        assertThat(userPreferencesDao.fetch(USER_A).orElseThrow().getTheme()).isEqualTo("Light");
        assertThat(userPreferencesDao.fetch(USER_B).orElseThrow().getTheme()).isEqualTo("Dark");
    }

    @Test
    void newPreferencesHaveNoAskStroomAiConfig() {
        // So that the configured defaults are used, rather than an empty config with no model.
        assertThat(UserPreferences.builder().build().getAskStroomAiConfig()).isNull();
    }

    @Test
    void clearAskStroomAiConfigs_nothingToClear() {
        userPreferencesDao.update(USER_B, UserPreferences.builder().theme("Dark").build());

        assertThat(userPreferencesDao.clearAskStroomAiConfigs(ADMIN)).isZero();
    }
}
