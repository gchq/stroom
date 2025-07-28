/*
 * Copyright 2024 Crown Copyright
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
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.RefreshDocumentEvent;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.PipelineResource;
import stroom.pipeline.shared.SavePipelineJsonRequest;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.structure.client.presenter.PipelineStructurePresenter.PipelineStructureView;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.ModelStringUtil;
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
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class PipelineStructurePresenter extends DocumentEditPresenter<PipelineStructureView, PipelineDoc>
        implements PipelineStructureUiHandlers {

    private static final PipelineResource PIPELINE_RESOURCE = GWT.create(PipelineResource.class);
    private static final DocRef NULL_SELECTION = DocRef.builder()
            .uuid("")
            .name("None")
            .type("")
            .build();

    private final DocSelectionBoxPresenter pipelinePresenter;
    private final RestFactory restFactory;
    private final NewElementPresenter newElementPresenter;
    private final PropertyListPresenter propertyListPresenter;
    private final PipelineReferenceListPresenter pipelineReferenceListPresenter;
    private final Provider<EditorPresenter> jsonEditorProvider;
    private final PipelineTreePresenter pipelineTreePresenter;
    private final PipelineElementTypesFactory pipelineElementTypesFactory;
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
                                      final PipelineTreePresenter pipelineTreePresenter,
                                      final DocSelectionBoxPresenter pipelinePresenter,
                                      final RestFactory restFactory,
                                      final NewElementPresenter newElementPresenter,
                                      final PropertyListPresenter propertyListPresenter,
                                      final PipelineReferenceListPresenter pipelineReferenceListPresenter,
                                      final Provider<EditorPresenter> jsonEditorProvider,
                                      final PipelineElementTypesFactory pipelineElementTypesFactory) {
        super(eventBus, view);
        this.pipelineTreePresenter = pipelineTreePresenter;
        this.pipelinePresenter = pipelinePresenter;
        this.restFactory = restFactory;
        this.newElementPresenter = newElementPresenter;
        this.propertyListPresenter = propertyListPresenter;
        this.pipelineReferenceListPresenter = pipelineReferenceListPresenter;
        this.jsonEditorProvider = jsonEditorProvider;
        this.pipelineElementTypesFactory = pipelineElementTypesFactory;

        getView().setUiHandlers(this);
        getView().setInheritanceTree(pipelinePresenter.getView());
        getView().setTreeView(pipelineTreePresenter.getView());
        getView().setProperties(propertyListPresenter.getView());
        getView().setPipelineReferences(pipelineReferenceListPresenter.getView());

        pipelinePresenter.setIncludedTypes(PipelineDoc.TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermission.USE);

        // Get a map of all available elements and properties.
        setAdvancedMode(true);
        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();

        final DirtyHandler dirtyHandler = event -> setDirty(true);

        registerHandler(propertyListPresenter.addDirtyHandler(dirtyHandler));
        registerHandler(pipelineReferenceListPresenter.addDirtyHandler(dirtyHandler));
        registerHandler(pipelinePresenter.addDataSelectionHandler(event -> {
            final DocRef selectedDocRef = event.getSelectedItem();
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
        registerHandler(
                pipelineTreePresenter.getSelectionModel().addSelectionChangeHandler(event -> {
                    selectedElement = pipelineTreePresenter.getSelectionModel().getSelectedObject();

                    propertyListPresenter.setPipelineModel(pipelineModel);
                    propertyListPresenter.setCurrentElement(selectedElement);

                    pipelineReferenceListPresenter.setPipeline(pipelineDoc);
                    pipelineReferenceListPresenter.setPipelineModel(pipelineModel);
                    pipelineReferenceListPresenter.setCurrentElement(selectedElement);

                    enableButtons();
                }));
        registerHandler(pipelineTreePresenter.addDirtyHandler(event -> setDirty(event.isDirty())));
        registerHandler(pipelineTreePresenter.addContextMenuHandler(event -> {
            if (advancedMode && selectedElement != null) {
                final List<Item> menuItems = addPipelineActionsToMenu();
                if (NullSafe.hasItems(menuItems)) {
                    showMenu(menuItems, event.getPopupPosition());
                }
            }
        }));
    }

    @Override
    protected void onRead(final DocRef docRef, final PipelineDoc document, final boolean readOnly) {
        pipelineElementTypesFactory.get(this, elementTypes -> {
            pipelinePresenter.setEnabled(!readOnly);
            propertyListPresenter.setReadOnly(readOnly);
            pipelineReferenceListPresenter.setReadOnly(readOnly);
            enableButtons();

            if (document != null) {
                final PipelineElement previousSelection = this.selectedElement;

                this.docRef = docRef;
                this.pipelineDoc = document;
                this.selectedElement = null;

                if (pipelineModel == null) {
                    pipelineModel = new PipelineModel(elementTypes);
                    pipelineTreePresenter.setModel(pipelineModel);
                }

                if (document.getParentPipeline() != null) {
                    this.parentPipeline = document.getParentPipeline();
                }
                pipelinePresenter.setSelectedEntityReference(document.getParentPipeline(), true);

                restFactory
                        .create(PIPELINE_RESOURCE)
                        .method(res -> res.fetchPipelineLayers(docRef))
                        .onSuccess(result -> {
                            final PipelineLayer pipelineLayer = result.get(result.size() - 1);
                            final List<PipelineLayer> baseStack = new ArrayList<>(result.size() - 1);

                            // If there is a stack of pipeline data then we need
                            // to make sure changes are reflected appropriately.
                            for (int i = 0; i < result.size() - 1; i++) {
                                baseStack.add(result.get(i));
                            }

                            try {
                                pipelineModel.setPipelineLayer(pipelineLayer);
                                pipelineModel.setBaseStack(baseStack);
                                pipelineModel.build();
                                pipelineTreePresenter.getSelectionModel().setSelected(previousSelection, true);

                                // We have just loaded the pipeline so set dirty to
                                // false.
                                setDirty(false);
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
        });
    }

    @Override
    protected PipelineDoc onWrite(final PipelineDoc document) {
        // Only write if we have been revealed and therefore created a pipeline
        // model.
        if (pipelineModel != null) {
            try {
                // Set the parent pipeline.
                document.setParentPipeline(getParentPipeline());

                // Diff base and combined to create fresh pipeline data.
                final PipelineData pipelineData = pipelineModel.diff();
                document.setPipelineData(pipelineData);
            } catch (final RuntimeException e) {
                AlertEvent.fireError(this, e.getMessage(), null);
            }
        }
        return document;
    }

    @Override
    public void onAdd(final ClickEvent event) {
        if (addMenuItems != null && !addMenuItems.isEmpty()) {
            showMenu(event, addMenuItems);
        }
    }

    @Override
    public void onRestore(final ClickEvent event) {
        if (restoreMenuItems != null && !restoreMenuItems.isEmpty()) {
            showMenu(event, restoreMenuItems);
        }
    }

    @Override
    public void onRemove(final ClickEvent event) {
        try {
            final PipelineElement selectedElement = pipelineTreePresenter.getSelectionModel().getSelectedObject();
            if (advancedMode && selectedElement != null && !PipelineModel.SOURCE_ELEMENT.equals(selectedElement)) {
                final PipelineElement parentElement = pipelineModel.getParentMap().get(selectedElement);
                pipelineModel.removeElement(selectedElement);
                if (parentElement != null) {
                    pipelineTreePresenter.getSelectionModel().setSelected(parentElement, true);
                }
                setDirty(true);
            }
        } catch (final PipelineModelException e) {
            AlertEvent.fireError(this, e.getMessage(), null);
        }
    }

    private void onRenameElement() {
        final PipelineElement selected = pipelineTreePresenter.getSelectionModel().getSelectedObject();
        if (selected != null) {
            final HidePopupRequestEvent.Handler handler = e -> {
                if (e.isOk()) {
                    final String newName = newElementPresenter.getElementName();
                    final String newDescription = newElementPresenter.getElementDescription();
                    if (newName != null && !newName.trim().isEmpty()) {
                        try {
                            final PipelineElement renamedElement =
                                    pipelineModel.renameElement(selected, newName.trim());
                            if (newDescription != null) {
                                pipelineModel.changeElementDescription(renamedElement, newDescription);
                            }
                            pipelineTreePresenter.getSelectionModel().setSelected(renamedElement, true);
                            setDirty(true);
                        } catch (final RuntimeException ex) {
                            AlertEvent.fireError(this, ex.getMessage(), null);
                        }
                    }
                }
                e.hide();
            };
            newElementPresenter.show(
                    pipelineModel.getElementType(selected),
                    handler,
                    selected.getName() != null
                            ? selected.getName()
                            : selected.getId().toString(),
                    "Edit Element"
            );
        }
    }

    private List<Item> addPipelineActionsToMenu() {
        final PipelineElement selected = pipelineTreePresenter.getSelectionModel().getSelectedObject();

        final List<Item> menuItems = new ArrayList<>();

        menuItems.add(new IconParentMenuItem.Builder()
                .priority(0)
                .icon(SvgImage.ADD)
                .text("Add")
                .enabled(addMenuItems != null && !addMenuItems.isEmpty())
                .children(addMenuItems)
                .build());
        menuItems.add(new IconParentMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.UNDO)
                .text("Restore")
                .enabled(restoreMenuItems != null && !restoreMenuItems.isEmpty())
                .children(restoreMenuItems)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(2)
                .icon(SvgImage.REMOVE)
                .text("Remove")
                .enabled(selected != null)
                .command(() -> onRemove(null))
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(3)
                .icon(SvgImage.EDIT)
                .text("Edit")
                .enabled(selected != null)
                .command(this::onRenameElement)
                .build());

        return menuItems;
    }

    private List<Item> getAddMenuItems() {
        final List<Item> menuItems = new ArrayList<>();

        final PipelineElement parent = pipelineTreePresenter.getSelectionModel().getSelectedObject();
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
                            final String type = pipelineElementType.getType();
                            final SvgImage icon = pipelineElementType.getIcon();
                            final Item item = new IconMenuItem.Builder()
                                    .priority(j++)
                                    .icon(icon)
                                    .text(type)
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

        final PipelineElement parent = pipelineTreePresenter.getSelectionModel().getSelectedObject();
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

    private void showMenu(final ClickEvent event, final List<Item> menuItems) {
        final com.google.gwt.dom.client.Element target = event.getNativeEvent().getEventTarget().cast();
        Rect relativeRect = new Rect(target);
        relativeRect = relativeRect.grow(3);
        final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.BELOW);
        showMenu(menuItems, popupPosition);
    }

    private void showMenu(final List<Item> menuItems,
                          final PopupPosition popupPosition) {
        ShowMenuEvent
                .builder()
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
        pipelineTreePresenter.setAllowDragging(advancedMode);
        if (advancedMode) {
            pipelineTreePresenter.setPipelineTreeBuilder(new DefaultPipelineTreeBuilder());
        } else {
            pipelineTreePresenter.setPipelineTreeBuilder(new SimplePipelineTreeBuilder());
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
            final EditorPresenter jsonEditor = jsonEditorProvider.get();
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
        getView().setRestoreEnabled(!isReadOnly() && NullSafe.hasItems(restoreMenuItems));
        getView().setRemoveEnabled(!isReadOnly()
                                   && advancedMode
                                   && selectedElement != null
                                   && !PipelineModel.SOURCE_ELEMENT.equals(selectedElement));
    }

    private DocRef getParentPipeline() {
        return parentPipeline;
    }

    private void changeParentPipeline(final DocRef parentPipeline) {
        // Don't do anything if the parent pipeline has not changed.
        if (Objects.equals(this.parentPipeline, parentPipeline)) {
            return;
        }

        this.parentPipeline = parentPipeline;

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

        // We have changed the parent pipeline so set dirty.
        setDirty(true);
    }


    // --------------------------------------------------------------------------------


    public interface PipelineStructureView extends View, HasUiHandlers<PipelineStructureUiHandlers> {

        void setInheritanceTree(View view);

        void setTreeView(View view);

        void setProperties(View view);

        void setPipelineReferences(View view);

        void setAddEnabled(boolean enabled);

        void setRestoreEnabled(boolean enabled);

        void setRemoveEnabled(boolean enabled);
    }


    // --------------------------------------------------------------------------------


    private class AddPipelineElementCommand implements Command {

        private final PipelineElementType elementType;

        public AddPipelineElementCommand(final PipelineElementType elementType) {
            this.elementType = elementType;
        }

        @Override
        public void execute() {
            final PipelineElement selectedElement = pipelineTreePresenter.getSelectionModel().getSelectedObject();
            if (selectedElement != null && elementType != null) {
                final HidePopupRequestEvent.Handler handler = e -> {
                    if (e.isOk()) {
                        final String id = UUID.randomUUID().toString();
                        final String name = newElementPresenter.getElementName();
                        final String description = newElementPresenter.getElementDescription();
                        final PipelineElementType elementType = newElementPresenter.getElementInfo();
                        try {
                            final PipelineElement newElement = pipelineModel.addElement(
                                    selectedElement, elementType, id, name, description);
                            pipelineTreePresenter.getSelectionModel().setSelected(newElement, true);
                            setDirty(true);
                        } catch (final RuntimeException ex) {
                            AlertEvent.fireError(
                                    PipelineStructurePresenter.this,
                                    ex.getMessage(),
                                    e::reset);
                        }
                    }
                    e.hide();
                };

                // We need to suggest a unique id for the element, else the user will get an
                // error if they click OK with a dup id.
                final Set<String> existingIds = pipelineTreePresenter.getIds();
                final String suggestedIdBase = ModelStringUtil.toCamelCase(elementType.getType());
                String suggestedId = suggestedIdBase;

                int suffix = 2;
                if (existingIds.contains(suggestedId)) {
                    do {
                        suggestedId = suggestedIdBase + suffix++;
                    } while (existingIds.contains(suggestedId));
                }

                newElementPresenter.show(elementType, handler, suggestedId, "Create Element");
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
            final PipelineElement selectedElement = pipelineTreePresenter.getSelectionModel().getSelectedObject();
            if (selectedElement != null) {
                try {
                    pipelineModel.addExistingElement(selectedElement, element);
                    pipelineTreePresenter.getSelectionModel().setSelected(element, true);
                    setDirty(true);
                } catch (final RuntimeException e) {
                    AlertEvent.fireError(PipelineStructurePresenter.this, e.getMessage(), null);
                }
            }
        }
    }
}
