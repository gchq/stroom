package stroom.annotation.client;

import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.CreateAnnotationRequest;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.EventLink;
import stroom.annotation.shared.SetAssignedToRequest;
import stroom.annotation.shared.SetDescriptionRequest;
import stroom.annotation.shared.SetStatusRequest;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.presenter.AbstractRestClient;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.task.client.TaskMonitorFactory;

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

    public void getById(final Long annotationId,
                        final Consumer<AnnotationDetail> consumer,
                        final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.get(annotationId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void getStatus(final String filter,
                          final Consumer<List<String>> consumer,
                          final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.getStatus(filter))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void setStatus(final SetStatusRequest request,
                          final Consumer<Integer> consumer,
                          RestErrorHandler errorHandler,
                          final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.setStatus(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void setAssignedTo(final SetAssignedToRequest request,
                              final Consumer<Integer> consumer,
                              RestErrorHandler errorHandler,
                              final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.setAssignedTo(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void setDescription(final SetDescriptionRequest request,
                               final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.setDescription(request))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void getComment(final String filter,
                           final Consumer<List<String>> consumer,
                           final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.getComment(filter))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

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

    public void addEntry(final CreateEntryRequest request,
                         final Consumer<AnnotationDetail> consumer,
                         final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.createEntry(request))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void getLinkedEvents(final Annotation annotation,
                                final Consumer<List<EventId>> consumer,
                                final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.getLinkedEvents(annotation.getId()))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void linkEvent(final EventLink eventLink,
                          final Consumer<List<EventId>> consumer,
                          final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.link(eventLink))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void unlinkEvent(final EventLink eventLink,
                            final Consumer<List<EventId>> consumer,
                            final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANNOTATION_RESOURCE)
                .method(res -> res.unlink(eventLink))
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
}
