/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.annotation.impl.db;

import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestAnnotationFeedDao {

    @Inject
    private AnnotationFeedDao annotationFeedDao;

    @BeforeEach
    void setup() {
        Guice.createInjector(new TestModule()).injectMembers(this);
        annotationFeedDao.clear();
    }

    @Test
    void testCreateDuplicate() {
        // First insert
        final int id1 = annotationFeedDao.create("test-feed");
        assertThat(id1).isGreaterThan(0);

        // Duplicate insert with same value
        final int id2 = annotationFeedDao.create("test-feed");
        assertThat(id2).isEqualTo(id1);
    }

    @Test
    void testFetchById() {
        final int id = annotationFeedDao.create("test-feed");

        final Optional<String> optional = annotationFeedDao.fetchById(id);
        assertThat(optional).isPresent();
        assertThat(optional.get()).isEqualTo("test-feed");

        final Optional<String> optional2 = annotationFeedDao.fetchById(-123);
        assertThat(optional2).isEmpty();
    }

    @Test
    void testFetchByName() {
        final int id = annotationFeedDao.create("test-feed");

        final Optional<Integer> optional = annotationFeedDao.fetchByName("test-feed");
        assertThat(optional).isPresent();
        assertThat(optional.get()).isEqualTo(id);

        final Optional<Integer> optional2 = annotationFeedDao.fetchByName("missing");
        assertThat(optional2).isEmpty();
    }

    @Test
    void testFetchWithWildCards() {
        annotationFeedDao.create("test-feed");

        final Set<Integer> set = annotationFeedDao.fetchWithWildCards(List.of("test-feed"));
        assertThat(set).isNotEmpty();

        final Set<Integer> set2 = annotationFeedDao.fetchWithWildCards(List.of("missing"));
        assertThat(set2).isEmpty();

        final Set<Integer> set3 = annotationFeedDao.fetchWithWildCards(List.of("test-*"));
        assertThat(set3).isNotEmpty();
    }
}
