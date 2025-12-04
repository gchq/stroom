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

package stroom.annotation.client;

import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.ChangeAnnotationEntryRequest;
import stroom.annotation.shared.CreateAnnotationRequest;
import stroom.annotation.shared.CreateAnnotationTagRequest;
import stroom.annotation.shared.DeleteAnnotationEntryRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.FetchAnnotationEntryRequest;
import stroom.annotation.shared.FindAnnotationRequest;
import stroom.annotation.shared.MultiAnnotationChangeRequest;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.security.client.presenter.AbstractRestClient;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;

public class AnnotationResourceClient extends AbstractRestClient {

    private static final AnnotationResource ANNOTATION_RESOURCE = GWT.create(AnnotationResource.class);

    @Inject
    public AnnotationResourceClient(final EventBus eventBus,
                                    final RestFactory restFactory) {
        super(eventBus, restFactory);
    }

    public void findAnnotations(final FindAnnotationRequest request,
                                final Consumer<ResultPage<Annotation>> consumer,
                                final RestErrorHandler errorHandler,
                                final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.findAnnotations(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

//    public void getAnnotationByRef(final DocRef annotationRef,
//                                   final Consumer<Annotation> consumer,
//                                   final TaskMonitorFactory taskMonitorFactory) {
//        restFactory
//                .create(ANNOTATION_RESOURCE)
//                .method(res -> res.getAnnotationByRef(annotationRef))
//                .onSuccess(consumer)
//                .taskMonitorFactory(taskMonitorFactory)
//                .exec();
//    }

    public void getAnnotationById(final long annotationId,
                                  final Consumer<Annotation> consumer,
                                  final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.getAnnotationById(annotationId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void getAnnotationEntries(final DocRef annotationRef,
                                     final Consumer<List<AnnotationEntry>> consumer,
                                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.getAnnotationEntries(annotationRef))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void getStandardComments(final String filter,
                                    final Consumer<List<String>> consumer,
                                    final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.getStandardComments(filter))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void createAnnotation(final CreateAnnotationRequest request,
                                 final Consumer<Annotation> consumer,
                                 final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.createAnnotation(request))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void change(final SingleAnnotationChangeRequest request,
                       final Consumer<Boolean> consumer,
                       final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.change(request))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void batchChange(final MultiAnnotationChangeRequest request,
                            final Consumer<Integer> consumer,
                            final RestErrorHandler errorHandler,
                            final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.batchChange(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void getLinkedEvents(final DocRef annotationRef,
                                final Consumer<List<EventId>> consumer,
                                final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.getLinkedEvents(annotationRef))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void delete(final DocRef annotationRef,
                       final Consumer<Boolean> consumer,
                       final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.deleteAnnotation(annotationRef))
                .onSuccess(consumer)
                .onFailure(new DefaultErrorHandler(this, () -> consumer.accept(false)))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void createAnnotationTag(final CreateAnnotationTagRequest request,
                                    final Consumer<AnnotationTag> consumer,
                                    final RestErrorHandler errorHandler,
                                    final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.createAnnotationTag(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void updateAnnotationTag(final AnnotationTag annotationCollection,
                                    final Consumer<AnnotationTag> consumer,
                                    final RestErrorHandler errorHandler,
                                    final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.updateAnnotationTag(annotationCollection))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void deleteAnnotationGroup(final AnnotationTag annotationCollection,
                                      final Consumer<Boolean> consumer,
                                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.deleteAnnotationTag(annotationCollection))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void findAnnotationTags(final ExpressionCriteria request,
                                   final Consumer<ResultPage<AnnotationTag>> consumer,
                                   final RestErrorHandler errorHandler,
                                   final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.findAnnotationTags(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void fetchAnnotationEntry(final FetchAnnotationEntryRequest request,
                                     final Consumer<AnnotationEntry> consumer,
                                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.fetchAnnotationEntry(request))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void changeAnnotationEntry(final ChangeAnnotationEntryRequest request,
                                      final Consumer<Boolean> consumer,
                                      final RestErrorHandler errorHandler,
                                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.changeAnnotationEntry(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void deleteAnnotationEntry(final DeleteAnnotationEntryRequest request,
                                      final Consumer<Boolean> consumer,
                                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.deleteAnnotationEntry(request))
                .onSuccess(consumer)
                .onFailure(new DefaultErrorHandler(this, () -> consumer.accept(false)))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
