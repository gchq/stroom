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
 */

package stroom.pipeline.stepping;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.explorer.shared.SharedDocRef;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.pipeline.PipelineEventLog;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.GetPipelineForMetaRequest;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResource;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskManager;
import stroom.util.HasHealthCheck;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import java.util.List;

class SteppingResourceImpl implements SteppingResource, RestResource, HasHealthCheck {
    private final MetaService metaService;
    private final PipelineStore pipelineStore;
    private final TaskManager taskManager;
    private final PipelineEventLog pipelineEventLog;
    private final SecurityContext securityContext;

    @Inject
    SteppingResourceImpl(final MetaService metaService,
                         final PipelineStore pipelineStore,
                         final TaskManager taskManager,
                         final PipelineEventLog pipelineEventLog,
                         final SecurityContext securityContext) {
        this.metaService = metaService;
        this.pipelineStore = pipelineStore;
        this.taskManager = taskManager;
        this.pipelineEventLog = pipelineEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public DocRef getPipelineForStepping(final GetPipelineForMetaRequest request) {
        return securityContext.secureResult(() -> {
            DocRef docRef = null;

            // First try and get the pipeline from the selected child stream.
            Meta childMeta = getMeta(request.getChildMetaId());
            if (childMeta != null) {
                docRef = getPipeline(childMeta);
            }

            if (docRef == null) {
                // If we didn't get a pipeline docRef from a child stream then try and
                // find a child stream to get one from.
                childMeta = getFirstChildMeta(request.getMetaId());
                if (childMeta != null) {
                    docRef = getPipeline(childMeta);
                }
            }

//        if (docRef == null) {
//            // If we still don't have a pipeline docRef then just try and find the
//            // first pipeline we can in the folder that the stream belongs
//            // to.
//            final Stream stream = getMeta(action.getMetaId());
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

    @Override
    public SteppingResult step(final PipelineStepRequest request) {
        SteppingResult result = null;
        StepLocation stepLocation = request.getStepLocation();

        try {
            // Copy the action settings to the server task.
            final SteppingTask task = new SteppingTask();
            task.setCriteria(request.getCriteria());
            task.setChildStreamType(request.getChildStreamType());
            task.setStepLocation(request.getStepLocation());
            task.setStepFilterMap(request.getStepFilterMap());
            task.setStepType(request.getStepType());
            task.setPipeline(request.getPipeline());
            task.setCode(request.getCode());

            // Make sure stepping can only happen on streams that are visible to
            // the user.
            // FIXME : Constrain available streams.
            // folderValidator.constrainCriteria(task.getCriteria());

            // Execute the stepping task.
            result = taskManager.exec(task);
            if (result.getStepLocation() != null) {
                stepLocation = result.getStepLocation();
            }

            if (stepLocation != null) {
                pipelineEventLog.stepStream(stepLocation.getEventId(), null, request.getChildStreamType(), request.getPipeline(), null);
            }
        } catch (final RuntimeException e) {
            if (stepLocation != null) {
                pipelineEventLog.stepStream(stepLocation.getEventId(), null, request.getChildStreamType(), request.getPipeline(), e);
            }
        }

        return result;
    }

    private Meta getMeta(final Long id) {
        if (id == null) {
            return null;
        }

        return securityContext.asProcessingUserResult(() -> {
            final FindMetaCriteria criteria = new FindMetaCriteria(MetaExpressionUtil.createDataIdExpression(id));
            final List<Meta> streamList = metaService.find(criteria).getValues();
            if (streamList != null && streamList.size() > 0) {
                return streamList.get(0);
            }

            return null;
        });
    }

    private Meta getFirstChildMeta(final Long id) {
        if (id == null) {
            return null;
        }

        return securityContext.asProcessingUserResult(() -> {
            final FindMetaCriteria criteria = new FindMetaCriteria(MetaExpressionUtil.createParentIdExpression(id));
            return metaService.find(criteria).getFirst();
        });
    }

    private DocRef getPipeline(final Meta meta) {
        DocRef docRef = null;

        // So we have got the stream so try and get the first pipeline that was
        // used to produce children for this stream.
        String pipelineUuid = meta.getPipelineUuid();
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

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}