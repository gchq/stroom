/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamstore.server;

import org.apache.commons.lang.StringUtils;
import org.springframework.context.annotation.Scope;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.EntityIdSet;
import stroom.pipeline.shared.PipelineEntity;
import stroom.security.Secured;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.ReprocessDataAction;
import stroom.streamstore.shared.ReprocessDataInfo;
import stroom.streamstore.shared.Stream;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilterService;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.Severity;
import stroom.util.shared.SharedList;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TaskHandlerBean(task = ReprocessDataAction.class)
@Scope(StroomScope.TASK)
@Secured(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)
public class ReprocessDataHandler extends AbstractTaskHandler<ReprocessDataAction, SharedList<ReprocessDataInfo>> {
    public static final int MAX_STREAM_TO_REPROCESS = 1000;

    @Resource
    private StreamProcessorFilterService streamProcessorFilterService;
    @Resource
    private StreamStore streamStore;

    @Override
    public SharedList<ReprocessDataInfo> exec(final ReprocessDataAction action) {
        final List<ReprocessDataInfo> info = new ArrayList<ReprocessDataInfo>();

        try {
            final FindStreamCriteria criteria = action.getCriteria();

            criteria.getFetchSet().add(StreamProcessor.ENTITY_TYPE);
            criteria.getFetchSet().add(PipelineEntity.ENTITY_TYPE);
            // We only want 1000 streams to be
            // reprocessed at a maximum.
            criteria.obtainPageRequest().setOffset(0L);
            criteria.obtainPageRequest().setLength(MAX_STREAM_TO_REPROCESS);

            final BaseResultList<Stream> streams = streamStore.find(criteria);

            if (!streams.isExact()) {
                info.add(new ReprocessDataInfo(Severity.ERROR, "Results exceed " + MAX_STREAM_TO_REPROCESS
                        + " configure a pipeline processor for large data sets", null));

            } else {
                int skippingCount = 0;
                final StringBuilder unableListSB = new StringBuilder();
                final StringBuilder submittedListSB = new StringBuilder();

                final Map<StreamProcessor, EntityIdSet<Stream>> streamToProcessorSet = new HashMap<>();

                for (final Stream stream : streams) {
                    // We can only reprocess streams that have a stream
                    // processor and a parent stream id.
                    if (stream.getStreamProcessor() != null && stream.getParentStreamId() != null) {
                        EntityIdSet<Stream> streamSet = streamToProcessorSet.get(stream.getStreamProcessor());
                        if (streamSet == null) {
                            streamSet = new EntityIdSet<Stream>();
                            streamToProcessorSet.put(stream.getStreamProcessor(), streamSet);
                        }

                        streamSet.add(stream.getParentStreamId());
                    } else {
                        skippingCount++;
                    }
                }

                final List<StreamProcessor> list = new ArrayList<StreamProcessor>(streamToProcessorSet.keySet());
                Collections.sort(list, (o1, o2) -> o1.getPipeline().getName().compareTo(o2.getPipeline().getName()));

                for (final StreamProcessor streamProcessor : list) {
                    final EntityIdSet<Stream> streamSet = streamToProcessorSet.get(streamProcessor);

                    final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
                    findStreamCriteria.setStreamIdSet(streamSet);

                    if (!streamProcessor.isEnabled()) {
                        unableListSB.append(streamProcessor.getPipeline().getName());
                        unableListSB.append("\n");

                    } else {
                        final String padded = StringUtils.rightPad(streamProcessor.getPipeline().getName(), 40, ' ');
                        submittedListSB.append(padded);
                        submittedListSB.append("\t");
                        submittedListSB.append(streamSet.size());
                        submittedListSB.append(" streams\n");

                        streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 10, findStreamCriteria);
                    }
                }

                if (skippingCount > 0) {
                    info.add(new ReprocessDataInfo(Severity.INFO,
                            "Skipping " + skippingCount + " streams that are not a result of processing", null));
                }

                final String unableList = unableListSB.toString().trim();
                if (unableList.length() > 0) {
                    info.add(new ReprocessDataInfo(Severity.WARNING,
                            "Unable to reprocess all streams as some pipelines are not enabled", unableList));
                }

                final String submittedList = submittedListSB.toString().trim();
                if (submittedList.length() > 0) {
                    info.add(new ReprocessDataInfo(Severity.INFO, "Created new processor filters to reprocess streams",
                            submittedList));
                }
            }
        } catch (final Exception ex) {
            info.add(new ReprocessDataInfo(Severity.ERROR, ex.getMessage(), null));
        }

        return new SharedList<ReprocessDataInfo>(info);
    }
}
