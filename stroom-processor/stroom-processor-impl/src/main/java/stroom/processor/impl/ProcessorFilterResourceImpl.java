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

package stroom.processor.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.event.logging.api.DocumentEventLog;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.CreateProcessorFilterRequest;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.FetchProcessorResponse;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.RestResource;

import javax.inject.Inject;

// TODO : @66 add event logging
public class ProcessorFilterResourceImpl implements ProcessorFilterResource, RestResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorFilterResourceImpl.class);

    private final ProcessorFilterService processorFilterService;
    private final DocumentEventLog documentEventLog;

    @Inject
    public ProcessorFilterResourceImpl(final ProcessorFilterService processorFilterService,
                                       final DocumentEventLog documentEventLog) {
        this.processorFilterService = processorFilterService;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public ProcessorFilter create(final CreateProcessorFilterRequest request) {
        return processorFilterService.create(request.getPipeline(), request.getQueryData(), request.getPriority(), request.isEnabled());
    }

    @Override
    public ProcessorFilter read(final Integer id) {
        return processorFilterService.fetch(id).orElse(null);
    }

    @Override
    public ProcessorFilter update(final Integer id, final ProcessorFilter processorFilter) {
        return processorFilterService.update(processorFilter);
    }

    @Override
    public void delete(final Integer id) {
        processorFilterService.delete(id);
    }

    @Override
    public void setPriority(final Integer id, final Integer priority) {
        processorFilterService.setPriority(id, priority);
    }

    @Override
    public void setEnabled(final Integer id, final Boolean enabled) {
        processorFilterService.setEnabled(id, enabled);
    }

    @Override
    public FetchProcessorResponse find(final FetchProcessorRequest request) {
        return processorFilterService.find(request);
    }

    //    @Override
//    public AnnotationDetail get(final Long annotationId) {
//        AnnotationDetail annotationDetail = null;
//
////        if (annotationId != null) {
//        LOGGER.info(() -> "Getting annotation " + annotationId);
//        try {
//            annotationDetail = annotationService.getDetail(annotationId);
//            if (annotationDetail != null) {
//                documentEventLog.view(annotationDetail, null);
//            }
//        } catch (final RuntimeException e) {
//            documentEventLog.view("Annotation " + annotationId, e);
//        }
////        } else {
////            LOGGER.info(() -> "Getting annotation " + streamId + ":" + eventId);
////            try {
////                annotationDetail = annotationService.getDetail(streamId, eventId);
////                if (annotationDetail != null) {
////                    documentEventLog.view(annotationDetail, null);
////                }
////            } catch (final RuntimeException e) {
////                documentEventLog.view("Annotation " + streamId + ":" + eventId, e);
////            }
////        }
//
//        return annotationDetail;
//    }
//
//    @Override
//    public AnnotationDetail createEntry(final CreateEntryRequest request) {
//        AnnotationDetail annotationDetail = null;
//
//        LOGGER.info(() -> "Creating annotation entry " + request.getAnnotation());
//        try {
//            annotationDetail = annotationService.createEntry(request);
//            documentEventLog.create(annotationDetail, null);
//        } catch (final RuntimeException e) {
//            documentEventLog.create("Annotation entry " + request.getAnnotation(), e);
//        }
//
//        return annotationDetail;
//    }
//
//    @Override
//    public List<String> getStatus(final String filter) {
//        final List<String> values = annotationConfig.getStatusValues();
//        if (filter == null || filter.isEmpty()) {
//            return values;
//        }
//
//        return values
//                .stream()
//                .filter(value -> value.toLowerCase().contains(filter.toLowerCase()))
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<String> getComment(final String filter) {
//        final List<String> values = annotationConfig.getStandardComments();
//        if (filter == null || filter.isEmpty()) {
//            return values;
//        }
//
//        return values
//                .stream()
//                .filter(value -> value.toLowerCase().contains(filter.toLowerCase()))
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<EventId> getLinkedEvents(final Long annotationId) {
//        return annotationService.getLinkedEvents(annotationId);
//    }
//
//    @Override
//    public List<EventId> link(final EventLink eventLink) {
//        return annotationService.link(eventLink);
//    }
//
//    @Override
//    public List<EventId> unlink(final EventLink eventLink) {
//        return annotationService.unlink(eventLink);
//    }
//
//    @Override
//    public Integer setStatus(final SetStatusRequest request) {
//        return annotationService.setStatus(request);
//    }
//
//    @Override
//    public Integer setAssignedTo(final SetAssignedToRequest request) {
//        return annotationService.setAssignedTo(request);
//    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}