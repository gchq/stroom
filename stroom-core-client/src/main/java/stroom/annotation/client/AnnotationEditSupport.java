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
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.CreateAnnotationRequest;
import stroom.annotation.shared.EventId;
import stroom.core.client.ContentManager;
import stroom.task.client.DefaultTaskMonitorFactory;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import javax.inject.Provider;

public class AnnotationEditSupport implements HasHandlers {

    private final EventBus eventBus;
    private final ContentManager contentManager;

    @Inject
    public AnnotationEditSupport(final EventBus eventBus,
                                 final Provider<AnnotationPresenter> presenterProvider,
                                 final ContentManager contentManager,
                                 final AnnotationResourceClient annotationResourceClient) {
        this.eventBus = eventBus;
        this.contentManager = contentManager;

        eventBus.addHandler(ShowAnnotationEvent.getType(), e -> {
            final Annotation annotation = e.getAnnotation();
            final List<EventId> linkedEvents = e.getLinkedEvents();

            boolean ok = true;
            if (annotation == null) {
                ok = false;
                AlertEvent.fireError(
                        this,
                        "No sample annotation has been provided to open the editor",
                        null);
            }

            if (ok) {
                final AnnotationPresenter presenter = presenterProvider.get();
                if (annotation.getId() == null) {
                    presenter.setInitialComment(annotation.getComment());
                    final CreateAnnotationRequest request = new CreateAnnotationRequest(annotation, linkedEvents);
                    annotationResourceClient.createAnnotation(request, annotationDetail ->
                            show(presenter, annotationDetail), new DefaultTaskMonitorFactory(this));
                } else {
                    annotationResourceClient.getById(annotation.getId(), annotationDetail ->
                            show(presenter, annotationDetail), new DefaultTaskMonitorFactory(this));
                }
            }
        });
    }

    private void show(final AnnotationPresenter presenter,
                      final AnnotationDetail annotationDetail) {
        presenter.read(annotationDetail);
        contentManager.open(e2 -> e2.getCallback().closeTab(true), presenter, presenter);
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
