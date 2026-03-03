/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.document.client;

import stroom.docref.DocRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class TestDocumentTabManager {

    private DocumentTabManager manager;
    private DocRef docRef1;
    private DocRef docRef2;

    @Mock
    private DocumentTabData tabData1;

    @Mock
    private DocumentTabData tabData2;

    @Mock
    private DocumentTabData tabData3;

    @BeforeEach
    void setUp() {
        manager = new DocumentTabManager();
        docRef1 = new DocRef("type1", "uuid1", "name1");
        docRef2 = new DocRef("type2", "uuid2", "name2");
    }

    @Nested
    @DisplayName("get() method tests")
    class GetTests {

        @Test
        @DisplayName("should return empty list for non-existent DocRef")
        void shouldReturnEmptyListForNonExistentDocRef() {
            final List<DocumentTabData> result = manager.get(docRef1);

            assertThat(result)
                    .isNotNull()
                    .isEmpty();
        }

        @Test
        @DisplayName("should return list with single tab data")
        void shouldReturnListWithSingleTabData() {
            manager.put(docRef1, tabData1);

            final List<DocumentTabData> result = manager.get(docRef1);

            assertThat(result)
                    .hasSize(1)
                    .contains(tabData1);
        }

        @Test
        @DisplayName("should return list with multiple tab data")
        void shouldReturnListWithMultipleTabData() {
            manager.put(docRef1, tabData1);
            manager.put(docRef1, tabData2);

            final List<DocumentTabData> result = manager.get(docRef1);

            assertThat(result)
                    .hasSize(2)
                    .contains(tabData1, tabData2);
        }
    }

    @Nested
    @DisplayName("put() method tests")
    class PutTests {

        @Test
        @DisplayName("should throw IllegalArgumentException when DocRef is null")
        void shouldThrowExceptionWhenDocRefIsNull() {
            assertThatThrownBy(() -> manager.put(null, tabData1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("DocRef cannot be null");
        }

        @Test
        @DisplayName("should add tab data to new DocRef")
        void shouldAddTabDataToNewDocRef() {
            manager.put(docRef1, tabData1);

            assertThat(manager.get(docRef1))
                    .hasSize(1)
                    .contains(tabData1);
        }

        @Test
        @DisplayName("should add multiple tab data to same DocRef")
        void shouldAddMultipleTabDataToSameDocRef() {
            manager.put(docRef1, tabData1);
            manager.put(docRef1, tabData2);
            manager.put(docRef1, tabData3);

            assertThat(manager.get(docRef1))
                    .hasSize(3)
                    .containsExactly(tabData1, tabData2, tabData3);
        }

        @Test
        @DisplayName("should handle null DocumentTabData")
        void shouldHandleNullDocumentTabData() {
            assertThatCode(() -> manager.put(docRef1, null))
                    .doesNotThrowAnyException();

            assertThat(manager.get(docRef1))
                    .hasSize(1)
                    .containsNull();
        }

        @Test
        @DisplayName("should maintain separate lists for different DocRefs")
        void shouldMaintainSeparateListsForDifferentDocRefs() {
            manager.put(docRef1, tabData1);
            manager.put(docRef2, tabData2);

            assertThat(manager.get(docRef1))
                    .hasSize(1)
                    .contains(tabData1)
                    .doesNotContain(tabData2);

            assertThat(manager.get(docRef2))
                    .hasSize(1)
                    .contains(tabData2)
                    .doesNotContain(tabData1);
        }
    }

    @Nested
    @DisplayName("remove() method tests")
    class RemoveTests {

        @Test
        @DisplayName("should return false when removing from non-existent DocRef")
        void shouldReturnFalseWhenRemovingFromNonExistentDocRef() {
            final boolean result = manager.remove(docRef1, tabData1);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when removing non-existent tab data")
        void shouldReturnFalseWhenRemovingNonExistentTabData() {
            manager.put(docRef1, tabData1);

            final boolean result = manager.remove(docRef1, tabData2);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true and remove existing tab data")
        void shouldReturnTrueAndRemoveExistingTabData() {
            manager.put(docRef1, tabData1);

            final boolean result = manager.remove(docRef1, tabData1);

            assertThat(result).isTrue();
            assertThat(manager.get(docRef1)).isEmpty();
        }

        @Test
        @DisplayName("should remove only specified tab data")
        void shouldRemoveOnlySpecifiedTabData() {
            manager.put(docRef1, tabData1);
            manager.put(docRef1, tabData2);

            manager.remove(docRef1, tabData1);

            assertThat(manager.get(docRef1))
                    .hasSize(1)
                    .contains(tabData2)
                    .doesNotContain(tabData1);
        }

        @Test
        @DisplayName("should clean up empty list after removing last tab data")
        void shouldCleanUpEmptyListAfterRemovingLastTabData() {
            manager.put(docRef1, tabData1);
            manager.remove(docRef1, tabData1);

            assertThat(manager.get(docRef1)).isEmpty();
            assertThat(manager.getAll()).isEmpty();
        }

        @Test
        @DisplayName("should handle removing null tab data")
        void shouldHandleRemovingNullTabData() {
            manager.put(docRef1, null);

            final boolean result = manager.remove(docRef1, null);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("getAll() method tests")
    class GetAllTests {

        @Test
        @DisplayName("should return empty list when no data exists")
        void shouldReturnEmptyListWhenNoDataExists() {
            assertThat(manager.getAll())
                    .isNotNull()
                    .isEmpty();
        }

        @Test
        @DisplayName("should return all tab data from single DocRef")
        void shouldReturnAllTabDataFromSingleDocRef() {
            manager.put(docRef1, tabData1);
            manager.put(docRef1, tabData2);

            assertThat(manager.getAll())
                    .hasSize(2)
                    .contains(tabData1, tabData2);
        }

        @Test
        @DisplayName("should return all tab data from multiple DocRefs")
        void shouldReturnAllTabDataFromMultipleDocRefs() {
            manager.put(docRef1, tabData1);
            manager.put(docRef1, tabData2);
            manager.put(docRef2, tabData3);

            assertThat(manager.getAll())
                    .hasSize(3)
                    .contains(tabData1, tabData2, tabData3);
        }

        @Test
        @DisplayName("should return new list instance")
        void shouldReturnNewListInstance() {
            manager.put(docRef1, tabData1);

            final List<DocumentTabData> result1 = manager.getAll();
            final List<DocumentTabData> result2 = manager.getAll();

            assertThat(result1)
                    .isNotSameAs(result2)
                    .isEqualTo(result2);
        }
    }
}
