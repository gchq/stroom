package stroom.annotation.client;

import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.AnnotationGroup;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.CreateAnnotationRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.MultiAnnotationChangeRequest;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.security.client.presenter.AbstractRestClient;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
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

    public void getById(final long annotationId,
                        final Consumer<AnnotationDetail> consumer,
                        final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.getById(annotationId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void getStatusValues(final String filter,
                                final Consumer<List<String>> consumer,
                                final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.getStatusValues(filter))
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

//    public void getDefaultRetentionPeriod(final Consumer<SimpleDuration> consumer,
//                                          final TaskMonitorFactory taskMonitorFactory) {
//        restFactory
//                .create(ANNOTATION_RESOURCE)
//                .method(AnnotationResource::getDefaultRetentionPeriod)
//                .onSuccess(consumer)
//                .taskMonitorFactory(taskMonitorFactory)
//                .exec();
//    }

    public void createAnnotation(final CreateAnnotationRequest request,
                                 final Consumer<AnnotationDetail> consumer,
                                 final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.createAnnotation(request))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void change(final SingleAnnotationChangeRequest request,
                       final Consumer<AnnotationDetail> consumer,
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

    public void getLinkedEvents(DocRef annotationRef,
                                final Consumer<List<EventId>> consumer,
                                final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.getLinkedEvents(annotationRef))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void changeDocumentPermissions(final SingleDocumentPermissionChangeRequest request,
                                          final Consumer<Boolean> consumer,
                                          final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.changeDocumentPermissions(request))
                .onSuccess(consumer)
                .onFailure(new DefaultErrorHandler(this, () -> consumer.accept(false)))
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

    public void fetchAnnotationGroupByName(final String name,
                                           final Consumer<AnnotationGroup> consumer,
                                           final RestErrorHandler errorHandler,
                                           final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.fetchAnnotationGroupByName(name))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void createAnnotationGroup(final String name,
                                      final Consumer<AnnotationGroup> consumer,
                                      final RestErrorHandler errorHandler,
                                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.createAnnotationGroup(name))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void updateAnnotationGroup(final AnnotationGroup annotationGroup,
                                      final Consumer<AnnotationGroup> consumer,
                                      final RestErrorHandler errorHandler,
                                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.updateAnnotationGroup(annotationGroup))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void deleteAnnotationGroup(final AnnotationGroup annotationGroup,
                                      final Consumer<Boolean> consumer,
                                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.deleteAnnotationGroup(annotationGroup))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void findAnnotationGroups(final ExpressionCriteria request,
                                     final Consumer<ResultPage<AnnotationGroup>> consumer,
                                     final RestErrorHandler errorHandler,
                                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.findAnnotationGroups(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void getAnnotationGroups(final String filter,
                                final Consumer<List<AnnotationGroup>> consumer,
                                final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.getAnnotationGroups(filter))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
