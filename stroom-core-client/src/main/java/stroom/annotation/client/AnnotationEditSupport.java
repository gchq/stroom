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

package stroom.annotation.client;

import stroom.alert.client.event.AlertEvent;
import stroom.annotation.client.component.FindAnnotationController;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.CreateAnnotationRequest;
import stroom.core.client.ContentManager;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.widget.component.client.AppRoot;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

public class AnnotationEditSupport implements HasHandlers {

    private final EventBus eventBus;
    private final ContentManager contentManager;
    private final ClientSecurityContext securityContext;
    private FindAnnotationController findAnnotation;

    @Inject
    public AnnotationEditSupport(final EventBus eventBus,
                                 final Provider<AnnotationPresenter> presenterProvider,
                                 final ContentManager contentManager,
                                 final AnnotationResourceClient annotationResourceClient,
                                 final ClientSecurityContext securityContext,
                                 final Provider<FindAnnotationController> findAnnotationProvider,
                                 final AppRoot appRoot) {
        this.eventBus = eventBus;
        this.contentManager = contentManager;
        this.securityContext = securityContext;

        eventBus.addHandler(CreateAnnotationEvent.getType(), e -> {
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
                // Made once there is something to show, and released, with all its presenters, when
                // its tab is closed
                final AnnotationPresenter presenter = presenterProvider.get();
                presenter.setInitialComment(e.getComment());
                show(presenter, annotation);
                AnnotationChangeEvent.fireDeferred(this, annotation.asDocRef());
            }, new DefaultTaskMonitorFactory(this));
        });

        eventBus.addHandler(EditAnnotationEvent.getType(), e -> {
            annotationResourceClient.getAnnotationById(e.getAnnotationId(), annotation ->
                    // Made once there is something to show, and released, with all its presenters,
                    // when its tab is closed
                    show(presenterProvider.get(), annotation), new DefaultTaskMonitorFactory(this));
        });

        // The screens it opens, made when first asked for and kept by the app, so they keep
        // their filters
        eventBus.addHandler(ShowFindAnnotationEvent.getType(), event -> {
            if (findAnnotation == null) {
                findAnnotation = appRoot.make(findAnnotationProvider);
            }
            findAnnotation.show(event.getAnnotationConsumer());
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
                        presenter.setReadOnly(!allowUpdate);
                        presenter.read(annotation);
                        // Closing the tab releases the annotation's presenters
                        contentManager.open(e2 -> e2.getCallback().closeTab(true), presenter, presenter);
                    },
                    throwable -> {
                        // Never shown, so released now
                        presenter.dispose();
                        AlertEvent.fireErrorFromException(this, throwable, null);
                    },
                    new DefaultTaskMonitorFactory(this));
        } else {
            // Never shown, so released now
            presenter.dispose();
        }
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
