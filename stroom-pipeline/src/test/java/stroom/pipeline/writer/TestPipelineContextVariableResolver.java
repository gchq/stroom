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

package stroom.pipeline.writer;

import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.node.api.NodeInfo;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.SearchIdHolder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestPipelineContextVariableResolver {

    @Mock
    private FeedHolder mockFeedHolder;
    @Mock
    private PipelineHolder mockPipelineHolder;
    @Mock
    private MetaHolder mockMetaHolder;
    @Mock
    private SearchIdHolder mockSearchIdHolder;
    @Mock
    private NodeInfo mockNodeInfo;
    @Mock
    private Meta mockMeta;
    @Mock
    private FeedHolder mockFeedHolder2;

    @Test
    void testAllNullProvides() {
        final PipelineContextVariableResolver resolver = new PipelineContextVariableResolver(
                null,
                null,
                null,
                null,
                null);
        asserAllEmpty(resolver);
    }

    @Test
    void testAllNullHolders() {
        final PipelineContextVariableResolver resolver = new PipelineContextVariableResolver(
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null);
        asserAllEmpty(resolver);
    }

    @Test
    void testAll() {
        final DocRef pipeDocRef = PipelineDoc.buildDocRef()
                .randomUuid()
                .name("pipe1")
                .build();
        final PipelineContextVariableResolver resolver = new PipelineContextVariableResolver(
                () -> mockFeedHolder,
                () -> mockPipelineHolder,
                () -> mockMetaHolder,
                () -> mockSearchIdHolder,
                () -> mockNodeInfo);

        Mockito.when(mockFeedHolder.getFeedName())
                .thenReturn("FEED1");
        Mockito.when(mockPipelineHolder.getPipeline())
                .thenReturn(pipeDocRef);
        Mockito.when(mockMetaHolder.getMeta())
                .thenReturn(mockMeta);
        Mockito.when(mockMeta.getId())
                .thenReturn(123L);
        Mockito.when(mockMetaHolder.getPartNo())
                .thenReturn(3L);
        Mockito.when(mockSearchIdHolder.getSearchId())
                .thenReturn("search1");
        Mockito.when(mockNodeInfo.getThisNodeName())
                .thenReturn("node1");

        assertThat(resolver.getVariableValue("foo"))
                .isEmpty();
        assertThat(resolver.getVariableValue("feed"))
                .hasValue("FEED1");
        assertThat(resolver.getVariableValue("pipeline"))
                .hasValue("pipe1");
        assertThat(resolver.getVariableValue("sourceId"))
                .hasValue("123");
        assertThat(resolver.getVariableValue("streamId"))
                .hasValue("123");
        assertThat(resolver.getVariableValue("partNo"))
                .hasValue("3");
        assertThat(resolver.getVariableValue("streamNo"))
                .hasValue("3");
        assertThat(resolver.getVariableValue("searchId"))
                .hasValue("search1");
        assertThat(resolver.getVariableValue("node"))
                .hasValue("node1");
    }

    @Test
    void testAll_providerChange() {
        final DocRef pipeDocRef = PipelineDoc.buildDocRef()
                .randomUuid()
                .name("pipe1")
                .build();
        final AtomicReference<FeedHolder> feedHolderProvider = new AtomicReference<>(mockFeedHolder);

        final PipelineContextVariableResolver resolver = new PipelineContextVariableResolver(
                feedHolderProvider::get,
                () -> mockPipelineHolder,
                () -> mockMetaHolder,
                () -> mockSearchIdHolder,
                () -> mockNodeInfo);

        Mockito.when(mockFeedHolder.getFeedName())
                .thenReturn("FEED1");
        Mockito.when(mockPipelineHolder.getPipeline())
                .thenReturn(pipeDocRef);
        Mockito.when(mockMetaHolder.getMeta())
                .thenReturn(mockMeta);
        Mockito.when(mockMeta.getId())
                .thenReturn(123L);
        Mockito.when(mockMetaHolder.getPartNo())
                .thenReturn(3L);
        Mockito.when(mockSearchIdHolder.getSearchId())
                .thenReturn("search1");
        Mockito.when(mockNodeInfo.getThisNodeName())
                .thenReturn("node1");

        assertThat(resolver.getVariableValue("foo"))
                .isEmpty();
        assertThat(resolver.getVariableValue("feed"))
                .hasValue("FEED1");
        assertThat(resolver.getVariableValue("pipeline"))
                .hasValue("pipe1");
        assertThat(resolver.getVariableValue("sourceId"))
                .hasValue("123");
        assertThat(resolver.getVariableValue("streamId"))
                .hasValue("123");
        assertThat(resolver.getVariableValue("partNo"))
                .hasValue("3");
        assertThat(resolver.getVariableValue("streamNo"))
                .hasValue("3");
        assertThat(resolver.getVariableValue("searchId"))
                .hasValue("search1");
        assertThat(resolver.getVariableValue("node"))
                .hasValue("node1");

        feedHolderProvider.set(mockFeedHolder2);

        Mockito.when(mockFeedHolder2.getFeedName())
                .thenReturn("FEED2");

        assertThat(resolver.getVariableValue("foo"))
                .isEmpty();
        assertThat(resolver.getVariableValue("feed"))
                .hasValue("FEED2");
        assertThat(resolver.getVariableValue("pipeline"))
                .hasValue("pipe1");
        assertThat(resolver.getVariableValue("sourceId"))
                .hasValue("123");
        assertThat(resolver.getVariableValue("streamId"))
                .hasValue("123");
        assertThat(resolver.getVariableValue("partNo"))
                .hasValue("3");
        assertThat(resolver.getVariableValue("streamNo"))
                .hasValue("3");
        assertThat(resolver.getVariableValue("searchId"))
                .hasValue("search1");
        assertThat(resolver.getVariableValue("node"))
                .hasValue("node1");
    }

    @Test
    void testAll_intermediateNulls() {
        final PipelineContextVariableResolver resolver = new PipelineContextVariableResolver(
                () -> mockFeedHolder,
                () -> mockPipelineHolder,
                () -> mockMetaHolder,
                () -> mockSearchIdHolder,
                () -> mockNodeInfo);

        Mockito.when(mockFeedHolder.getFeedName())
                .thenReturn(null);
        Mockito.when(mockPipelineHolder.getPipeline())
                .thenReturn(null);
        Mockito.when(mockMetaHolder.getMeta())
                .thenReturn(null);
        Mockito.when(mockMetaHolder.getPartNo())
                .thenReturn(3L);
        Mockito.when(mockSearchIdHolder.getSearchId())
                .thenReturn(null);
        Mockito.when(mockNodeInfo.getThisNodeName())
                .thenReturn(null);

        assertThat(resolver.getVariableValue("foo"))
                .isEmpty();
        assertThat(resolver.getVariableValue("feed"))
                .isEmpty();
        assertThat(resolver.getVariableValue("pipeline"))
                .isEmpty();
        assertThat(resolver.getVariableValue("sourceId"))
                .isEmpty();
        assertThat(resolver.getVariableValue("streamId"))
                .isEmpty();
        assertThat(resolver.getVariableValue("partNo"))
                .hasValue("3");
        assertThat(resolver.getVariableValue("streamNo"))
                .hasValue("3");
        assertThat(resolver.getVariableValue("searchId"))
                .isEmpty();
        assertThat(resolver.getVariableValue("node"))
                .isEmpty();
    }

    private static void asserAllEmpty(final PipelineContextVariableResolver resolver) {
        assertThat(resolver.getVariableValue("foo"))
                .isEmpty();
        assertThat(resolver.getVariableValue("feed"))
                .isEmpty();
        assertThat(resolver.getVariableValue("pipeline"))
                .isEmpty();
        assertThat(resolver.getVariableValue("sourceId"))
                .isEmpty();
        assertThat(resolver.getVariableValue("streamId"))
                .isEmpty();
        assertThat(resolver.getVariableValue("partNo"))
                .isEmpty();
        assertThat(resolver.getVariableValue("streamNo"))
                .isEmpty();
        assertThat(resolver.getVariableValue("searchId"))
                .isEmpty();
        assertThat(resolver.getVariableValue("node"))
                .isEmpty();
    }
}
