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
 *
 */

package stroom.pipeline.stepping;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.shared.SharedDocRef;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.GetPipelineForStreamAction;
import stroom.security.Security;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.meta.api.StreamMetaService;
import stroom.streamstore.shared.ExpressionUtil;
import stroom.streamstore.meta.api.FindStreamCriteria;
import stroom.streamstore.meta.api.Stream;
import stroom.streamtask.shared.Processor;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;

import javax.inject.Inject;
import java.util.List;

@TaskHandlerBean(task = GetPipelineForStreamAction.class)
class GetPipelineForStreamHandler extends AbstractTaskHandler<GetPipelineForStreamAction, SharedDocRef> {
    private final StreamStore streamStore;
    private final StreamMetaService streamMetaService;
    private final PipelineStore pipelineStore;
    private final Security security;

    @Inject
    GetPipelineForStreamHandler(final StreamStore streamStore,
                                final StreamMetaService streamMetaService,
                                final PipelineStore pipelineStore,
                                final Security security) {
        this.streamStore = streamStore;
        this.streamMetaService = streamMetaService;
        this.pipelineStore = pipelineStore;
        this.security = security;
    }

    @Override
    public SharedDocRef exec(final GetPipelineForStreamAction action) {
        return security.secureResult(() -> {
            DocRef docRef = null;

            // First try and get the pipeline from the selected child stream.
            Stream childStream = getStream(action.getChildStreamId());
            if (childStream != null) {
                docRef = getPipeline(childStream);
            }

            if (docRef == null) {
                // If we didn't get a pipeline docRef from a child stream then try and
                // find a child stream to get one from.
                childStream = getFirstChildStream(action.getStreamId());
                if (childStream != null) {
                    docRef = getPipeline(childStream);
                }
            }

//        if (docRef == null) {
//            // If we still don't have a pipeline docRef then just try and find the
//            // first pipeline we can in the folder that the stream belongs
//            // to.
//            final Stream stream = getStream(action.getStreamId());
//            if (stream != null) {
//                final Feed feed = feedService.load(stream.getFeed());
//                if (feed != null) {
//
//
//                    final Folder folder = feed.getFolder();
//                    if (folder != null) {
//                        final FindPipelineEntityCriteria findPipelineCriteria = new FindPipelineEntityCriteria();
//                        findPipelineCriteria.getFolderIdSet().add(folder);
//                        final List<PipelineEntity> pipelines = pipelineStore.find(findPipelineCriteria);
//                        if (pipelines != null && pipelines.size() > 0) {
//                            final PipelineEntity pipelineDoc = pipelines.get(0);
//                            docRef = DocRefUtil.create(pipelineDoc);
//                        }
//                    }
//                }
//            }
//        }

            return SharedDocRef.create(docRef);
        });
    }

    private Stream getStream(final Long id) {
        if (id == null) {
            return null;
        }

        return security.asProcessingUserResult(() -> {
            final FindStreamCriteria criteria = new FindStreamCriteria();
            criteria.setExpression(ExpressionUtil.createStreamExpression(id));
            criteria.getFetchSet().add(Processor.ENTITY_TYPE);
            criteria.getFetchSet().add(PipelineDoc.DOCUMENT_TYPE);
            criteria.getFetchSet().add(FeedDoc.DOCUMENT_TYPE);

            final List<Stream> streamList = streamMetaService.find(criteria);
            if (streamList != null && streamList.size() > 0) {
                return streamList.get(0);
            }

            return null;
        });
    }

    private Stream getFirstChildStream(final Long id) {
        if (id == null) {
            return null;
        }

        return security.asProcessingUserResult(() -> {
            final FindStreamCriteria criteria = new FindStreamCriteria();
            criteria.setExpression(ExpressionUtil.createParentStreamExpression(id));
            criteria.getFetchSet().add(Processor.ENTITY_TYPE);
            criteria.getFetchSet().add(PipelineDoc.DOCUMENT_TYPE);

            return streamMetaService.find(criteria).getFirst();
        });
    }

    private DocRef getPipeline(final Stream stream) {
        DocRef docRef = null;

        // So we have got the stream so try and get the first pipeline that was
        // used to produce children for this stream.
        String pipelineUuid = stream.getPipelineUuid();
        if (pipelineUuid != null) {
            try {
                // Ensure the current user is allowed to load this pipeline.
                final PipelineDoc pipelineDoc = pipelineStore.readDocument(new DocRef(PipelineDoc.DOCUMENT_TYPE, pipelineUuid));
                docRef = DocRefUtil.create(pipelineDoc);
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }

        return docRef;
    }
}
