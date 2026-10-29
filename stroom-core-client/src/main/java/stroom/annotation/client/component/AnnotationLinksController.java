/*
 * Copyright 2025 Crown Copyright
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

package stroom.annotation.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.annotation.client.AnnotationResourceClient;
import stroom.annotation.client.EditAnnotationEvent;
import stroom.annotation.client.ShowFindAnnotationEvent;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.LinkAnnotations;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.annotation.shared.UnlinkAnnotations;
import stroom.docref.DocRef;
import stroom.explorer.client.component.FindPanel;
import stroom.security.shared.DocumentPermission;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;

/// An annotation's Link To or Link From tab ([FindAnnotations]): the annotations it links to, which can
/// be added and removed unless the annotation is read only, or those that link to it
/// ([#setFrom(boolean)]), filtered by a quick filter. Choosing one opens it.
public class AnnotationLinksController extends Controller {

    private final AnnotationResourceClient annotationResourceClient;
    private final FindAnnotationListController findAnnotationList;

    private DocRef annotationRef;
    private boolean from;
    private boolean readOnly = true;
    private Runnable onChange = () -> {
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                  The application's event bus.
    /// @param content                   The content.
    /// @param annotationResourceClient  Links and unlinks annotations.
    /// @param findAnnotationList        Finds the linked annotations, and lists them.
    @Inject
    public AnnotationLinksController(final EventBus eventBus,
                                     final FindAnnotations content,
                                     final AnnotationResourceClient annotationResourceClient,
                                     final FindAnnotationListController findAnnotationList) {
        super(eventBus);
        this.annotationResourceClient = annotationResourceClient;
        initWidget(content);
        final FindPanel view = content.getFindPanel();
        this.findAnnotationList = own(findAnnotationList).attach(content.getResultList());

        // To browse, users only need view permission.
        findAnnotationList.setPermission(DocumentPermission.VIEW);
        view.setDialogMode(false);
        view.setOnFilterChange(this::changeQuickFilter);
        view.setOnFilterKeyDown(this::onFilterKeyDown);
        findAnnotationList.setOnOpen(this::openDocument);
        findAnnotationList.setOnFocusFilter(view::focus);

        findAnnotationList.showLinkActions();
        findAnnotationList.setOnAction(action -> {
            if (action == FindAnnotationList.LinkAction.ADD) {
                ShowFindAnnotationEvent.fire(this, this::link);
            } else {
                onRemove();
            }
        });

        own(findAnnotationList.addSelectionHandler(e -> enableButtons()));
        watch(Contexts.READ_ONLY, readOnly -> {
            this.readOnly = readOnly;
            enableButtons();
        });
    }

    private void onRemove() {
        final Annotation annotation = findAnnotationList.getSelected();
        if (annotation != null) {
            ConfirmEvent.fire(this, "Are you sure you want to remove this reference?", ok -> {
                if (ok) {
                    unlink(annotation);
                }
            });
        }
    }

    private void link(final Annotation annotation) {
        final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(annotationRef,
                new LinkAnnotations(Collections.singletonList(annotation.getId())));
        annotationResourceClient.change(request, guard(this::onChangeComplete), taskMonitor());
    }

    private void unlink(final Annotation annotation) {
        final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(annotationRef,
                new UnlinkAnnotations(Collections.singletonList(annotation.getId())));
        annotationResourceClient.change(request, guard(this::onChangeComplete), taskMonitor());
    }

    private void onChangeComplete(final Boolean success) {
        if (success != null && success) {
            onChange.run();
            findAnnotationList.refresh();
        }
    }

    private void changeQuickFilter(final String name) {
        findAnnotationList.setFilter(name);
        findAnnotationList.refresh();
    }

    private void onFilterKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openDocument(findAnnotationList.getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            findAnnotationList.setKeyboardSelectedRow(0, true);
        }
    }

    // Opens an annotation found, if there is one
    private void openDocument(final Annotation match) {
        if (match != null) {
            EditAnnotationEvent.fire(this, match.getId());
        }
    }

    /// Lists the annotations linked to or from an annotation.
    ///
    /// @param docRef   The annotation.
    /// @param document The annotation.
    public void read(final DocRef docRef, final Annotation document) {
        annotationRef = docRef;
        if (from) {
            findAnnotationList.setDestinationId(document.getId());
        } else {
            findAnnotationList.setSourceId(document.getId());
        }
        findAnnotationList.refresh();
        enableButtons();
    }

    private void enableButtons() {
        findAnnotationList.updateLinkActions(
                !from,
                !readOnly,
                !readOnly && findAnnotationList.getSelected() != null);
    }

    /// @param from Whether it lists the annotations that link to the annotation, which can't be
    ///             changed from here, rather than those it links to.
    public void setFrom(final boolean from) {
        this.from = from;
    }

    /// @param onChange Run when a link is added or removed.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

}
