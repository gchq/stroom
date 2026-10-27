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

package stroom.pipeline.structure.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.RefreshDocumentEvent;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.explorer.client.component.DocSelectionController;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.PipelineResource;
import stroom.pipeline.shared.SavePipelineJsonRequest;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.structure.client.component.PipelineStructure.PipelineStructureAction;
import stroom.pipeline.structure.client.presenter.DefaultPipelineTreeBuilder;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.pipeline.structure.client.presenter.SimplePipelineTreeBuilder;
import stroom.pipeline.structure.client.presenter.StructureValidationUtil;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
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
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Command;
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

/// A pipeline's Structure tab ([PipelineStructure]): its elements, which can be added, removed,
/// edited, restored and moved (in advanced mode, unless the pipeline is read only), the pipeline it
/// inherits from, the selected element's properties and pipeline references, and its source.
public class PipelineStructureController extends Controller {

    private static final PipelineResource PIPELINE_RESOURCE = GWT.create(PipelineResource.class);
    private static final DocRef NULL_SELECTION = DocRef.builder()
            .uuid("")
            .name("None")
            .type("")
            .build();

    private final PipelineStructure view;
    private final DocSelectionController pipelinePresenter;
    private final RestFactory restFactory;
    private final Provider<NewElementController> newElementProvider;
    private final PropertyListController propertyList;
    private final PipelineReferenceListController pipelineReferenceList;
    private final Provider<EditorPresenter> jsonEditorProvider;
    private final PipelineTree pipelineTree;
    private PipelineElement selectedElement;
    private PipelineModel pipelineModel;
    private DocRef docRef;
    private PipelineDoc pipelineDoc;
    // The document as read, to tell whether it has been changed since
    private PipelineDoc readDoc;
    private DocRef parentPipeline;
    private boolean advancedMode;

    private List<Item> addMenuItems;
    private List<Item> restoreMenuItems;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus              The application's event bus.
    /// @param view                  The tab.
    /// @param docSelectionProvider  Makes the Inherit From picker's controller.
    /// @param restFactory           Fetches and saves the pipeline's source.
    /// @param newElementProvider    Makes the dialog that names a new element, or edits one.
    /// @param propertyList          The selected element's properties.
    /// @param pipelineReferenceList The selected element's pipeline references.
    /// @param jsonEditorProvider    Makes the dialog that shows the pipeline's source.
    @Inject
    public PipelineStructureController(final EventBus eventBus,
                                       final PipelineStructure view,
                                       final Provider<DocSelectionController> docSelectionProvider,
                                       final RestFactory restFactory,
                                       final Provider<NewElementController> newElementProvider,
                                       final PropertyListController propertyList,
                                       final PipelineReferenceListController pipelineReferenceList,
                                       final Provider<EditorPresenter> jsonEditorProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(propertyList);
        own(pipelineReferenceList);
        this.pipelineTree = view.getTree();
        this.pipelinePresenter = make(docSelectionProvider);
        pipelinePresenter.attach(view.getInheritBox());
        this.restFactory = restFactory;
        this.newElementProvider = newElementProvider;
        this.propertyList = propertyList;
        this.pipelineReferenceList = pipelineReferenceList;
        this.jsonEditorProvider = jsonEditorProvider;

        view.setOnAction(this::onAction);
        view.setProperties(propertyList);
        view.setPipelineReferences(pipelineReferenceList);

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
                            PipelineStructureController.this,
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

                    propertyList.setPipelineModel(pipelineModel);
                    propertyList.setCurrentElement(selectedElement);

                    pipelineReferenceList.setPipeline(pipelineDoc);
                    pipelineReferenceList.setPipelineModel(pipelineModel);
                    pipelineReferenceList.setCurrentElement(selectedElement);

                    enableButtons();
                }));
        // The Delete key removes the selected element (which has the focus), as Remove does
        pipelineTree.setOnRemove(element -> {
            if (isRemoveEnabled()) {
                onRemove();
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

    private void onAction(final PipelineStructureAction action, final Element element) {
        switch (action) {
            case ADD -> onAdd(element);
            case REMOVE -> onRemove();
            case EDIT -> onEdit();
            case RESTORE -> onRestore(element);
            case VIEW_SOURCE -> viewSource();
        }
    }

    // Whether the pipeline may not be changed, as its document says
    private boolean isReadOnly() {
        return context(Contexts.READ_ONLY);
    }

    /// @return Whether the pipeline's structure has been changed since it was read: what this tab
    /// writes differs from what was read. A read-only pipeline is never changed.
    public boolean isDirty() {
        return !isReadOnly()
               && readDoc != null
               && !Objects.equals(readDoc, write(readDoc));
    }

    /// Shows a pipeline's structure. Its model comes separately ([#setPipelineModel(PipelineModel)]).
    ///
    /// @param docRef   The pipeline.
    /// @param document The pipeline.
    public void read(final DocRef docRef, final PipelineDoc document) {
        this.readDoc = document;
        pipelinePresenter.setReadOnly(isReadOnly());
        // Dragging rearranges the pipeline, so it is only allowed in advanced mode on a document
        // that can be changed
        pipelineTree.setAllowDragging(advancedMode && !isReadOnly());
        propertyList.setTableName(
                "Pipeline '" + docRef.getName() + "' Properties");
        pipelineReferenceList.setTableName(
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

    /// Shows a pipeline's model (its elements and their properties, inherited ones included), and
    /// follows its changes.
    ///
    /// @param model The model.
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
        markChanged();
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
            AlertEvent.fireError(PipelineStructureController.this, e.getMessage(), null);
        }
    }

    /// Writes the pipeline's structure into a copy of it: its parent, and its elements as they
    /// differ from what it inherits.
    ///
    /// @param document The pipeline.
    /// @return A copy of it with the structure shown.
    public PipelineDoc write(final PipelineDoc document) {
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

    private void onAdd(final Element element) {
        if (addMenuItems != null && !addMenuItems.isEmpty()) {
            showMenu(element, "Add Pipeline Element", addMenuItems);
        }
    }

    private void onRestore(final Element element) {
        if (restoreMenuItems != null && !restoreMenuItems.isEmpty()) {
            showMenu(element, "Restore Pipeline Element", restoreMenuItems);
        }
    }

    private void onRemove() {
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

    private void onEdit() {
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
                .command(this::onRemove)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(2)
                .icon(SvgImage.EDIT)
                .text("Edit")
                .enabled(isEditEnabled())
                .command(this::onEdit)
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

    private void showMenu(final Element target, final String name, final List<Item> menuItems) {
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

    private void setAdvancedMode(final boolean advancedMode) {
        this.advancedMode = advancedMode;
        // Dragging rearranges the pipeline, so a read-only document can't be dragged
        pipelineTree.setAllowDragging(advancedMode && !isReadOnly());
        if (advancedMode) {
            pipelineTree.setPipelineTreeBuilder(new DefaultPipelineTreeBuilder());
        } else {
            pipelineTree.setPipelineTreeBuilder(new SimplePipelineTreeBuilder());
        }
    }

    private void viewSource() {
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
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    private void querySave(final EditorPresenter jsonEditor,
                           final HidePopupRequestEvent event) {
        ConfirmEvent.fire(PipelineStructureController.this,
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
                    RefreshDocumentEvent.fire(PipelineStructureController.this, docRef);
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
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

        view.setAddEnabled(!isReadOnly() && NullSafe.hasItems(addMenuItems));
        view.setRemoveEnabled(isRemoveEnabled());
        view.setEditEnabled(isEditEnabled());
        view.setRestoreEnabled(!isReadOnly() && NullSafe.hasItems(restoreMenuItems));
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }

    private DocRef getParentPipeline() {
        return parentPipeline;
    }

    /// @return The pipeline, with any change to its parent.
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
                                    PipelineStructureController.this,
                                    e.getMessage(),
                                    null);
                        }
                    })
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
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

                newElementProvider.get().edit(PipelineStructureController.this,
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
                                AlertEvent.fireError(PipelineStructureController.this, ex.getMessage(), null);
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
                    AlertEvent.fireError(PipelineStructureController.this, e.getMessage(), null);
                }
            }
        }
    }
}
