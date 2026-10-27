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

import stroom.annotation.client.AnnotationChangeEvent;
import stroom.annotation.client.AnnotationResourceClient;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.ChangeDescription;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.component.TabbedController;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.svg.shared.SvgImage;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// An annotation's tabs: Annotation, Events, Link To, Link From, Documentation and Permissions. An
/// annotation isn't a stored document: it has no Save As, and each tab saves its own changes (the
/// description with the Documentation tab's own Save button), so nothing here is dirty. Whether it
/// may be changed comes from whatever shows it.
public class AnnotationTabs extends TabbedController<Annotation> {

    private static final TabData ANNOTATION = new TabDataImpl("Annotation");
    private static final TabData EVENTS = new TabDataImpl("Events");
    private static final TabData LINK_TO = new TabDataImpl("Link To");
    private static final TabData LINK_FROM = new TabDataImpl("Link From");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final AnnotationResourceClient annotationResourceClient;
    private final AnnotationEditController annotationEdit;
    private MarkdownEditController description;
    // Made in the Documentation tab's toolbar when it is first shown
    private ToolbarButton saveButton;
    private boolean descriptionDirty;
    private Runnable onTitleChange = () -> {
    };
    private Runnable onDelete = () -> {
    };

    /// Makes the tabs.
    @Inject
    public AnnotationTabs(final EventBus eventBus,
                          final LinkTabPanelView view,
                          final AnnotationResourceClient annotationResourceClient,
                          final AnnotationEditController annotationEdit,
                          final Provider<AnnotationLinksController> annotationLinksProvider,
                          final LinkedEventsController linkedEvents,
                          final Provider<MarkdownEditController> markdownEditProvider,
                          final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        this.annotationResourceClient = annotationResourceClient;
        this.annotationEdit = own(annotationEdit);
        own(linkedEvents);
        getToolbar().setName("Annotation actions");
        showReadOnlyStatus();
        annotationEdit.setOnTitleChange(() -> onTitleChange.run());
        annotationEdit.setOnDelete(() -> onDelete.run());
        linkedEvents.setOnChange(this::updateHistory);

        addOwnedTab(ANNOTATION, annotationEdit, AnnotationEditController::read);
        addOwnedTab(EVENTS, linkedEvents, LinkedEventsController::read);
        addTab(LINK_TO,
                () -> {
                    final AnnotationLinksController linkTo = annotationLinksProvider.get();
                    linkTo.setOnChange(this::updateHistory);
                    return linkTo;
                },
                AnnotationLinksController::read);
        addTab(LINK_FROM,
                () -> {
                    final AnnotationLinksController linkFrom = annotationLinksProvider.get();
                    linkFrom.setFrom(true);
                    return linkFrom;
                },
                AnnotationLinksController::read);
        addTab(DOCUMENTATION,
                () -> {
                    description = markdownEditProvider.get();
                    description.followReadOnly();
                    description.setAddButtonsFirst(toolbar -> {
                        saveButton = toolbar.button(SvgImage.SAVE, "Save Annotation Description",
                                this::saveDescription);
                        saveButton.setEnabled(descriptionDirty);
                    });
                    own(description.addDirtyListener(this::setDescriptionDirty));
                    return description;
                },
                (documentation, docRef, annotation) -> documentation.setText(annotation.getDescription()));
        addTab(PERMISSIONS,
                permissionsProvider::get,
                (permissions, docRef, annotation) -> permissions.setDocRef(docRef));
        setCommonTab(CommonDocLinkTab.DOCUMENTATION, DOCUMENTATION);
        setCommonTab(CommonDocLinkTab.PERMISSIONS, PERMISSIONS);
        selectTab(ANNOTATION);
    }

    /// Shows an annotation.
    ///
    /// @param annotation The annotation.
    public void read(final Annotation annotation) {
        read(annotation.asDocRef(), annotation);
    }

    /// @param initialComment The comment the annotation was made with, to show until it is saved.
    public void setInitialComment(final String initialComment) {
        annotationEdit.setInitialComment(initialComment);
    }

    /// @param onTitleChange What to run when the annotation's title changes, e.g. to label its tab
    ///                      again.
    public void setOnTitleChange(final Runnable onTitleChange) {
        this.onTitleChange = Objects.requireNonNull(onTitleChange, "onTitleChange");
    }

    /// @param onDelete What to run once the annotation is deleted, e.g. to close its tab.
    public void setOnDelete(final Runnable onDelete) {
        this.onDelete = Objects.requireNonNull(onDelete, "onDelete");
    }

    @Override
    public String getTabLabel() {
        final Annotation annotation = getDocument();
        return annotation == null
                ? null
                : "#" + annotation.getId() + " " + annotation.getName();
    }

    private void updateHistory() {
        annotationEdit.updateHistory();
    }

    private void saveDescription() {
        annotationResourceClient.change(
                new SingleAnnotationChangeRequest(getDocRef(), new ChangeDescription(description.getText())),
                guard((Boolean success) -> {
                    if (Boolean.TRUE.equals(success)) {
                        AnnotationChangeEvent.fire(this, getDocRef());
                    }
                }), taskMonitor());
        setDescriptionDirty(false);
    }

    // The task monitor of whatever shows the tabs, or one of their own, looked up when a task starts
    private TaskMonitorFactory taskMonitor() {
        return () -> {
            final TaskMonitorFactory provided = getProvidedTaskMonitor();
            return provided != null
                    ? provided.createTaskMonitor()
                    : new DefaultTaskMonitorFactory(this).createTaskMonitor();
        };
    }

    private void setDescriptionDirty(final boolean dirty) {
        this.descriptionDirty = dirty;
        if (saveButton != null) {
            saveButton.setEnabled(dirty);
        }
    }
}
