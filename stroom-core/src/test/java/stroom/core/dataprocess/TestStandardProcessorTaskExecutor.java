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

package stroom.core.dataprocess;

import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.feed.api.FeedProperties;
import stroom.feed.api.VolumeGroupNameProvider;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.node.api.NodeInfo;
import stroom.pipeline.ErrorWriterProxy;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.RecordErrorReceiver;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaData;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.state.SearchIdHolder;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.processor.api.ProcessorResult;
import stroom.processor.api.RunIndexing;
import stroom.processor.api.RunOutput;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.api.SimpleTaskContext;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tests what happens when errors can't be written to the error stream. The pipeline always has an error to write, as
/// it can't be found.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestStandardProcessorTaskExecutor {

    private static final String MESSAGE = "Unable to write the error stream";

    @Mock
    private Store store;
    @Mock
    private PipelineStore pipelineStore;
    @Mock
    private NodeInfo nodeInfo;
    @Mock
    private Source source;
    @Mock
    private Target target;
    @Mock
    private OutputStreamProvider outputStreamProvider;
    @Mock
    private SegmentOutputStream outputStream;

    private final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy();
    private final ErrorWriterProxy errorWriterProxy = new ErrorWriterProxy();
    private final RunOutput runOutput = new RunOutput();

    @BeforeEach
    void beforeEach() {
        Mockito.when(source.getMeta()).thenReturn(Meta.builder().id(1).feedName("FEED").build());
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("node");
        Mockito.when(store.openTarget(Mockito.any(), Mockito.any())).thenReturn(target);
        Mockito.when(target.getMeta()).thenReturn(Meta.builder().id(2).build());
        Mockito.when(target.getAttributes()).thenReturn(new AttributeMap());
        Mockito.when(target.next()).thenReturn(outputStreamProvider);
        Mockito.when(outputStreamProvider.get()).thenReturn(outputStream);
    }

    @Test
    void errorInThePipelineIsNotAFault() throws IOException {
        final ProcessorResult result = exec();

        assertThat(result.getMarkerCount(Severity.FATAL_ERROR)).isOne();
        Mockito.verify(outputStream).write(Mockito.any(byte[].class));
        Mockito.verify(target).close();
    }

    @Test
    void errorStreamIsRecordedAsOutputOfTheRun() {
        exec();

        assertThat(runOutput.getAllIds()).containsExactly(2L);
        assertThat(runOutput.getOutputIds()).isEmpty();
    }

    @Test
    void errorStreamThatCannotBeOpenedIsAFault() {
        Mockito.when(store.openTarget(Mockito.any(), Mockito.any())).thenThrow(new RuntimeException("Expected"));

        assertThatThrownBy(this::exec).hasMessageContaining(MESSAGE).hasMessageContaining("Expected");
    }

    @Test
    void errorStreamThatCannotBeWrittenToIsAFault() throws IOException {
        Mockito.doThrow(new IOException("Expected")).when(outputStream).write(Mockito.any(byte[].class));

        assertThatThrownBy(this::exec).hasMessageContaining(MESSAGE).hasMessageContaining("Expected");
        // It is still closed.
        Mockito.verify(target).close();
    }

    @Test
    void errorStreamWhoseLastWriteFailsIsAFault() throws IOException {
        Mockito.doThrow(new IOException("Expected")).when(outputStream).flush();

        assertThatThrownBy(this::exec).hasMessageContaining(MESSAGE).hasMessageContaining("Expected");
        Mockito.verify(target).close();
    }

    @Test
    void errorStreamThatCannotBeClosedIsAFault() throws IOException {
        Mockito.doThrow(new RuntimeException("Expected")).when(target).close();

        assertThatThrownBy(this::exec).hasMessageContaining(MESSAGE).hasMessageContaining("Expected");
    }

    @Test
    void errorReceiverThatFailsIsAFault() {
        // Nothing gets as far as the error stream.
        final RecordErrorReceiver recordErrorReceiver = Mockito.mock(RecordErrorReceiver.class);
        Mockito.doThrow(new RuntimeException("Expected")).when(recordErrorReceiver).log(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        assertThatThrownBy(() -> exec(recordErrorReceiver))
                .hasMessageContaining(MESSAGE)
                .hasMessageContaining("Expected");
    }

    private ProcessorResult exec() {
        return exec(new RecordErrorReceiver(errorWriterProxy));
    }

    private ProcessorResult exec(final RecordErrorReceiver recordErrorReceiver) {
        final StandardProcessorTaskExecutor executor = new StandardProcessorTaskExecutor(
                Mockito.mock(PipelineFactory.class),
                store,
                pipelineStore,
                Mockito.mock(PipelineHolder.class),
                Mockito.mock(FeedHolder.class),
                Mockito.mock(FeedProperties.class),
                Mockito.mock(MetaDataHolder.class),
                Mockito.mock(MetaHolder.class),
                Mockito.mock(SearchIdHolder.class),
                Mockito.mock(LocationFactoryProxy.class),
                Mockito.mock(StreamProcessorHolder.class),
                errorReceiverProxy,
                errorWriterProxy,
                new MetaData(),
                new RecordCount(),
                recordErrorReceiver,
                nodeInfo,
                Mockito.mock(PipelineDataCache.class),
                Mockito.mock(InternalStatisticsReceiver.class),
                Mockito.mock(VolumeGroupNameProvider.class));
        final Processor processor = Processor.builder().id(1).pipelineUuid("pipeline").build();
        return executor.exec(
                new SimpleTaskContext(),
                processor,
                ProcessorFilter.builder().id(1).processor(processor).build(),
                ProcessorTask.builder().id(1).build(),
                source,
                runOutput,
                new RunIndexing());
    }
}
