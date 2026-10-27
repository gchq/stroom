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

import stroom.annotation.client.component.AnnotationEditController;
import stroom.annotation.client.component.AnnotationLinksController;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.ChangeDescription;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.content.client.event.CloseContentTabEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.docref.DocRef;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.ControllerTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.DocTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

public class AnnotationPresenter
        extends DocTabPresenter<LinkTabPanelView, Annotation> {

    private static final TabData ANNOTATION = new TabDataImpl("Annotation");
    private static final TabData EVENTS = new TabDataImpl("Events");
    private static final TabData LINK_TO = new TabDataImpl("Link To");
    private static final TabData LINK_FROM = new TabDataImpl("Link From");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    // Made in the documentation tab's toolbar when it is first shown
    private ToolbarButton saveButton;
    private boolean descriptionDirty;

    private final AnnotationResourceClient annotationResourceClient;
    private final AnnotationEditController annotationEdit;
    private MarkdownEditController markdownEdit;

    @SuppressWarnings("checkstyle:linelength")
    @Inject
    public AnnotationPresenter(final EventBus eventBus,
                               final LinkTabPanelView view,
                               final AnnotationResourceClient annotationResourceClient,
                               final AnnotationEditController annotationEdit,
                               final Provider<AnnotationLinksController> annotationLinksProvider,
                               final LinkedEventPresenter linkedEventPresenter,
                               final Provider<MarkdownEditController> markdownEditProvider,
                               final DocumentUserPermissionsTabProvider<Annotation> documentUserPermissionsTabProvider) {
        super(eventBus, view);
        own(annotationEdit);
        own(linkedEventPresenter);
        this.annotationResourceClient = annotationResourceClient;
        this.annotationEdit = annotationEdit;
        annotationEdit.setOnTitleChange(() -> RefreshContentTabEvent.fire(this, this));
        annotationEdit.setOnDelete(() -> CloseContentTabEvent.fire(this, this));
        linkedEventPresenter.setParent(this);

        addTab(ANNOTATION, ControllerTabProvider.ofOwned(eventBus, annotationEdit, AnnotationEditController::read));
        addTab(EVENTS, new DocTabProvider<>(() -> linkedEventPresenter));
        addTab(LINK_TO, new ControllerTabProvider<>(eventBus, () -> {
            final AnnotationLinksController linkTo = annotationLinksProvider.get();
            linkTo.setOnChange(this::updateHistory);
            return linkTo;
        }, AnnotationLinksController::read));
        addTab(LINK_FROM, new ControllerTabProvider<>(eventBus, () -> {
            final AnnotationLinksController linkFrom = annotationLinksProvider.get();
            linkFrom.setFrom(true);
            return linkFrom;
        }, AnnotationLinksController::read));
        addTab(DOCUMENTATION, new MarkdownTabProvider<Annotation>(getEventBus(), () -> {
            if (markdownEdit == null) {
                // Owned by the tab that shows it
                markdownEdit = markdownEditProvider.get();
                markdownEdit.setAddButtonsFirst(markdownToolbar -> {
                    saveButton = markdownToolbar.button(SvgImage.SAVE, "Save Annotation Description",
                            this::saveDescription);
                    saveButton.setEnabled(descriptionDirty);
                });
                markdownEdit.addDirtyListener(this::setDescriptionDirty);
            }
            return markdownEdit;
        }) {
            @Override
            public void onRead(final MarkdownEditController documentation,
                               final DocRef docRef,
                               final Annotation document) {
                documentation.setText(document.getDescription());
                documentation.setReadOnly(isReadOnly());
            }

            @Override
            public Annotation onWrite(final MarkdownEditController documentation,
                                      final Annotation document) {
//                document.setDescription(presenter.getText());
                return document;
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);

        selectTab(ANNOTATION);
    }

    private void saveDescription() {
        annotationResourceClient.change(
                new SingleAnnotationChangeRequest(getDocRef(),
                        new ChangeDescription(markdownEdit.getText())),
                success -> {
                    if (success) {
                        AnnotationChangeEvent.fire(this, getDocRef());
                    }
                }, this);
        setDescriptionDirty(false);
    }

    private void setDescriptionDirty(final boolean dirty) {
        this.descriptionDirty = dirty;
        if (saveButton != null) {
            saveButton.setEnabled(descriptionDirty);
        }
    }

    // An annotation has no Save As, and its Save is in its editor's buttons
    @Override
    protected Toolbar createToolbar() {
        return new Toolbar("Annotation actions");
    }

    @Override
    public String getType() {
        return Annotation.TYPE;
    }

    @Override
    public String getLabel() {
        final Annotation annotation = getEntity();
        return "#" + annotation.getId() + " " + annotation.getName();
    }

    @Override
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }

    @Override
    protected TabData getDocumentationTab() {
        return DOCUMENTATION;
    }

    /// Shows an annotation, read only or not as [#setReadOnly(boolean)] said.
    ///
    /// @param annotation The annotation.
    public void read(final Annotation annotation) {
        read(annotation.asDocRef(), annotation);
    }

    public void updateHistory() {
        annotationEdit.updateHistory();
    }

    public void setInitialComment(final String initialComment) {
        annotationEdit.setInitialComment(initialComment);
    }
}
