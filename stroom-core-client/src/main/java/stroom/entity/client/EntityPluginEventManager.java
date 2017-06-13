/*
 *
 *  * Copyright 2017 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.entity.client;

import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.app.client.KeyboardInterceptor;
import stroom.app.client.KeyboardInterceptor.KeyTest;
import stroom.app.client.MenuKeys;
import stroom.app.client.presenter.Plugin;
import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.event.CopyEntityEvent;
import stroom.entity.client.event.CreateEntityEvent;
import stroom.entity.client.event.MoveEntityEvent;
import stroom.entity.client.event.ReloadEntityEvent;
import stroom.entity.client.event.RenameEntityEvent;
import stroom.entity.client.event.SaveAsEntityEvent;
import stroom.entity.client.event.SaveEntityEvent;
import stroom.entity.client.event.ShowCopyEntityDialogEvent;
import stroom.entity.client.event.ShowCreateEntityDialogEvent;
import stroom.entity.client.event.ShowMoveEntityDialogEvent;
import stroom.entity.client.event.ShowPermissionsEntityDialogEvent;
import stroom.entity.client.event.ShowRenameEntityDialogEvent;
import stroom.entity.client.event.ShowSaveAsEntityDialogEvent;
import stroom.entity.client.presenter.EntityEditPresenter;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.Folder;
import stroom.explorer.client.event.ExplorerTreeDeleteEvent;
import stroom.explorer.client.event.ExplorerTreeSelectEvent;
import stroom.explorer.client.event.ShowExplorerMenuEvent;
import stroom.explorer.client.event.ShowNewMenuEvent;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.EntityData;
import stroom.explorer.shared.ExplorerData;
import stroom.explorer.shared.ExplorerPermissions;
import stroom.explorer.shared.FetchExplorerPermissionsAction;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.client.ImageUtil;
import stroom.util.shared.HasDisplayValue;
import stroom.util.shared.SharedMap;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.button.client.SVGIcons;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItem;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.menu.client.presenter.Separator;
import stroom.widget.menu.client.presenter.SimpleParentMenuItem;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.event.RequestCloseAllTabsEvent;
import stroom.widget.tab.client.event.RequestCloseTabEvent;
import stroom.widget.tab.client.presenter.ImageIcon;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.Future;
import stroom.widget.util.client.FutureImpl;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityPluginEventManager extends Plugin {
    private static final KeyTest CTRL_S = event -> event.getCtrlKey() && !event.getShiftKey() && event.getKeyCode() == 'S';
    private static final KeyTest CTRL_SHIFT_S = event -> event.getCtrlKey() && event.getShiftKey() && event.getKeyCode() == 'S';
    private static final KeyTest ALT_W = event -> event.getAltKey() && !event.getShiftKey() && event.getKeyCode() == 'W';
    private static final KeyTest ALT_SHIFT_W = event -> event.getAltKey() && event.getShiftKey() && event.getKeyCode() == 'W';

    private final ClientDispatchAsync dispatcher;
    private final DocumentTypeCache documentTypeCache;
    private final MenuListPresenter menuListPresenter;
    private final ClientSecurityContext clientSecurityContext;
    private final Map<String, EntityPlugin<?>> pluginMap = new HashMap<>();
    private final KeyboardInterceptor keyboardInterceptor;
    private TabData selectedTab;
    private MultiSelectionModel<ExplorerData> selectionModel;

    @Inject
    public EntityPluginEventManager(final EventBus eventBus,
                                    final KeyboardInterceptor keyboardInterceptor, final ClientDispatchAsync dispatcher,
                                    final DocumentTypeCache documentTypeCache, final MenuListPresenter menuListPresenter, final ClientSecurityContext clientSecurityContext) {
        super(eventBus);
        this.keyboardInterceptor = keyboardInterceptor;
        this.dispatcher = dispatcher;
        this.documentTypeCache = documentTypeCache;
        this.menuListPresenter = menuListPresenter;
        this.clientSecurityContext = clientSecurityContext;
    }

    @Override
    protected void onBind() {
        super.onBind();

        // track the currently selected content tab.
        registerHandler(getEventBus().addHandler(ContentTabSelectionChangeEvent.getType(),
                event -> selectedTab = event.getTabData()));

        // 1. Handle entity creation events.
        registerHandler(getEventBus().addHandler(CreateEntityEvent.getType(), event -> {
            final EntityPlugin<?> plugin = pluginMap.get(event.getEntityType());
            if (plugin != null) {
                plugin.createEntity(event.getPresenter(), event.getFolder(), event.getEntityName(), event.getPermissionInheritance());
            }
        }));

        // // 2. Handle requests to close tabs.
        // registerHandler(getEventBus().addHandler(
        // RequestCloseTabEvent.getType(), new RequestCloseTabHandler() {
        // @Override
        // public void onCloseTab(final RequestCloseTabEvent event) {
        // final TabData tabData = event.getTabData();
        // if (tabData instanceof EntityTabData) {
        // final EntityTabData entityTabData = (EntityTabData) tabData;
        // final EntityPlugin<?> plugin = pluginMap
        // .get(entityTabData.getType());
        // if (plugin != null) {
        // plugin.close(entityTabData, false);
        // }
        // }
        // }
        // }));
        //
        // // 3. Handle requests to close all tabs.
        // registerHandler(getEventBus().addHandler(
        // RequestCloseAllTabsEvent.getType(), new CloseAllTabsHandler() {
        // @Override
        // public void onCloseAllTabs(
        // final RequestCloseAllTabsEvent event) {
        // for (final EntityPlugin<?> plugin : pluginMap.values()) {
        // plugin.closeAll(event.isLogoffAfterClose());
        // }
        // }
        // }));

        // 4. Handle explorer events and open items as required.
        registerHandler(
                getEventBus().addHandler(ExplorerTreeSelectEvent.getType(), event -> {
                    // Remember the selection model.
                    if (event.getSelectionModel() != null) {
                        selectionModel = event.getSelectionModel();
                    }

                    if (!event.getSelectionType().isRightClick() && !event.getSelectionType().isMultiSelect()) {
                        final ExplorerData explorerData = event.getSelectionModel().getSelected();
                        if (explorerData != null && explorerData instanceof EntityData) {
                            final EntityData entityData = (EntityData) explorerData;
                            final EntityPlugin<?> plugin = pluginMap.get(entityData.getType());
                            if (plugin != null) {
                                plugin.open(entityData.getDocRef(), event.getSelectionType().isDoubleSelect());
                            }
                        }
                    }
                }));

        // 5. Handle save events.
        registerHandler(getEventBus().addHandler(SaveEntityEvent.getType(), event -> {
            if (isDirty(event.getTabData())) {
                final EntityTabData entityTabData = event.getTabData();
                final EntityPlugin<?> plugin = pluginMap.get(entityTabData.getType());
                if (plugin != null) {
                    plugin.save(entityTabData);
                }
            }
        }));

        // 6. Handle save as events.
        registerHandler(getEventBus().addHandler(SaveAsEntityEvent.getType(), event -> {
            final EntityPlugin<?> plugin = pluginMap.get(event.getTabData().getType());
            if (plugin != null) {
                plugin.copy(event.getDialog(), event.getTabData(), event.getEntityName(), event.getPermissionInheritance());
            }
        }));

        // 7. Save all entities - handled directly.

        // 8.1. Handle entity copy events.
        registerHandler(getEventBus().addHandler(CopyEntityEvent.getType(), event -> {
            final EntityPlugin<?> plugin = pluginMap.get(event.getDocument().getType());
            if (plugin != null) {
                plugin.copyEntity(event.getPresenter(), event.getDocument(), event.getFolder(),
                        event.getName(), event.getPermissionInheritance());
            }
        }));

        // 8.2. Handle entity move events.
        registerHandler(getEventBus().addHandler(MoveEntityEvent.getType(), event -> {
            DocRef folder = null;
            if (event.getFolder() instanceof EntityData) {
                folder = ((EntityData) event.getFolder()).getDocRef();
            }

            final List<ExplorerData> children = event.getChildren();
            for (final ExplorerData child : children) {
                if (child instanceof EntityData) {
                    final EntityData entityData = (EntityData) child;
                    final EntityPlugin<?> plugin = pluginMap.get(entityData.getType());
                    if (plugin != null) {
                        plugin.moveEntity(event.getPresenter(), entityData.getDocRef(), folder, event.getPermissionInheritance());
                    }
                }
            }
        }));

        // 9. Handle entity rename events.
        registerHandler(getEventBus().addHandler(RenameEntityEvent.getType(), event -> {
            final EntityPlugin<?> plugin = pluginMap.get(event.getDocument().getType());
            if (plugin != null) {
                plugin.renameEntity(event.getDialog(), event.getDocument(), event.getEntityName());
            }
        }));

        // 10. Handle entity delete events.
        registerHandler(getEventBus().addHandler(ExplorerTreeDeleteEvent.getType(), event -> {
            if (getSelectedItems().size() > 0) {
                fetchPermissions(getSelectedItems()).onSuccess(documentPermissionMap -> documentTypeCache.fetch().onSuccess(documentTypes -> {
                    final List<ExplorerData> deletableItems = getExplorerDataListWithPermission(documentPermissionMap, DocumentPermissionNames.DELETE);
                    if (deletableItems.size() > 0) {
                        deleteItems(deletableItems);
                    }
                }));
            }
        }));

        // 11. Handle entity reload events.
        registerHandler(getEventBus().addHandler(ReloadEntityEvent.getType(), event -> {
            final EntityPlugin<?> plugin = pluginMap.get(event.getEntity().getType());
            if (plugin != null) {
                plugin.reload(DocRef.create(event.getEntity()));
            }
        }));

        // Not handled as it is done directly.

        registerHandler(getEventBus().addHandler(ShowNewMenuEvent.getType(), event -> {
            if (getSelectedItems().size() == 1) {
                final ExplorerData primarySelection = getPrimarySelection();
                getNewMenuItems(primarySelection).onSuccess(children -> {
                    menuListPresenter.setData(children);

                    final PopupPosition popupPosition = new PopupPosition(event.getX(), event.getY());
                    ShowPopupEvent.fire(EntityPluginEventManager.this, menuListPresenter, PopupType.POPUP,
                            popupPosition, null, event.getElement());
                });
            }
        }));
        registerHandler(getEventBus().addHandler(ShowExplorerMenuEvent.getType(), event -> {
            final List<ExplorerData> selectedItems = getSelectedItems();
            final boolean singleSelection = selectedItems.size() == 1;
            final ExplorerData primarySelection = getPrimarySelection();

            if (selectedItems.size() > 0) {
                fetchPermissions(selectedItems).onSuccess(documentPermissionMap ->
                        documentTypeCache.fetch().onSuccess(documentTypes -> {
                            final List<Item> menuItems = new ArrayList<>();

                            // Only allow the new menu to appear if we have a single selection.
                            addNewMenuItem(menuItems, singleSelection, documentPermissionMap, primarySelection, documentTypes);
                            addModifyMenuItems(menuItems, singleSelection, documentPermissionMap);

                            menuListPresenter.setData(menuItems);
                            final PopupPosition popupPosition = new PopupPosition(event.getX(), event.getY());
                            ShowPopupEvent.fire(EntityPluginEventManager.this, menuListPresenter, PopupType.POPUP,
                                    popupPosition, null);
                        })
                );
            }
        }));
    }

    private List<ExplorerData> getExplorerDataListWithPermission(final SharedMap<ExplorerData, ExplorerPermissions> documentPermissionMap, final String permission) {
        final List<ExplorerData> list = new ArrayList<>();
        for (final Map.Entry<ExplorerData, ExplorerPermissions> entry : documentPermissionMap.entrySet()) {
            if (entry.getValue().hasDocumentPermission(permission)) {
                list.add(entry.getKey());
            }
        }

        list.sort(Comparator.comparing(HasDisplayValue::getDisplayValue));
        return list;
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        super.onReveal(event);

        // Add menu bar item menu.
        event.getMenuItems().addMenuItem(MenuKeys.MAIN_MENU, new SimpleParentMenuItem(1, "Item", null) {
            @Override
            public Future<List<Item>> getChildren() {
                final FutureImpl<List<Item>> future = new FutureImpl<>();
                final List<ExplorerData> selectedItems = getSelectedItems();
                final boolean singleSelection = selectedItems.size() == 1;
                final ExplorerData primarySelection = getPrimarySelection();

                fetchPermissions(selectedItems).onSuccess(documentPermissionMap -> documentTypeCache.fetch().onSuccess(documentTypes -> {
                    final List<Item> menuItems = new ArrayList<>();

                    // Only allow the new menu to appear if we have a single selection.
                    addNewMenuItem(menuItems, singleSelection, documentPermissionMap, primarySelection, documentTypes);
                    menuItems.add(createCloseMenuItem(isTabItemSelected(selectedTab)));
                    menuItems.add(createCloseAllMenuItem(isTabItemSelected(selectedTab)));
                    menuItems.add(new Separator(5));
                    menuItems.add(createSaveMenuItem(6, isDirty(selectedTab)));
                    menuItems.add(createSaveAsMenuItem(7, isEntityTabData(selectedTab)));
                    menuItems.add(createSaveAllMenuItem(8, isTabItemSelected(selectedTab)));
                    menuItems.add(new Separator(9));
                    addModifyMenuItems(menuItems, singleSelection, documentPermissionMap);

                    future.setResult(menuItems);
                }));
                return future;
            }
        });
    }

    private Future<List<Item>> getNewMenuItems(final ExplorerData explorerData) {
        final FutureImpl<List<Item>> future = new FutureImpl<>();
        fetchPermissions(Collections.singletonList(explorerData))
                .onSuccess(documentPermissions -> documentTypeCache.fetch()
                        .onSuccess(documentTypes -> future.setResult(createNewMenuItems(explorerData, documentPermissions.get(explorerData), documentTypes))));
        return future;
    }

    private Future<SharedMap<ExplorerData, ExplorerPermissions>> fetchPermissions(final List<ExplorerData> explorerDataList) {
        final FetchExplorerPermissionsAction action = new FetchExplorerPermissionsAction(explorerDataList);
        return dispatcher.exec(action);
    }

//    private DocRef getDocRef(final ExplorerData explorerData) {
//        DocRef docRef = null;
//        if (explorerData != null && explorerData instanceof EntityData) {
//            final EntityData entityData = (EntityData) explorerData;
//            docRef = entityData.getDocRef();
//        }
//        return docRef;
//    }

    private void addNewMenuItem(final List<Item> menuItems, final boolean singleSelection, final SharedMap<ExplorerData, ExplorerPermissions> documentPermissionMap, final ExplorerData primarySelection, final DocumentTypes documentTypes) {
        // Only allow the new menu to appear if we have a single selection.
        if (singleSelection) {
            // Add 'New' menu item.
            final ExplorerPermissions documentPermissions = documentPermissionMap.get(primarySelection);
            final List<Item> children = createNewMenuItems(primarySelection, documentPermissions,
                    documentTypes);
            final boolean allowNew = children != null && children.size() > 0;

            if (allowNew) {
                final Item newItem = new SimpleParentMenuItem(1, GlyphIcons.NEW_ITEM, GlyphIcons.NEW_ITEM, "New",
                        null, true, null) {
                    @Override
                    public Future<List<Item>> getChildren() {
                        final FutureImpl<List<Item>> future = new FutureImpl<>();
                        future.setResult(children);
                        return future;
                    }
                };
                menuItems.add(newItem);
                menuItems.add(new Separator(2));
            }
        }
    }

    private List<Item> createNewMenuItems(final ExplorerData explorerData,
                                          final ExplorerPermissions documentPermissions, final DocumentTypes documentTypes) {
        final List<Item> children = new ArrayList<>();

        for (final DocumentType documentType : documentTypes.getAllTypes()) {
            if (documentPermissions.hasCreatePermission(documentType)) {
                final Item item = new IconMenuItem(documentType.getPriority(), ImageIcon.create(ImageUtil.getImageURL() + documentType.getIconUrl()), null,
                        documentType.getDisplayType(), null, true, () -> ShowCreateEntityDialogEvent.fire(EntityPluginEventManager.this,
                        explorerData, documentType.getType(), documentType.getDisplayType(), true));
                children.add(item);

                if (Folder.ENTITY_TYPE.equals(documentType.getType())) {
                    children.add(new Separator(documentType.getPriority()));
                }
            }
        }

        return children;
    }

    private void addModifyMenuItems(final List<Item> menuItems, final boolean singleSelection, final SharedMap<ExplorerData, ExplorerPermissions> documentPermissionMap) {
        final List<ExplorerData> readableItems = getExplorerDataListWithPermission(documentPermissionMap, DocumentPermissionNames.READ);
        final List<ExplorerData> updatableItems = getExplorerDataListWithPermission(documentPermissionMap, DocumentPermissionNames.UPDATE);
        final List<ExplorerData> deletableItems = getExplorerDataListWithPermission(documentPermissionMap, DocumentPermissionNames.DELETE);

        final boolean allowRead = readableItems.size() > 0;
        final boolean allowUpdate = updatableItems.size() > 0;
        final boolean allowDelete = deletableItems.size() > 0;

        menuItems.add(createCopyMenuItem(readableItems, 3, allowRead));
        menuItems.add(createMoveMenuItem(updatableItems, 4, allowUpdate));
        menuItems.add(createRenameMenuItem(updatableItems, 5, singleSelection && allowUpdate));
        menuItems.add(createDeleteMenuItem(deletableItems, 6, allowDelete));

        // Only allow users to change permissions if they have a single item selected.
        if (singleSelection) {
            final List<ExplorerData> ownedItems = getExplorerDataListWithPermission(documentPermissionMap, DocumentPermissionNames.OWNER);
            if (ownedItems.size() == 1 && ownedItems.get(0) instanceof EntityData) {
                menuItems.add(new Separator(7));
                menuItems.add(createPermissionsMenuItem(ownedItems.get(0), 8, true));
            }
        }
    }

    private MenuItem createCloseMenuItem(final boolean enabled) {
        final Command command = () -> {
            if (isTabItemSelected(selectedTab)) {
                RequestCloseTabEvent.fire(EntityPluginEventManager.this, selectedTab);
            }
        };

        keyboardInterceptor.addKeyTest(ALT_W, command);

        return new IconMenuItem(3, GlyphIcons.CLOSE, GlyphIcons.CLOSE, "Close", "Alt+W", enabled,
                command);
    }

    private MenuItem createCloseAllMenuItem(final boolean enabled) {
        final Command command = () -> {
            if (isTabItemSelected(selectedTab)) {
                RequestCloseAllTabsEvent.fire(EntityPluginEventManager.this);
            }
        };

        keyboardInterceptor.addKeyTest(ALT_SHIFT_W, command);

        return new IconMenuItem(4, GlyphIcons.CLOSE, GlyphIcons.CLOSE, "Close All",
                "Alt+Shift+W", enabled, command);
    }

    private MenuItem createSaveMenuItem(final int priority, final boolean enabled) {
        final Command command = () -> {
            if (isDirty(selectedTab)) {
                final EntityTabData entityTabData = (EntityTabData) selectedTab;
                SaveEntityEvent.fire(EntityPluginEventManager.this, entityTabData);
            }
        };

        keyboardInterceptor.addKeyTest(CTRL_S, command);

        return new IconMenuItem(priority, SVGIcons.SAVE, SVGIcons.SAVE, "Save", "Ctrl+S",
                enabled, command);
    }

    private MenuItem createSaveAsMenuItem(final int priority, final boolean enabled) {
        final Command command = () -> {
            if (isEntityTabData(selectedTab)) {
                final EntityTabData entityTabData = (EntityTabData) selectedTab;
                ShowSaveAsEntityDialogEvent.fire(EntityPluginEventManager.this, entityTabData);
            }
        };

        return new IconMenuItem(priority, SVGIcons.SAVE_AS, SVGIcons.SAVE_AS, "Save As", null,
                enabled, command);
    }

    private MenuItem createSaveAllMenuItem(final int priority, final boolean enabled) {
        final Command command = () -> {
            if (isTabItemSelected(selectedTab)) {
                for (final EntityPlugin<?> plugin : pluginMap.values()) {
                    plugin.saveAll();
                }
            }
        };

        keyboardInterceptor.addKeyTest(CTRL_SHIFT_S, command);

        return new IconMenuItem(priority, SVGIcons.SAVE, SVGIcons.SAVE, "Save All",
                "Ctrl+Shift+S", enabled, command);
    }

    private MenuItem createCopyMenuItem(final List<ExplorerData> explorerDataList, final int priority, final boolean enabled) {
        final Command command = () -> ShowCopyEntityDialogEvent.fire(EntityPluginEventManager.this, explorerDataList);

        return new IconMenuItem(priority, GlyphIcons.COPY, GlyphIcons.COPY, "Copy", null, enabled,
                command);
    }

    private MenuItem createMoveMenuItem(final List<ExplorerData> explorerDataList, final int priority, final boolean enabled) {
        final Command command = () -> ShowMoveEntityDialogEvent.fire(EntityPluginEventManager.this, explorerDataList);

        return new IconMenuItem(priority, GlyphIcons.MOVE, GlyphIcons.MOVE, "Move", null, enabled,
                command);
    }

    private MenuItem createRenameMenuItem(final List<ExplorerData> explorerDataList, final int priority, final boolean enabled) {
        final Command command = () -> ShowRenameEntityDialogEvent.fire(EntityPluginEventManager.this, explorerDataList);

        return new IconMenuItem(priority, GlyphIcons.EDIT, GlyphIcons.EDIT, "Rename", null,
                enabled, command);
    }

    private MenuItem createDeleteMenuItem(final List<ExplorerData> explorerDataList, final int priority, final boolean enabled) {
        final Command command = () -> deleteItems(explorerDataList);

        return new IconMenuItem(priority, GlyphIcons.DELETE, GlyphIcons.DELETE, "Delete", null,
                enabled, command);
    }

    private MenuItem createPermissionsMenuItem(final ExplorerData explorerData, final int priority, final boolean enabled) {
        final Command command = () -> {
            if (explorerData != null) {
                ShowPermissionsEntityDialogEvent.fire(EntityPluginEventManager.this, explorerData);
            }
        };

        return new IconMenuItem(priority, GlyphIcons.PERMISSIONS, GlyphIcons.PERMISSIONS, "Permissions", null,
                enabled, command);
    }

    private void deleteItems(final List<ExplorerData> explorerDataList) {
        if (explorerDataList != null) {
            for (final ExplorerData explorerData : explorerDataList) {
                if (explorerData instanceof EntityData) {
                    final EntityData entityData = (EntityData) explorerData;
                    final EntityPlugin<?> plugin = pluginMap.get(entityData.getType());
                    if (plugin != null) {
                        plugin.deleteEntity(entityData.getDocRef());
                    }
                }
            }
        }
    }

    void registerPlugin(final String entityType, final EntityPlugin<?> plugin) {
        pluginMap.put(entityType, plugin);
    }

    private boolean isTabItemSelected(final TabData tabData) {
        return tabData != null;
    }

    private boolean isEntityTabData(final TabData tabData) {
        if (isTabItemSelected(tabData)) {
            if (tabData instanceof EntityEditPresenter<?, ?>) {
                return true;
            }
        }

        return false;
    }

    private boolean isDirty(final TabData tabData) {
        if (isEntityTabData(tabData)) {
            final EntityEditPresenter<?, ?> editPresenter = (EntityEditPresenter<?, ?>) tabData;
            if (editPresenter.isDirty()) {
                return true;
            }
        }

        return false;
    }

    private List<ExplorerData> getSelectedItems() {
        if (selectionModel == null) {
            return Collections.emptyList();
        }

        return selectionModel.getSelectedItems();
    }

    private ExplorerData getPrimarySelection() {
        if (selectionModel == null) {
            return null;
        }

        return selectionModel.getSelected();
    }
}
