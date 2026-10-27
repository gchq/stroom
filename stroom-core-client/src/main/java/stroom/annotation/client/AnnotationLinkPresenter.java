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

package stroom.annotation.client;

import stroom.alert.client.event.ConfirmEvent;
import stroom.annotation.client.component.FindAnnotationListController;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.LinkAnnotations;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.annotation.shared.UnlinkAnnotations;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.explorer.client.presenter.AbstractFindPresenter.FindView;
import stroom.explorer.client.presenter.FindUiHandlers;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;

public class AnnotationLinkPresenter
        extends DocPresenter<FindView, Annotation>
        implements FindUiHandlers {

    private final AnnotationResourceClient annotationResourceClient;
    private final FindAnnotationListController findAnnotationList;

    private DocRef annotationRef;
    private AnnotationPresenter parent;
    private boolean from;

    private final ButtonView add;
    private final ButtonView remove;

    @Inject
    public AnnotationLinkPresenter(final EventBus eventBus,
                                   final FindView view,
                                   final AnnotationResourceClient annotationResourceClient,
                                   final FindAnnotationListController findAnnotationList) {
        super(eventBus, view);
        own(findAnnotationList);
        this.annotationResourceClient = annotationResourceClient;
        this.findAnnotationList = findAnnotationList;

        // To browse, users only need view permission.
        findAnnotationList.setPermission(DocumentPermission.VIEW);
        view.setDialogMode(false);
        view.setResultView(findAnnotationList);
        view.setUiHandlers(this);
        findAnnotationList.setOnOpen(this::openDocument);
        findAnnotationList.setOnFocusFilter(this::focus);

        findAnnotationList.getToolbar().setName("Link actions");
        add = findAnnotationList.getToolbar().button(SvgImage.ADD, "Add Annotation Link",
                () -> ShowFindAnnotationEvent.fire(this, this::link));
        add.setEnabled(false);
        add.setVisible(false);
        remove = findAnnotationList.getToolbar().button(SvgImage.DELETE, "Remove Annotation Link", this::onRemove);
        remove.setEnabled(false);
        remove.setVisible(false);

        own(findAnnotationList.addSelectionHandler(e -> enableButtons()));
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
        annotationResourceClient.change(request, this::onChangeComplete, this);
    }

    private void unlink(final Annotation annotation) {
        final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(annotationRef,
                new UnlinkAnnotations(Collections.singletonList(annotation.getId())));
        annotationResourceClient.change(request, this::onChangeComplete, this);
    }

    private void onChangeComplete(final Boolean success) {
        if (success != null && success) {
            parent.updateHistory();
            findAnnotationList.refresh();
        }
    }

    /// Puts the focus on the filter.
    public void focus() {
        getView().focus();
    }

    public void refresh() {
        findAnnotationList.refresh();
    }

    @Override
    public void changeQuickFilter(final String name) {
        findAnnotationList.setFilter(name);
        findAnnotationList.refresh();
    }

    @Override
    public void onFilterKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openDocument(findAnnotationList.getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            findAnnotationList.setKeyboardSelectedRow(0, true);
        }
    }

    /// Opens an annotation found.
    ///
    /// @param match The annotation, or `null` for none.
    public void openDocument(final Annotation match) {
        if (match != null) {
            EditAnnotationEvent.fire(this, match.getId());
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final Annotation document) {
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
        add.setEnabled(!isReadOnly());
        remove.setEnabled(!isReadOnly() && findAnnotationList.getSelected() != null);
        add.setVisible(!from);
        remove.setVisible(!from);
    }

    @Override
    protected Annotation onWrite(final Annotation document) {
        return document;
    }

    public void setParent(final AnnotationPresenter parent) {
        this.parent = parent;
    }

    public void setFrom(final boolean from) {
        this.from = from;
    }
}
