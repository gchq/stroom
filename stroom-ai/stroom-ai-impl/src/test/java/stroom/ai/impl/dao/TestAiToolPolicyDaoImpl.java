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

import stroom.ai.impl.AiToolPolicyDao;
import stroom.ai.impl.db.AiDbModule;
import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AiToolPolicy;
import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocFinder;
import stroom.test.common.util.db.DbTestUtil;
import stroom.test.common.util.db.UniqueDbTestModule;
import stroom.util.exception.DataChangedException;
import stroom.util.shared.UserRef;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Runs against a database of its own, with every migration run, as an upgrade would.
class TestAiToolPolicyDaoImpl {

    private AiToolPolicyDao dao;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(
                new UniqueDbTestModule(),
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
        dao = injector.getInstance(AiToolPolicyDao.class);
    }

    @AfterEach
    void tearDown() {
        DbTestUtil.dropThreadTestDatabase(false);
    }

    @Test
    void testTheUpgradeLeavesEveryoneTheToolsThatOnlyRead() {
        // Policies govern the chat too, so without this nobody would have any tools after the upgrade.
        assertThat(dao.list()).singleElement().satisfies(policy -> {
            assertThat(policy.getName()).isEqualTo("Everyone: read-only tools");
            assertThat(policy.isEnabled()).isTrue();
            assertThat(policy.isAllUsers()).isTrue();
            assertThat(policy.getMembers()).isEmpty();
            assertThat(policy.getAccess()).isEqualTo(AiToolAccess.READ_ONLY);
            assertThat(policy.getCategories()).containsExactly(AiToolCategory.values());
        });
    }

    @Test
    void testCreateFetchUpdateAndDelete() {
        final AiToolPolicy created = dao.create(AiToolPolicy.builder()
                .name("Analysts")
                .description("Search and content")
                .members(List.of(user("alice"), user("analysts")))
                .categories(List.of(AiToolCategory.SEARCH, AiToolCategory.CONTENT))
                .access(AiToolAccess.READ_WRITE)
                .build(), "admin");

        assertThat(created.getId()).isNotNull();
        assertThat(created.getVersion()).isEqualTo(1);
        assertThat(created.getCreateUser()).isEqualTo("admin");
        assertThat(created.getMembers()).extracting(UserRef::getUuid).containsExactly("alice", "analysts");
        assertThat(created.getCategories()).containsExactly(AiToolCategory.SEARCH, AiToolCategory.CONTENT);
        assertThat(dao.fetch(created.getId())).contains(created);
        assertThat(dao.list()).extracting(AiToolPolicy::getName)
                .containsExactly("Analysts", "Everyone: read-only tools");

        final AiToolPolicy updated = dao.update(created.copy()
                .enabled(false)
                .members(List.of(user("bob")))
                .categories(List.of(AiToolCategory.PROCESSING))
                .build(), "admin2");

        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(updated.isEnabled()).isFalse();
        assertThat(updated.getUpdateUser()).isEqualTo("admin2");
        assertThat(updated.getMembers()).extracting(UserRef::getUuid).containsExactly("bob");
        assertThat(updated.getCategories()).containsExactly(AiToolCategory.PROCESSING);
        // The list reads members and categories for every policy at once.
        assertThat(dao.list()).filteredOn(policy -> policy.getName().equals("Analysts")).containsExactly(updated);

        assertThat(dao.delete(created.getId())).isTrue();
        assertThat(dao.fetch(created.getId())).isEmpty();
        assertThat(dao.delete(created.getId())).isFalse();
    }

    @Test
    void testAnUpdateFromAStaleReadIsRefused() {
        final AiToolPolicy created = dao.create(AiToolPolicy.builder()
                .name("Readers")
                .categories(List.of(AiToolCategory.CORE))
                .build(), "admin");
        dao.update(created.copy().description("first").build(), "admin");

        assertThatThrownBy(() -> dao.update(created.copy().description("second").build(), "admin"))
                .isInstanceOf(DataChangedException.class)
                .hasMessageContaining("Readers");
        assertThat(dao.fetch(created.getId()).orElseThrow().getDescription()).isEqualTo("first");
    }

    private static UserRef user(final String uuid) {
        return UserRef.builder().uuid(uuid).build();
    }
}
