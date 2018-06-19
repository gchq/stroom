/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.streamtask;

import com.google.common.base.Strings;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.CriteriaSet;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.data.meta.api.FindStreamCriteria;
import stroom.data.meta.api.Stream;
import stroom.data.meta.api.StreamMetaService;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.shared.ReprocessDataInfo;
import stroom.data.meta.api.StreamDataSource;
import stroom.streamtask.shared.Processor;
import stroom.streamtask.shared.ReprocessDataAction;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.Severity;
import stroom.util.shared.SharedList;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TaskHandlerBean(task = ReprocessDataAction.class)
class ReprocessDataHandler extends AbstractTaskHandler<ReprocessDataAction, SharedList<ReprocessDataInfo>> {
    private static final int MAX_STREAM_TO_REPROCESS = 1000;

    private final StreamProcessorService streamProcessorService;
    private final StreamProcessorFilterService streamProcessorFilterService;
    private final StreamMetaService streamMetaService;
    private final Security security;

    @Inject
    ReprocessDataHandler(final StreamProcessorService streamProcessorService,
                         final StreamProcessorFilterService streamProcessorFilterService,
                         final StreamMetaService streamMetaService,
                         final Security security) {
        this.streamProcessorService = streamProcessorService;
        this.streamProcessorFilterService = streamProcessorFilterService;
        this.streamMetaService = streamMetaService;
        this.security = security;
    }

    @Override
    public SharedList<ReprocessDataInfo> exec(final ReprocessDataAction action) {
        return security.secureResult(PermissionNames.MANAGE_PROCESSORS_PERMISSION, () -> {
            final List<ReprocessDataInfo> info = new ArrayList<>();

            try {
                final FindStreamCriteria criteria = action.getCriteria();
                // We only want 1000 streams to be
                // reprocessed at a maximum.
                criteria.obtainPageRequest().setOffset(0L);
                criteria.obtainPageRequest().setLength(MAX_STREAM_TO_REPROCESS);

                final BaseResultList<Stream> streams = streamMetaService.find(criteria);

                if (!streams.isExact()) {
                    info.add(new ReprocessDataInfo(Severity.ERROR, "Results exceed " + MAX_STREAM_TO_REPROCESS
                            + " configure a pipeline processor for large data sets", null));

                } else {
                    int skippingCount = 0;
                    final StringBuilder unableListSB = new StringBuilder();
                    final StringBuilder submittedListSB = new StringBuilder();

                    final Map<Processor, CriteriaSet<Long>> streamToProcessorSet = new HashMap<>();

                    for (final Stream stream : streams) {
                        // We can only reprocess streams that have a stream
                        // processor and a parent stream id.
                        if (stream.getStreamProcessorId() != null && stream.getParentStreamId() != null) {
                            final Processor streamProcessor = streamProcessorService.loadByIdInsecure(stream.getStreamProcessorId());
                            streamToProcessorSet.computeIfAbsent(streamProcessor, k -> new CriteriaSet<>()).add(stream.getParentStreamId());
                        } else {
                            skippingCount++;
                        }
                    }

                    final List<Processor> list = new ArrayList<>(streamToProcessorSet.keySet());
                    list.sort(Comparator.comparing(Processor::getPipelineUuid));

                    for (final Processor streamProcessor : list) {
                        final CriteriaSet<Long> streamIdSet = streamToProcessorSet.get(streamProcessor);

                        final QueryData queryData = new QueryData();
                        final ExpressionOperator.Builder operator = new ExpressionOperator.Builder(ExpressionOperator.Op.AND);

                        final ExpressionOperator.Builder streamIdTerms = new ExpressionOperator.Builder(ExpressionOperator.Op.OR);
                        streamIdSet.forEach(streamId -> streamIdTerms.addTerm(StreamDataSource.STREAM_ID, ExpressionTerm.Condition.EQUALS, Long.toString(streamId)));
                        operator.addOperator(streamIdTerms.build());

                        queryData.setDataSource(StreamDataSource.STREAM_STORE_DOC_REF);
                        queryData.setExpression(operator.build());

                        if (!streamProcessor.isEnabled()) {
                            unableListSB.append(streamProcessor.getPipelineUuid());
                            unableListSB.append("\n");

                        } else {
                            final String padded = Strings.padEnd(streamProcessor.getPipelineUuid(), 40, ' ');
                            submittedListSB.append(padded);
                            submittedListSB.append("\t");
                            submittedListSB.append(streamIdSet.size());
                            submittedListSB.append(" streams\n");

                            streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 10, queryData);
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
            } catch (final RuntimeException e) {
                info.add(new ReprocessDataInfo(Severity.ERROR, e.getMessage(), null));
            }

            return new SharedList<>(info);
        });
    }
}
