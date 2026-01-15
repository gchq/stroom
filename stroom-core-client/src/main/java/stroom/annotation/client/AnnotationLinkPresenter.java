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

import stroom.alert.client.event.ConfirmEvent;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.LinkAnnotations;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.annotation.shared.UnlinkAnnotations;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.client.presenter.AbstractFindPresenter.FindView;
import stroom.explorer.client.presenter.FindDocResultListHandler;
import stroom.explorer.client.presenter.FindUiHandlers;
import stroom.security.shared.DocumentPermission;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;

public class AnnotationLinkPresenter
        extends DocumentEditPresenter<FindView, Annotation>
        implements FindUiHandlers, FindDocResultListHandler<Annotation> {

    private final AnnotationResourceClient annotationResourceClient;
    private final FindAnnotationListPresenter listPresenter;

    private DocRef annotationRef;
    private AnnotationPresenter parent;
    private boolean from;

    private final ButtonView add;
    private final ButtonView remove;

    @Inject
    public AnnotationLinkPresenter(final EventBus eventBus,
                                   final FindView view,
                                   final AnnotationResourceClient annotationResourceClient,
                                   final FindAnnotationListPresenter listPresenter) {
        super(eventBus, view);
        this.annotationResourceClient = annotationResourceClient;
        this.listPresenter = listPresenter;

        // To browse, users only need view permission.
        listPresenter.setPermission(DocumentPermission.VIEW);
        view.setDialogMode(false);
        view.setResultView(listPresenter.getView());
        view.setUiHandlers(this);
        listPresenter.setFindResultListHandler(this);

        add = listPresenter.getView().addButton(SvgPresets.ADD);
        add.setTitle("Add Annotation Link");
        add.setEnabled(false);
        add.setVisible(false);
        remove = listPresenter.getView().addButton(SvgPresets.DELETE);
        remove.setTitle("Remove Annotation Link");
        remove.setEnabled(false);
        remove.setVisible(false);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(add.addClickHandler(e -> ShowFindAnnotationEvent.fire(this, this::link)));
        registerHandler(remove.addClickHandler(e -> {
            final Annotation annotation = listPresenter.getSelected();
            if (annotation != null) {
                ConfirmEvent.fire(this, "Are you sure you want to remove this reference?", ok -> {
                    if (ok) {
                        unlink(annotation);
                    }
                });
            }
        }));
        registerHandler(listPresenter.addSelectionHandler(e -> enableButtons()));
    }

    private void link(final Annotation annotation) {
        final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(annotationRef,
                new LinkAnnotations(Collections.singletonList(annotation.getId())));
        annotationResourceClient.change(request, this::onChange, this);
    }

    private void unlink(final Annotation annotation) {
        final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(annotationRef,
                new UnlinkAnnotations(Collections.singletonList(annotation.getId())));
        annotationResourceClient.change(request, this::onChange, this);
    }

    private void onChange(final Boolean success) {
        if (success != null && success) {
            parent.updateHistory();
            listPresenter.refresh();
        }
    }

    @Override
    public void focus() {
        getView().focus();
    }

    public void refresh() {
        listPresenter.refresh();
    }

    @Override
    public void changeQuickFilter(final String name) {
        listPresenter.setFilter(name);
        listPresenter.refresh();
    }

    @Override
    public void onFilterKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openDocument(listPresenter.getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            listPresenter.setKeyboardSelectedRow(0, true);
        }
    }

    @Override
    public void openDocument(final Annotation match) {
        if (match != null) {
            EditAnnotationEvent.fire(this, match.getId());
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final Annotation document, final boolean readOnly) {
        annotationRef = docRef;
        if (from) {
            listPresenter.setDestinationId(document.getId());
        } else {
            listPresenter.setSourceId(document.getId());
        }
        listPresenter.refresh();
        enableButtons();
    }

    private void enableButtons() {
        add.setEnabled(!isReadOnly());
        remove.setEnabled(!isReadOnly() && listPresenter.getSelected() != null);
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
