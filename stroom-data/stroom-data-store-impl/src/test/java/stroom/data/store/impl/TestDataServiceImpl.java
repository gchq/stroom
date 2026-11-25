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

package stroom.data.store.impl;

import stroom.data.shared.DataInfoSection;
import stroom.data.store.api.AttributeMapFactory;
import stroom.docstore.api.DocFinder;
import stroom.feed.api.FeedProperties;
import stroom.feed.api.FeedStore;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestDataServiceImpl {

    private static final long META_ID = 123;
    private static final String FEED_NAME = "MY_FEED";

    @Mock
    private MetaService metaService;
    @Mock
    private AttributeMapFactory attributeMapFactory;
    @Mock
    private FeedStore feedStore;
    @Mock
    private FeedProperties feedProperties;
    @Mock
    private DocFinder docFinder;
    @Mock
    private DataVolumeService dataVolumeService;

    @Test
    void testInfoHasClassification() {
        final Meta meta = Meta.builder()
                .id(META_ID)
                .feedName(FEED_NAME)
                .typeName("Events")
                .status(Status.UNLOCKED)
                .createMs(0L)
                .build();
        Mockito.when(metaService.findDecoratedRows(Mockito.any(FindMetaCriteria.class)))
                .thenReturn(new ResultPage<>(List.of(new MetaRow(meta, null, new HashMap<>()))));
        Mockito.when(attributeMapFactory.getAttributes(META_ID)).thenReturn(new HashMap<>());
        Mockito.when(feedProperties.getDisplayClassification(FEED_NAME)).thenReturn("MY CLASSIFICATION");

        assertThat(findClassification(createService().info(META_ID))).contains("MY CLASSIFICATION");
    }

    @Test
    void testInfoOfMissingStreamHasNoClassification() {
        Mockito.when(metaService.findDecoratedRows(Mockito.any(FindMetaCriteria.class)))
                .thenReturn(ResultPage.empty());

        final List<DataInfoSection> sections = createService().info(META_ID);

        assertThat(sections).hasSize(1);
        assertThat(findClassification(sections)).isEmpty();
        Mockito.verifyNoInteractions(feedProperties);
    }

    private static Optional<String> findClassification(final List<DataInfoSection> sections) {
        return sections.stream()
                .filter(section -> DataInfoSection.STREAM.equals(section.getTitle()))
                .flatMap(section -> section.getEntries().stream())
                .filter(entry -> DataInfoSection.CLASSIFICATION.equals(entry.getKey()))
                .map(DataInfoSection.Entry::getValue)
                .findFirst();
    }

    private DataServiceImpl createService() {
        return new DataServiceImpl(
                null,
                null,
                null,
                metaService,
                attributeMapFactory,
                null,
                feedStore,
                null,
                feedProperties,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                docFinder,
                dataVolumeService);
    }
}
