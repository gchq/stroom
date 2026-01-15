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

import stroom.alert.client.event.AlertEvent;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.CreateAnnotationRequest;
import stroom.core.client.ContentManager;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.DefaultTaskMonitorFactory;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class AnnotationEditSupport implements HasHandlers {

    private final EventBus eventBus;
    private final ContentManager contentManager;
    private final ClientSecurityContext securityContext;

    @Inject
    public AnnotationEditSupport(final EventBus eventBus,
                                 final Provider<AnnotationPresenter> presenterProvider,
                                 final ContentManager contentManager,
                                 final AnnotationResourceClient annotationResourceClient,
                                 final ClientSecurityContext securityContext) {
        this.eventBus = eventBus;
        this.contentManager = contentManager;
        this.securityContext = securityContext;

        eventBus.addHandler(CreateAnnotationEvent.getType(), e -> {
            final AnnotationPresenter presenter = presenterProvider.get();
            presenter.setInitialComment(e.getComment());
            final CreateAnnotationRequest request = new CreateAnnotationRequest(
                    e.getTitle(),
                    e.getSubject(),
                    e.getStatus(),
                    e.getAssignTo(),
                    e.getComment(),
                    e.getTable(),
                    e.getLinkedEvents(),
                    e.getLinkedAnnotations());
            annotationResourceClient.createAnnotation(request, annotation -> {
                show(presenter, annotation);
                AnnotationChangeEvent.fireDeferred(this, annotation.asDocRef());
            }, new DefaultTaskMonitorFactory(this));
        });

        eventBus.addHandler(EditAnnotationEvent.getType(), e -> {
            final AnnotationPresenter presenter = presenterProvider.get();
            annotationResourceClient.getAnnotationById(e.getAnnotationId(), annotation ->
                    show(presenter, annotation), new DefaultTaskMonitorFactory(this));
        });
    }

    private void show(final AnnotationPresenter presenter,
                      final Annotation annotation) {
        if (securityContext.hasAppPermission(AppPermission.ANNOTATIONS)) {
            // Check document permissions and read.
            securityContext.hasDocumentPermission(
                    annotation.asDocRef(),
                    DocumentPermission.EDIT,
                    allowUpdate -> {
                        presenter.read(annotation, !allowUpdate);
                        contentManager.open(e2 -> e2.getCallback().closeTab(true), presenter, presenter);
                    },
                    throwable -> AlertEvent.fireErrorFromException(this, throwable, null),
                    new DefaultTaskMonitorFactory(this));
        }
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
