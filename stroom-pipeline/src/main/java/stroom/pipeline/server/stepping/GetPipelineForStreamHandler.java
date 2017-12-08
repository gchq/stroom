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

package stroom.pipeline.server.stepping;

import org.springframework.context.annotation.Scope;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.Folder;
import stroom.entity.shared.SharedDocRef;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.pipeline.shared.FindPipelineEntityCriteria;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.pipeline.shared.stepping.GetPipelineForStreamAction;
import stroom.pool.SecurityHelper;
import stroom.query.api.v2.DocRef;
import stroom.security.SecurityContext;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamtask.shared.StreamProcessor;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.List;

@TaskHandlerBean(task = GetPipelineForStreamAction.class)
@Scope(value = StroomScope.TASK)
public class GetPipelineForStreamHandler extends AbstractTaskHandler<GetPipelineForStreamAction, SharedDocRef> {
    private final StreamStore streamStore;
    private final PipelineEntityService pipelineEntityService;
    private final FeedService feedService;
    private final SecurityContext securityContext;

    @Inject
    GetPipelineForStreamHandler(final StreamStore streamStore, final PipelineEntityService pipelineEntityService, final FeedService feedService, final SecurityContext securityContext) {
        this.streamStore = streamStore;
        this.pipelineEntityService = pipelineEntityService;
        this.feedService = feedService;
        this.securityContext = securityContext;
    }

    @Override
    public SharedDocRef exec(final GetPipelineForStreamAction action) {
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

        if (docRef == null) {
            // If we still don't have a pipeline docRef then just try and find the
            // first pipeline we can in the folder that the stream belongs
            // to.
            final Stream stream = getStream(action.getStreamId());
            if (stream != null) {
                final Folder folder = getFolder(stream);
                if (folder != null) {
                    final FindPipelineEntityCriteria findPipelineCriteria = new FindPipelineEntityCriteria();
                    findPipelineCriteria.getFolderIdSet().add(folder);
                    final List<PipelineEntity> pipelines = pipelineEntityService.find(findPipelineCriteria);
                    if (pipelines != null && pipelines.size() > 0) {
                        final PipelineEntity pipelineEntity = pipelines.get(0);
                        docRef = DocRefUtil.create(pipelineEntity);
                    }
                }
            }
        }

        return SharedDocRef.create(docRef);
    }

    private Stream getStream(final Long id) {
        Stream stream = null;
        if (id != null) {
            try (SecurityHelper securityHelper = SecurityHelper.asProcUser(securityContext)) {
                final FindStreamCriteria criteria = new FindStreamCriteria();
                criteria.getFetchSet().add(StreamProcessor.ENTITY_TYPE);
                criteria.getFetchSet().add(PipelineEntity.ENTITY_TYPE);
                criteria.getFetchSet().add(Feed.ENTITY_TYPE);
                criteria.getFetchSet().add(Folder.ENTITY_TYPE);
                criteria.obtainStreamIdSet().add(id);
                final List<Stream> streamList = streamStore.find(criteria);
                if (streamList != null && streamList.size() > 0) {
                    stream = streamList.get(0);
                }
            }
        }

        return stream;
    }

    private Stream getFirstChildStream(final Long id) {
        if (id != null) {
            try (SecurityHelper securityHelper = SecurityHelper.asProcUser(securityContext)) {
                final FindStreamCriteria criteria = new FindStreamCriteria();
                criteria.getFetchSet().add(StreamProcessor.ENTITY_TYPE);
                criteria.getFetchSet().add(PipelineEntity.ENTITY_TYPE);
                criteria.obtainParentStreamIdSet().add(id);
                return streamStore.find(criteria).getFirst();
            }
        }

        return null;
    }

    private DocRef getPipeline(final Stream stream) {
        DocRef docRef = null;

        // So we have got the stream so try and get the first pipeline that was
        // used to produce children for this stream.
        final StreamProcessor streamProcessor = stream.getStreamProcessor();
        if (streamProcessor != null) {
            PipelineEntity pipelineEntity = streamProcessor.getPipeline();
            if (pipelineEntity != null) {
                try {
                    // Ensure the current user is allowed to load this pipeline.
                    pipelineEntity = pipelineEntityService.loadByUuid(pipelineEntity.getUuid());
                    docRef = DocRefUtil.create(pipelineEntity);
                } catch (final Exception e) {
                    // Ignore.
                }
            }
        }

        return docRef;
    }

    private Folder getFolder(final Stream stream) {
        try (SecurityHelper securityHelper = SecurityHelper.asProcUser(securityContext)) {
            final Feed feed = feedService.load(stream.getFeed());
            if (feed != null) {
                return feed.getFolder();
            }
            return null;
        }
    }
}
