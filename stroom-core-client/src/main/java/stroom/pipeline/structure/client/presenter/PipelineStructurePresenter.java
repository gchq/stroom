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

package stroom.pipeline.structure.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.RefreshDocumentEvent;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.DocPresenter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.PipelineResource;
import stroom.pipeline.shared.SavePipelineJsonRequest;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.structure.client.component.NewElementController;
import stroom.pipeline.structure.client.component.PipelineReferenceListController;
import stroom.pipeline.structure.client.component.PipelineTree;
import stroom.pipeline.structure.client.component.PropertyListController;
import stroom.pipeline.structure.client.presenter.PipelineStructurePresenter.PipelineStructureView;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.IconParentMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItems;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.View;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class PipelineStructurePresenter
        extends DocPresenter<PipelineStructureView, PipelineDoc>
        implements PipelineStructureUiHandlers {

    private static final PipelineResource PIPELINE_RESOURCE = GWT.create(PipelineResource.class);
    private static final DocRef NULL_SELECTION = DocRef.builder()
            .uuid("")
            .name("None")
            .type("")
            .build();

    private final DocSelectionBoxPresenter pipelinePresenter;
    private final RestFactory restFactory;
    private final Provider<NewElementController> newElementProvider;
    private final PropertyListController propertyListPresenter;
    private final PipelineReferenceListController pipelineReferenceListPresenter;
    private final Provider<EditorPresenter> jsonEditorProvider;
    private final PipelineTree pipelineTree;
    private PipelineElement selectedElement;
    private PipelineModel pipelineModel;
    private DocRef docRef;
    private PipelineDoc pipelineDoc;
    private DocRef parentPipeline;
    private boolean advancedMode;

    private List<Item> addMenuItems;
    private List<Item> restoreMenuItems;

    @Inject
    public PipelineStructurePresenter(final EventBus eventBus,
                                      final PipelineStructureView view,
                                      final PipelineTree pipelineTree,
                                      final DocSelectionBoxPresenter pipelinePresenter,
                                      final RestFactory restFactory,
                                      final Provider<NewElementController> newElementProvider,
                                      final PropertyListController propertyListPresenter,
                                      final PipelineReferenceListController pipelineReferenceListPresenter,
                                      final Provider<EditorPresenter> jsonEditorProvider) {
        super(eventBus, view);
        own(pipelinePresenter);
        own(propertyListPresenter);
        own(pipelineReferenceListPresenter);
        this.pipelineTree = pipelineTree;
        this.pipelinePresenter = pipelinePresenter;
        this.restFactory = restFactory;
        this.newElementProvider = newElementProvider;
        this.propertyListPresenter = propertyListPresenter;
        this.pipelineReferenceListPresenter = pipelineReferenceListPresenter;
        this.jsonEditorProvider = jsonEditorProvider;

        getView().setUiHandlers(this);
        getView().setInheritanceTree(pipelinePresenter.getView());
        getView().setTreeView(pipelineTree);
        getView().setProperties(propertyListPresenter);
        getView().setPipelineReferences(pipelineReferenceListPresenter);

        pipelinePresenter.setIncludedTypes(PipelineDoc.TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermission.USE);

        // Get a map of all available elements and properties.
        setAdvancedMode(true);
        enableButtons();

        own(pipelinePresenter.addSelectionListener(selected -> {
            final DocRef selectedDocRef = selected;
            if (selectedDocRef != null && !Objects.equals(selectedDocRef, NULL_SELECTION)) {
                if (Objects.equals(selectedDocRef.getUuid(), pipelineDoc.getUuid())) {
                    AlertEvent.fireWarn(
                            PipelineStructurePresenter.this,
                            "A pipeline cannot inherit from itself",
                            () -> {
                                // Reset selection.
                                pipelinePresenter.setSelectedEntityReference(
                                        getParentPipeline(),
                                        true);
                            });
                } else {
                    changeParentPipeline(selectedDocRef);
                }
            } else {
                changeParentPipeline(null);
            }
        }));
        own(
                pipelineTree.getSelectionModel().addSelectionChangeHandler(event -> {
                    selectedElement = pipelineTree.getSelectionModel().getSelectedObject();

                    propertyListPresenter.setPipelineModel(pipelineModel);
                    propertyListPresenter.setCurrentElement(selectedElement);

                    pipelineReferenceListPresenter.setPipeline(pipelineDoc);
                    pipelineReferenceListPresenter.setPipelineModel(pipelineModel);
                    pipelineReferenceListPresenter.setCurrentElement(selectedElement);

                    enableButtons();
                }));
        // The Delete key removes the selected element (which has the focus), as Remove does
        pipelineTree.setOnRemove(element -> {
            if (isRemoveEnabled()) {
                onRemove(null);
            }
        });
        pipelineTree.setOnMove(this::moveElement);
        own(pipelineTree.addContextMenuHandler(event -> {
            // A right-click selects the element under the pointer just before the menu opens, and
            // the selection change event comes later, so take the element from the selection model
            selectedElement = pipelineTree.getSelectionModel().getSelectedObject();
            if (advancedMode && selectedElement != null) {
                final List<Item> menuItems = addPipelineActionsToMenu();
                if (NullSafe.hasItems(menuItems)) {
                    showMenu("Actions for element '" + selectedElement.getDisplayName() + "'",
                            menuItems,
                            event.getPopupPosition());
                }
            }
        }));
    }

    @Override
    protected void onRead(final DocRef docRef, final PipelineDoc document) {
        pipelinePresenter.setReadOnly(isReadOnly());
        // Dragging rearranges the pipeline, so it is only allowed in advanced mode on a document
        // that can be changed
        pipelineTree.setAllowDragging(advancedMode && !isReadOnly());
        propertyListPresenter.setTableName(
                "Pipeline '" + docRef.getName() + "' Properties");
        pipelineReferenceListPresenter.setTableName(
                "Pipeline '" + docRef.getName() + "' References");
        enableButtons();

        if (document != null) {
            final PipelineElement previousSelection = this.selectedElement;

            this.docRef = docRef;
            this.pipelineDoc = document;
            this.selectedElement = null;

            if (document.getParentPipeline() != null) {
                this.parentPipeline = document.getParentPipeline();
            }
            pipelinePresenter.setSelectedEntityReference(document.getParentPipeline(), true);

            pipelineTree.getSelectionModel().setSelected(previousSelection, true);
        }
    }

    public void setPipelineModel(final PipelineModel model) {
        if (pipelineModel != model) {
            pipelineModel = model;
            if (model != null) {
                // Every edit ends in a model change event, so this is the only place that needs to
                // react to one. Anything that edits the pipeline via the model therefore enables the
                // Save button without having to remember to say so.
                own(model.addChangeListener(changed -> onPipelineModelChanged()));
            }
        }
        refreshTree();
    }

    private void onPipelineModelChanged() {
        // Re-evaluate whether the document is dirty and redraw the tree. Deliberately does NOT
        // rebuild the model: doing so here would re-enter this handler, and rebuilding on every
        // change is what previously made the tree flash and stepping editors lag.
        onChange();
        refreshTree();
    }

    private void refreshTree() {
        try {
            final PipelineElement selectedElement = pipelineTree.getSelectionModel().getSelectedObject();
            pipelineTree.setModel(pipelineModel);
            // Keep the selection, unless the edit that triggered this removed the selected element.
            if (selectedElement == null || pipelineModel == null || pipelineModel.hasElement(selectedElement)) {
                pipelineTree.getSelectionModel().setSelected(selectedElement, true);
            }
        } catch (final PipelineModelException e) {
            AlertEvent.fireError(PipelineStructurePresenter.this, e.getMessage(), null);
        }
    }

    @Override
    public PipelineDoc onWrite(final PipelineDoc document) {
        final PipelineDoc.Builder builder = document.copy();

        // Only write if we have been revealed and therefore created a pipeline model.
        if (pipelineModel != null) {
            try {
                // Set the parent pipeline.
                builder.parentPipeline(getParentPipeline());

                // Diff base and combined to create fresh pipeline data.
                final PipelineData pipelineData = pipelineModel.diff();
                builder.pipelineData(pipelineData);
            } catch (final RuntimeException e) {
                AlertEvent.fireError(this, e.getMessage(), null);
            }
        }
        return builder.build();
    }

    @Override
    public void onAdd(final ClickEvent event) {
        if (addMenuItems != null && !addMenuItems.isEmpty()) {
            showMenu(event, "Add Pipeline Element", addMenuItems);
        }
    }

    @Override
    public void onRestore(final ClickEvent event) {
        if (restoreMenuItems != null && !restoreMenuItems.isEmpty()) {
            showMenu(event, "Restore Pipeline Element", restoreMenuItems);
        }
    }

    @Override
    public void onRemove(final ClickEvent event) {
        try {
            final PipelineElement selectedElement = pipelineTree.getSelectionModel().getSelectedObject();
            if (advancedMode && selectedElement != null && !PipelineModel.SOURCE_ELEMENT.equals(selectedElement)) {
                ConfirmEvent.fire(this, "Are you sure you want to remove this element?", ok -> {
                    if (ok) {
                        // Select the parent before removing, as removing fires the change event that
                        // redraws the tree.
                        final PipelineElement parentElement = pipelineModel.getParentMap().get(selectedElement);
                        if (parentElement != null) {
                            pipelineTree.getSelectionModel().setSelected(parentElement, true);
                        }
                        pipelineModel.removeElement(selectedElement);
                    }
                });
            }
        } catch (final PipelineModelException e) {
            AlertEvent.fireError(this, e.getMessage(), null);
        }
    }

    private boolean isRemoveEnabled() {
        return !isReadOnly()
               && advancedMode
               && selectedElement != null
               && !PipelineModel.SOURCE_ELEMENT.equals(selectedElement);
    }

    private boolean isEditEnabled() {
        return !isReadOnly()
               && advancedMode
               && selectedElement != null
               && !PipelineModel.SOURCE_ELEMENT.equals(selectedElement)
               && pipelineModel.getPipelineData().getAddedElements().contains(selectedElement);
    }

    @Override
    public void onEdit(final ClickEvent event) {
        final PipelineElement selected = current(pipelineTree.getSelectionModel().getSelectedObject());
        if (selected != null) {
            final String currentName = selected.getName();
            final String currentDescription = selected.getDescription();

            // The current description is shown, so OK without changing it keeps it
            newElementProvider.get().edit(this,
                    "Edit Element",
                    selected.getDisplayName(),
                    currentDescription,
                    (enteredName, enteredDescription) -> {
                        if (NullSafe.isNonBlankString(enteredName)) {
                            final String newName = enteredName.trim();
                            final String newDescription = NullSafe.isBlankString(enteredDescription)
                                    ? null
                                    : enteredDescription;
                            try {
                                PipelineElement renamedElement = selected;
                                if (!Objects.equals(currentName, newName)) {
                                    renamedElement = pipelineModel.renameElement(selected, newName);
                                }
                                if (!Objects.equals(currentDescription, newDescription)) {
                                    renamedElement = pipelineModel.changeElementDescription(renamedElement,
                                            newDescription);
                                }
                                pipelineTree.getSelectionModel().setSelected(renamedElement, true);
                            } catch (final RuntimeException ex) {
                                AlertEvent.fireError(this, ex.getMessage(), null);
                            }
                        }
                    });
        }
    }

    // The element as the pipeline now has it: the tree's selection keeps the element it was given,
    // which an element equal to it (the same id) with a new description doesn't replace
    private PipelineElement current(final PipelineElement element) {
        if (element == null) {
            return null;
        }
        return pipelineModel.getPipelineData()
                .getAddedElements()
                .stream()
                .filter(element::equals)
                .findFirst()
                .orElse(element);
    }

    private List<Item> addPipelineActionsToMenu() {
        final List<Item> menuItems = new ArrayList<>();
        menuItems.add(new IconParentMenuItem.Builder()
                .priority(0)
                .icon(SvgImage.ADD)
                .text("Add")
                .enabled(addMenuItems != null && !addMenuItems.isEmpty())
                .children(addMenuItems)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.REMOVE)
                .text("Remove")
                .enabled(isRemoveEnabled())
                .command(() -> onRemove(null))
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(2)
                .icon(SvgImage.EDIT)
                .text("Edit")
                .enabled(isEditEnabled())
                .command(() -> onEdit(null))
                .build());
        menuItems.add(new IconParentMenuItem.Builder()
                .priority(3)
                .icon(SvgImage.UNDO)
                .text("Restore")
                .enabled(restoreMenuItems != null && !restoreMenuItems.isEmpty())
                .children(restoreMenuItems)
                .build());
        // Moving an element (and those after it) to another parent, as dragging it does, and as
        // Ctrl+X and Ctrl+V do in the tree
        final PipelineElement detached = pipelineTree.getDetached();
        if (detached == null) {
            menuItems.add(new IconMenuItem.Builder()
                    .priority(4)
                    .icon(SvgImage.MOVE)
                    .text("Detach")
                    .enabled(isRemoveEnabled())
                    .command(() -> pipelineTree.detach(selectedElement))
                    .build());
        } else {
            menuItems.add(new IconMenuItem.Builder()
                    .priority(4)
                    .icon(SvgImage.MOVE)
                    .text("Attach '" + detached.getDisplayName() + "' here")
                    .enabled(pipelineTree.canAttach(selectedElement))
                    .command(() -> pipelineTree.attach(selectedElement))
                    .build());
            menuItems.add(new IconMenuItem.Builder()
                    .priority(5)
                    .icon(SvgImage.UNDO)
                    .text("Put '" + detached.getDisplayName() + "' back")
                    .command(pipelineTree::cancelDetach)
                    .build());
        }
        return menuItems;
    }

    private List<Item> getAddMenuItems() {
        final List<Item> menuItems = new ArrayList<>();

        final PipelineElement parent = pipelineTree.getSelectionModel().getSelectedObject();
        if (parent != null) {
            final PipelineElementType parentType = pipelineModel.getElementType(parent);
            int childCount = 0;
            final List<PipelineElement> currentChildren = pipelineModel.getChildMap().get(parent);
            if (currentChildren != null) {
                childCount = currentChildren.size();
            }

            for (final Entry<Category, List<PipelineElementType>> entry :
                    pipelineModel.getElementTypesByCategory().entrySet()) {
                final Category category = entry.getKey();
                if (category.getOrder() >= 0) {
                    final List<Item> children = new ArrayList<>();
                    int j = 0;
                    for (final PipelineElementType pipelineElementType : entry.getValue()) {
                        if (StructureValidationUtil.isValidChildType(parentType, pipelineElementType, childCount)) {
                            final SvgImage icon = pipelineElementType.getIcon();
                            final Item item = new IconMenuItem.Builder()
                                    .priority(j++)
                                    .icon(icon)
                                    .text(pipelineElementType.getDisplayValue())
                                    .command(new AddPipelineElementCommand(pipelineElementType))
                                    .build();
                            children.add(item);
                        }
                    }

                    if (!children.isEmpty()) {
                        children.sort(new MenuItems.ItemComparator());
                        final Item parentItem = new IconParentMenuItem.Builder()
                                .priority(category.getOrder())
                                .text(category.getDisplayValue())
                                .children(children)
                                .build();
                        menuItems.add(parentItem);
                    }
                }
            }
        }

        menuItems.sort(new MenuItems.ItemComparator());
        return menuItems;
    }

    private List<Item> getRestoreMenuItems() {
        final List<PipelineElement> existingElements = getExistingElements();

        if (existingElements.isEmpty()) {
            return null;
        }

        final List<Item> menuItems = new ArrayList<>();

        final PipelineElement parent = pipelineTree.getSelectionModel().getSelectedObject();
        if (parent != null) {
            final PipelineElementType parentType = pipelineModel.getElementType(parent);
            int childCount = 0;
            final List<PipelineElement> currentChildren = pipelineModel.getChildMap().get(parent);
            if (currentChildren != null) {
                childCount = currentChildren.size();
            }

            final Map<Category, List<Item>> categoryMenuItems = new HashMap<>();
            int pos = 0;
            for (final PipelineElement element : existingElements) {
                final PipelineElementType pipelineElementType = pipelineModel.getElementType(element);
                if (StructureValidationUtil.isValidChildType(parentType, pipelineElementType, childCount)) {
                    final Category category = pipelineElementType.getCategory();

                    final List<Item> items = categoryMenuItems.computeIfAbsent(category, k -> new ArrayList<>());
                    final SvgImage icon = pipelineElementType.getIcon();

                    final Item item = new IconMenuItem.Builder()
                            .priority(pos++)
                            .icon(icon)
                            .text(element.getDisplayName())
                            .command(new RestorePipelineElementCommand(element))
                            .build();
                    items.add(item);
                }
            }

            for (final Entry<Category, List<Item>> entry : categoryMenuItems.entrySet()) {
                final Category category = entry.getKey();
                final List<Item> children = entry.getValue();

                children.sort(new MenuItems.ItemComparator());
                final Item parentItem = new IconParentMenuItem.Builder()
                        .priority(category.getOrder())
                        .text(category.getDisplayValue())
                        .children(children)
                        .build();
                menuItems.add(parentItem);
            }
        }

        menuItems.sort(new MenuItems.ItemComparator());
        return menuItems;
    }

    private void showMenu(final ClickEvent event, final String name, final List<Item> menuItems) {
        final com.google.gwt.dom.client.Element target = event.getNativeEvent().getEventTarget().cast();
        Rect relativeRect = new Rect(target);
        relativeRect = relativeRect.grow(3);
        final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.BELOW);
        showMenu(name, menuItems, popupPosition);
    }

    private void showMenu(final String name,
                          final List<Item> menuItems,
                          final PopupPosition popupPosition) {
        ShowMenuEvent
                .builder()
                .name(name)
                .items(menuItems)
                .popupPosition(popupPosition)
                .fire(this);
    }

    private List<PipelineElement> getExistingElements() {
        final List<PipelineElement> existingElements = new ArrayList<>();

        if (selectedElement != null) {
            final List<PipelineElement> removedElements = pipelineModel.getRemovedElements();
            if (removedElements != null) {
                existingElements.addAll(removedElements);
            }
        }

        return existingElements;
    }

    @Override
    public void setAdvancedMode(final boolean advancedMode) {
        this.advancedMode = advancedMode;
        // Dragging rearranges the pipeline, so a read-only document can't be dragged
        pipelineTree.setAllowDragging(advancedMode && !isReadOnly());
        if (advancedMode) {
            pipelineTree.setPipelineTreeBuilder(new DefaultPipelineTreeBuilder());
        } else {
            pipelineTree.setPipelineTreeBuilder(new SimplePipelineTreeBuilder());
        }
    }

    @Override
    public void viewSource() {
        if (isDirty()) {
            AlertEvent.fireError(
                    this,
                    "You must save changes to this pipeline before you can view the source",
                    null);
        } else {
            final EditorPresenter jsonEditor = makeDialog(jsonEditorProvider);
            jsonEditor.setMode(AceEditorMode.JSON);
//            jsonEditor.getIndicatorsOption().setAvailable(false);
//            jsonEditor.getIndicatorsOption().setOn(false);
//            jsonEditor.getStylesOption().setOn(true);
            jsonEditor.getView().asWidget().getElement().addClassName("form-control-border default-min-sizes");

            final PopupSize popupSize = PopupSize.resizable(600, 400);
            restFactory
                    .create(PIPELINE_RESOURCE)
                    .method(res -> res.fetchPipelineJson(docRef))
                    .onSuccess(result -> {
                        String text = "";
                        if (result != null) {
                            text = result.getJson();
                        }
                        jsonEditor.setText(text, true);
                        ShowPopupEvent.builder(jsonEditor)
                                .popupType(PopupType.OK_CANCEL_DIALOG)
                                .modal()
                                .popupSize(popupSize)
                                .caption("Pipeline Source")
                                .onShow(e -> jsonEditor.focus())
                                .onHideRequest(e -> {
                                    if (e.isOk()) {
                                        querySave(jsonEditor, e);
                                    } else {
                                        e.hide();
                                    }
                                })
                                .fire();
                    })
                    .onFailure(throwable -> jsonEditor.setErrorText(
                            "Unable to display pipeline source",
                            throwable.getMessage()
                    ))
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    private void querySave(final EditorPresenter jsonEditor,
                           final HidePopupRequestEvent event) {
        ConfirmEvent.fire(PipelineStructurePresenter.this,
                "Are you sure you want to save changes to the underlying JSON?", ok -> {
                    if (ok) {
                        doActualSave(jsonEditor, event);
                    } else {
                        event.hide();
                    }
                });
    }

    private void doActualSave(final EditorPresenter jsonEditor, final HidePopupRequestEvent event) {
        restFactory
                .create(PIPELINE_RESOURCE)
                .method(res -> res.savePipelineJson(new SavePipelineJsonRequest(docRef, jsonEditor.getText())))
                .onSuccess(result -> {
                    // Hide the popup.
                    event.hide();
                    // Reload the entity.
                    RefreshDocumentEvent.fire(PipelineStructurePresenter.this, docRef);
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(this)
                .exec();
    }

    private void enableButtons() {
        if (advancedMode) {
            addMenuItems = getAddMenuItems();
            restoreMenuItems = getRestoreMenuItems();
        } else {
            addMenuItems = null;
            restoreMenuItems = null;
        }

        getView().setAddEnabled(!isReadOnly() && NullSafe.hasItems(addMenuItems));
        getView().setRemoveEnabled(isRemoveEnabled());
        getView().setEditEnabled(isEditEnabled());
        getView().setRestoreEnabled(!isReadOnly() && NullSafe.hasItems(restoreMenuItems));
    }

    private DocRef getParentPipeline() {
        return parentPipeline;
    }

    public PipelineDoc getPipelineDoc() {
        return pipelineDoc;
    }

    private void changeParentPipeline(final DocRef parentPipeline) {
        // Don't do anything if the parent pipeline has not changed.
        if (Objects.equals(this.parentPipeline, parentPipeline)) {
            return;
        }

        this.parentPipeline = parentPipeline;
        this.pipelineDoc = pipelineDoc.copy().parentPipeline(parentPipeline).build();

        if (parentPipeline == null) {
            pipelineModel.setBaseStack(null);
            try {
                pipelineModel.build();
            } catch (final PipelineModelException e) {
                AlertEvent.fireError(this, e.getMessage(), null);
            }

        } else {
            restFactory
                    .create(PIPELINE_RESOURCE)
                    .method(res -> res.fetchPipelineLayers(parentPipeline))
                    .onSuccess(result -> {
                        pipelineModel.setBaseStack(result);

                        try {
                            pipelineModel.build();
                        } catch (final PipelineModelException e) {
                            AlertEvent.fireError(
                                    PipelineStructurePresenter.this,
                                    e.getMessage(),
                                    null);
                        }
                    })
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    // An element dragged, or moved with the keyboard, to another parent in the tree
    private void moveElement(final PipelineElement parent, final PipelineElement child) {
        if (pipelineModel != null && pipelineModel.getParentMap() != null) {
            try {
                // A successful move fires a change event from the model, which is what redraws the
                // tree and re-evaluates whether the pipeline is dirty.
                pipelineModel.moveElement(parent, child);
            } catch (final RuntimeException e) {
                AlertEvent.fireError(this, e.getMessage(), null);
            }
        }
    }

    // --------------------------------------------------------------------------------


    public interface PipelineStructureView extends View, HasUiHandlers<PipelineStructureUiHandlers> {

        void setInheritanceTree(View view);

        void setTreeView(IsWidget view);

        void setProperties(IsWidget view);

        void setPipelineReferences(IsWidget view);

        void setAddEnabled(boolean enabled);

        void setRemoveEnabled(boolean enabled);

        void setEditEnabled(boolean enabled);

        void setRestoreEnabled(boolean enabled);
    }


    // --------------------------------------------------------------------------------


    private class AddPipelineElementCommand implements Command {

        private final PipelineElementType elementType;

        public AddPipelineElementCommand(final PipelineElementType elementType) {
            this.elementType = elementType;
        }

        @Override
        public void execute() {
            final PipelineElement selectedElement = pipelineTree.getSelectionModel().getSelectedObject();
            if (selectedElement != null && elementType != null) {
                // We need to suggest a unique name for the element
                final Set<String> existingNames = pipelineTree.getNames();
                final String suggestedNameBase = elementType.getDisplayValue();
                String suggestedName = suggestedNameBase;

                int suffix = 2;
                if (existingNames.contains(suggestedName)) {
                    do {
                        suggestedName = suggestedNameBase + suffix++;
                    } while (existingNames.contains(suggestedName));
                }

                newElementProvider.get().edit(PipelineStructurePresenter.this,
                        "Create Element",
                        suggestedName,
                        null,
                        (enteredName, enteredDescription) -> {
                            final String id = UUID.randomUUID().toString();
                            final String name = enteredName.trim();
                            final String description = NullSafe.isBlankString(enteredDescription)
                                    ? null
                                    : enteredDescription;
                            try {
                                final PipelineElement newElement = pipelineModel.addElement(
                                        selectedElement,
                                        elementType,
                                        id,
                                        name,
                                        description);
                                pipelineTree.getSelectionModel().setSelected(newElement, true);
                            } catch (final RuntimeException ex) {
                                AlertEvent.fireError(PipelineStructurePresenter.this, ex.getMessage(), null);
                            }
                        });
            }
        }
    }


    // --------------------------------------------------------------------------------


    private class RestorePipelineElementCommand implements Command {

        private final PipelineElement element;

        public RestorePipelineElementCommand(final PipelineElement element) {
            this.element = element;
        }

        @Override
        public void execute() {
            final PipelineElement selectedElement = pipelineTree.getSelectionModel().getSelectedObject();
            if (selectedElement != null) {
                try {
                    pipelineModel.addExistingElement(selectedElement, element);
                    pipelineTree.getSelectionModel().setSelected(element, true);
                } catch (final RuntimeException e) {
                    AlertEvent.fireError(PipelineStructurePresenter.this, e.getMessage(), null);
                }
            }
        }
    }
}
