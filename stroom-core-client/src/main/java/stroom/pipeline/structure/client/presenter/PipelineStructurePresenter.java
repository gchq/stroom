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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.Command;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.presenter.AlertCallback;
import stroom.alert.client.presenter.ConfirmCallback;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.client.event.ReloadEntityEvent;
import stroom.entity.client.presenter.HasRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.DocRefUtil;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.explorer.shared.EntityData;
import stroom.explorer.shared.ExplorerData;
import stroom.pipeline.shared.FetchPipelineDataAction;
import stroom.pipeline.shared.FetchPipelineXMLAction;
import stroom.pipeline.shared.FetchPropertyTypesAction;
import stroom.pipeline.shared.FetchPropertyTypesResult;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.SavePipelineXMLAction;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.structure.client.view.PipelineImageUtil;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.client.BorderUtil;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.SharedList;
import stroom.util.shared.SharedString;
import stroom.util.shared.VoidResult;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.contextmenu.client.event.ContextMenuEvent;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItems;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.menu.client.presenter.SimpleParentMenuItem;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.VerticalLocation;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.presenter.Icon;
import stroom.widget.tab.client.presenter.ImageIcon;
import stroom.xmleditor.client.presenter.XMLEditorPresenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class PipelineStructurePresenter extends MyPresenterWidget<PipelineStructurePresenter.PipelineStructureView>
        implements HasRead<PipelineEntity>, HasWrite<PipelineEntity>, HasDirtyHandlers, PipelineStructureUiHandlers {
    private final EntityDropDownPresenter pipelinePresenter;
    private final MenuListPresenter menuListPresenter;
    private final ClientDispatchAsync dispatcher;
    private final NewElementPresenter newElementPresenter;
    private final PropertyListPresenter propertyListPresenter;
    private final PipelineReferenceListPresenter pipelineReferenceListPresenter;
    private final Provider<XMLEditorPresenter> xmlEditorProvider;
    private final PipelineTreePresenter pipelineTreePresenter;
    private boolean dirty;
    private PipelineElement selectedElement;
    private PipelineModel pipelineModel;
    private PipelineEntity pipelineEntity;
    private DocRef parentPipeline;
    private Map<Category, List<PipelineElementType>> elementTypes;
    private boolean advancedMode;

    @Inject
    public PipelineStructurePresenter(final EventBus eventBus, final PipelineStructureView view,
                                      final PipelineTreePresenter pipelineTreePresenter, final EntityDropDownPresenter pipelinePresenter,
                                      final ClientDispatchAsync dispatcher, final MenuListPresenter menuListPresenter,
                                      final NewElementPresenter newElementPresenter, final PropertyListPresenter propertyListPresenter,
                                      final PipelineReferenceListPresenter pipelineReferenceListPresenter,
                                      final Provider<XMLEditorPresenter> xmlEditorProvider, final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.pipelineTreePresenter = pipelineTreePresenter;
        this.pipelinePresenter = pipelinePresenter;
        this.menuListPresenter = menuListPresenter;
        this.dispatcher = dispatcher;
        this.newElementPresenter = newElementPresenter;
        this.propertyListPresenter = propertyListPresenter;
        this.pipelineReferenceListPresenter = pipelineReferenceListPresenter;
        this.xmlEditorProvider = xmlEditorProvider;

        getView().setUiHandlers(this);
        getView().setInheritanceTree(pipelinePresenter.getView());
        getView().setTreeView(pipelineTreePresenter.getView());
        getView().setProperties(propertyListPresenter.getView());
        getView().setPipelineReferences(pipelineReferenceListPresenter.getView());

        pipelinePresenter.setIncludedTypes(PipelineEntity.ENTITY_TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        // Get a map of all available elements and properties.
        dispatcher.execute(new FetchPropertyTypesAction(), new AsyncCallbackAdaptor<FetchPropertyTypesResult>() {
            @Override
            public void onSuccess(final FetchPropertyTypesResult result) {
                final Map<PipelineElementType, Map<String, PipelinePropertyType>> propertyTypes = result
                        .getPropertyTypes();

                propertyListPresenter.setPropertyTypes(propertyTypes);
                pipelineReferenceListPresenter.setPropertyTypes(propertyTypes);

                elementTypes = new HashMap<>();

                for (final PipelineElementType elementType : propertyTypes.keySet()) {
                    List<PipelineElementType> list = elementTypes.get(elementType.getCategory());
                    if (list == null) {
                        list = new ArrayList<>();
                        elementTypes.put(elementType.getCategory(), list);
                    }

                    list.add(elementType);
                }

                for (final List<PipelineElementType> types : elementTypes.values()) {
                    Collections.sort(types);
                }
            }
        });

        setAdvancedMode(false);
        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();

        final DirtyHandler dirtyHandler = new DirtyHandler() {
            @Override
            public void onDirty(final DirtyEvent event) {
                setDirty(true);
            }
        };

        registerHandler(propertyListPresenter.addDirtyHandler(dirtyHandler));
        registerHandler(pipelineReferenceListPresenter.addDirtyHandler(dirtyHandler));
        registerHandler(pipelinePresenter.addDataSelectionHandler(new DataSelectionHandler<ExplorerData>() {
            @Override
            public void onSelection(final DataSelectionEvent<ExplorerData> event) {
                if (event.getSelectedItem() != null) {
                    final EntityData entityData = (EntityData) event.getSelectedItem();
                    if (EqualsUtil.isEquals(entityData.getDocRef().getUuid(), pipelineEntity.getUuid())) {
                        AlertEvent.fireWarn(PipelineStructurePresenter.this, "A pipeline cannot inherit from itself",
                                new AlertCallback() {
                                    @Override
                                    public void onClose() {
                                        // Reset selection.
                                        pipelinePresenter.setSelectedEntityReference(getParentPipeline());
                                    }
                                });
                    } else {
                        changeParentPipeline(entityData.getDocRef());
                    }
                } else {
                    changeParentPipeline(null);
                }
            }
        }));
        registerHandler(
                pipelineTreePresenter.getSelectionModel().addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
                    @Override
                    public void onSelectionChange(final SelectionChangeEvent event) {
                        selectedElement = pipelineTreePresenter.getSelectionModel().getSelectedObject();

                        propertyListPresenter.setPipeline(pipelineEntity);
                        propertyListPresenter.setPipelineModel(pipelineModel);
                        propertyListPresenter.setCurrentElement(selectedElement);

                        pipelineReferenceListPresenter.setPipeline(pipelineEntity);
                        pipelineReferenceListPresenter.setPipelineModel(pipelineModel);
                        pipelineReferenceListPresenter.setCurrentElement(selectedElement);

                        enableButtons();
                    }
                }));
        registerHandler(pipelineTreePresenter.addDirtyHandler(new DirtyHandler() {
            @Override
            public void onDirty(final DirtyEvent event) {
                setDirty(event.isDirty());
            }
        }));
        registerHandler(pipelineTreePresenter.addContextMenuHandler(new ContextMenuEvent.Handler() {
            @Override
            public void onContextMenu(final ContextMenuEvent event) {
                if (advancedMode && selectedElement != null) {
                    final List<Item> menuItems = addPipelineActionsToMenu();
                    if (menuItems != null && menuItems.size() > 0) {
                        final PopupPosition popupPosition = new PopupPosition(event.getX(), event.getY());
                        showMenu(popupPosition, menuItems);
                    }
                }
            }
        }));
    }

    @Override
    public void read(final PipelineEntity pipelineEntity) {
        final PipelineElement previousSelection = this.selectedElement;

        this.pipelineEntity = pipelineEntity;
        this.selectedElement = null;

        if (pipelineModel == null) {
            pipelineModel = new PipelineModel();
            pipelineTreePresenter.setModel(pipelineModel);
        }

        if (pipelineEntity.getParentPipeline() != null) {
            this.parentPipeline = pipelineEntity.getParentPipeline();
        }
        pipelinePresenter.setSelectedEntityReference(pipelineEntity.getParentPipeline());

        final FetchPipelineDataAction action = new FetchPipelineDataAction(DocRefUtil.create(pipelineEntity));
        dispatcher.execute(action, new AsyncCallbackAdaptor<SharedList<PipelineData>>() {
            @Override
            public void onSuccess(final SharedList<PipelineData> result) {
                final PipelineData pipelineData = result.get(result.size() - 1);
                final List<PipelineData> baseStack = new ArrayList<>(result.size() - 1);

                // If there is a stack of pipeline data then we need
                // to make sure changes are reflected appropriately.
                for (int i = 0; i < result.size() - 1; i++) {
                    baseStack.add(result.get(i));
                }

                try {
                    pipelineModel.setPipelineData(pipelineData);
                    pipelineModel.setBaseStack(baseStack);
                    pipelineModel.build();

                    pipelineTreePresenter.getSelectionModel().setSelected(previousSelection, true);

                    // We have just loaded the pipeline so set dirty to
                    // false.
                    setDirty(false);
                } catch (final PipelineModelException e) {
                    AlertEvent.fireError(PipelineStructurePresenter.this, e.getMessage(), null);
                }
            }
        });
    }

    @Override
    public void write(final PipelineEntity pipeline) {
        // Only write if we have been revealed and therefore created a pipeline
        // model.
        if (pipelineModel != null) {
            try {
                // Set the parent pipeline.
                pipeline.setParentPipeline(getParentPipeline());

                // Diff base and combined to create fresh pipeline data.
                final PipelineData pipelineData = pipelineModel.diff();
                pipeline.setPipelineData(pipelineData);
            } catch (final Exception e) {
                AlertEvent.fireError(this, e.getMessage(), null);
            }
        }
    }

    @Override
    public void onAdd(final ClickEvent event) {
        if (advancedMode && selectedElement != null) {
            final List<Item> menuItems = addPipelineElementTypesToMenu();
            if (menuItems != null && menuItems.size() > 0) {
                showMenu(event, menuItems);
            }
        }
    }

    @Override
    public void onRestore(final ClickEvent event) {
        if (advancedMode && selectedElement != null) {
            final List<Item> menuItems = addRestoreElementsToMenu();
            if (menuItems != null && menuItems.size() > 0) {
                showMenu(event, menuItems);
            }
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

    private List<Item> addPipelineActionsToMenu() {
        final List<Item> menuItems = new ArrayList<Item>();
        final List<Item> addPipelineElementTypes = addPipelineElementTypesToMenu();
        final List<Item> addRestoreElements = addRestoreElementsToMenu();

        if (addPipelineElementTypes != null) {
            final Item parentItem = new SimpleParentMenuItem(0, GlyphIcons.ADD, GlyphIcons.ADD, "Add", null, true, addPipelineElementTypes);
            menuItems.add(parentItem);
        }

        if (addRestoreElements != null) {
            final Item parentItem = new SimpleParentMenuItem(1, GlyphIcons.UNDO, GlyphIcons.UNDO, "Restore", null, true, addRestoreElements);
            menuItems.add(parentItem);
        }

        final Item item = new IconMenuItem(2, GlyphIcons.REMOVE, GlyphIcons.REMOVE, "Remove", null, true,
                new Command() {
                    @Override
                    public void execute() {
                        onRemove(null);
                    }
                });
        menuItems.add(item);

        return menuItems;
    }

    private List<Item> addPipelineElementTypesToMenu() {
        final List<Item> menuItems = new ArrayList<Item>();

        for (final Entry<Category, List<PipelineElementType>> entry : elementTypes.entrySet()) {
            final Category category = entry.getKey();
            if (category.getOrder() >= 0) {
                final List<Item> children = new ArrayList<Item>();
                int j = 0;
                for (final PipelineElementType pipelineElementType : entry.getValue()) {
                    final String type = pipelineElementType.getType();
                    final Icon icon = ImageIcon.create(PipelineImageUtil.getImage(pipelineElementType));

                    final Item item = new IconMenuItem(j++, icon, null, type, null, true,
                            new AddPipelineElementCommand(pipelineElementType));
                    children.add(item);
                }

                final Item parentItem = new SimpleParentMenuItem(category.getOrder(), null, null,
                        category.getDisplayValue(), null, true, children);
                menuItems.add(parentItem);
                Collections.sort(menuItems, new MenuItems.ItemComparator());
            }
        }

        return menuItems;
    }

    private List<Item> addRestoreElementsToMenu() {
        final List<PipelineElement> existingElements = getExistingElements();

        if (existingElements == null || existingElements.size() == 0) {
            return null;
        }

        final Map<Category, List<Item>> categoryMenuItems = new HashMap<Category, List<Item>>();
        int pos = 0;
        for (final PipelineElement element : existingElements) {
            final PipelineElementType pipelineElementType = element.getElementType();
            final Category category = pipelineElementType.getCategory();

            List<Item> items = categoryMenuItems.get(category);
            if (items == null) {
                items = new ArrayList<Item>();
                categoryMenuItems.put(category, items);
            }

            final String type = pipelineElementType.getType();
            final Icon icon = ImageIcon.create(PipelineImageUtil.getImage(pipelineElementType));

            final Item item = new IconMenuItem(pos++, icon, null, element.getId(), null, true,
                    new RestorePipelineElementCommand(element));
            items.add(item);
        }

        final List<Item> menuItems = new ArrayList<Item>();
        for (final Entry<Category, List<Item>> entry : categoryMenuItems.entrySet()) {
            final Category category = entry.getKey();
            final List<Item> children = entry.getValue();
            final Item parentItem = new SimpleParentMenuItem(category.getOrder(), null, null,
                    category.getDisplayValue(), null, true, children);
            menuItems.add(parentItem);
        }

        return menuItems;
    }

    private void showMenu(final ClickEvent event, final List<Item> menuItems) {
        final com.google.gwt.dom.client.Element target = event.getNativeEvent().getEventTarget().cast();
        final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteLeft() - 3, target.getAbsoluteRight(),
                target.getAbsoluteTop(), target.getAbsoluteBottom() + 3, null, VerticalLocation.BELOW);
        showMenu(popupPosition, menuItems);
    }

    private void showMenu(final PopupPosition popupPosition, final List<Item> menuItems) {
        menuListPresenter.setData(menuItems);

        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                HidePopupEvent.fire(PipelineStructurePresenter.this, menuListPresenter);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
            }
        };
        ShowPopupEvent.fire(this, menuListPresenter, PopupType.POPUP, popupPosition, popupUiHandlers);
    }

    private List<PipelineElement> getExistingElements() {
        final List<PipelineElement> existingElements = new ArrayList<>();

        if (selectedElement != null) {
            final List<PipelineElement> removedElements = pipelineModel.getRemovedElements();

            if (removedElements != null) {
                for (final PipelineElement element : removedElements) {
                    existingElements.add(element);
                }
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
        if (dirty) {
            AlertEvent.fireError(this, "You must save changes to this pipeline before you can view the source", null);
        } else {
            final XMLEditorPresenter xmlEditor = xmlEditorProvider.get();
            xmlEditor.getIndicatorsOption().setAvailable(false);
            xmlEditor.getIndicatorsOption().setOn(false);
            xmlEditor.getStylesOption().setOn(true);
            BorderUtil.addBorder(xmlEditor.getView().asWidget().getElement());

            final PopupSize popupSize = new PopupSize(600, 400, true);
            final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        querySave(xmlEditor);
                    } else {
                        HidePopupEvent.fire(PipelineStructurePresenter.this, xmlEditor, autoClose, ok);
                    }
                }
            };

            dispatcher.execute(new FetchPipelineXMLAction(pipelineEntity.getId()),
                    new AsyncCallbackAdaptor<SharedString>() {
                        @Override
                        public void onSuccess(final SharedString result) {
                            String text = "";
                            if (result != null) {
                                text = result.toString();
                            }
                            xmlEditor.setText(text, true);
                            ShowPopupEvent.fire(PipelineStructurePresenter.this, xmlEditor, PopupType.OK_CANCEL_DIALOG,
                                    popupSize, "Pipeline Source", popupUiHandlers);
                        }
                    });
        }
    }

    private void querySave(final XMLEditorPresenter xmlEditor) {
        ConfirmEvent.fire(PipelineStructurePresenter.this,
                "Are you sure you want to save changes to the underlying XML?", new ConfirmCallback() {
                    @Override
                    public void onResult(final boolean ok) {
                        if (ok) {
                            doActualSave(xmlEditor);
                        } else {
                            HidePopupEvent.fire(PipelineStructurePresenter.this, xmlEditor, false, false);
                        }
                    }
                });
    }

    private void doActualSave(final XMLEditorPresenter xmlEditor) {
        dispatcher.execute(new SavePipelineXMLAction(pipelineEntity.getId(), xmlEditor.getText()),
                new AsyncCallbackAdaptor<VoidResult>() {
                    @Override
                    public void onSuccess(final VoidResult result) {
                        // Hide the popup.
                        HidePopupEvent.fire(PipelineStructurePresenter.this, xmlEditor, false, true);
                        // Reload the entity.
                        ReloadEntityEvent.fire(PipelineStructurePresenter.this, pipelineEntity);
                    }
                });
    }

    private void enableButtons() {
        PipelineElementType elementType = null;

        if (selectedElement != null) {
            elementType = selectedElement.getElementType();
        }

        getView().setAddEnabled(
                advancedMode && elementType != null && elementType.hasRole(PipelineElementType.ROLE_HAS_TARGETS));
        getView().setRestoreEnabled(
                advancedMode && elementType != null && elementType.hasRole(PipelineElementType.ROLE_HAS_TARGETS));
        getView().setRemoveEnabled(
                advancedMode && selectedElement != null && !PipelineModel.SOURCE_ELEMENT.equals(selectedElement));
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    private void setDirty(final boolean dirty) {
        this.dirty = dirty;
        if (dirty) {
            DirtyEvent.fire(this, dirty);
        }
    }

    private DocRef getParentPipeline() {
        return parentPipeline;
    }

    private void changeParentPipeline(final DocRef parentPipeline) {
        // Don't do anything if the parent pipeline has not changed.
        if (EqualsUtil.isEquals(this.parentPipeline, parentPipeline)) {
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
            final FetchPipelineDataAction action = new FetchPipelineDataAction(parentPipeline);
            dispatcher.execute(action, new AsyncCallbackAdaptor<SharedList<PipelineData>>() {
                @Override
                public void onSuccess(final SharedList<PipelineData> result) {
                    pipelineModel.setBaseStack(result);

                    try {
                        pipelineModel.build();
                    } catch (final PipelineModelException e) {
                        AlertEvent.fireError(PipelineStructurePresenter.this, e.getMessage(), null);
                    }
                }
            });
        }

        // We have changed the parent pipeline so set dirty.
        setDirty(true);
    }

    public interface PipelineStructureView extends View, HasUiHandlers<PipelineStructureUiHandlers> {
        void setInheritanceTree(View view);

        void setTreeView(View view);

        void setProperties(View view);

        void setPipelineReferences(View view);

        void setAddEnabled(boolean enabled);

        void setRestoreEnabled(boolean enabled);

        void setRemoveEnabled(boolean enabled);
    }

    private class AddPipelineElementCommand implements Command {
        private final PipelineElementType elementType;

        public AddPipelineElementCommand(final PipelineElementType elementType) {
            this.elementType = elementType;
        }

        @Override
        public void execute() {
            final PipelineElement selectedElement = pipelineTreePresenter.getSelectionModel().getSelectedObject();
            if (selectedElement != null && elementType != null) {
                final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        if (ok) {
                            final String id = newElementPresenter.getElementId();
                            final PipelineElementType elementType = newElementPresenter.getElementInfo();
                            try {
                                final PipelineElement newElement = pipelineModel.addElement(selectedElement,
                                        elementType, id);
                                pipelineTreePresenter.getSelectionModel().setSelected(newElement, true);
                                setDirty(true);
                            } catch (final Exception e) {
                                AlertEvent.fireError(PipelineStructurePresenter.this, e.getMessage(), null);
                            }
                        }

                        newElementPresenter.hide();
                    }
                };

                newElementPresenter.show(elementType, popupUiHandlers);
            }
        }
    }

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
                } catch (final Exception e) {
                    AlertEvent.fireError(PipelineStructurePresenter.this, e.getMessage(), null);
                }
            }
        }
    }
}
