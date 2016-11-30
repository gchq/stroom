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

package stroom.entity.client;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.content.client.event.ContentTabSelectionChangeEvent.ContentTabSelectionChangeHandler;
import stroom.core.client.KeyboardInterceptor;
import stroom.core.client.KeyboardInterceptor.KeyTest;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.dispatch.client.AsyncCallbackAdaptor;
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
import stroom.widget.button.client.GlyphIcons;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityPluginEventManager extends Plugin {
    private static final KeyTest CTRL_S = new KeyTest() {
        @Override
        public boolean match(final NativeEvent event) {
            return event.getCtrlKey() && !event.getShiftKey() && event.getKeyCode() == 'S';
        }
    };
    private static final KeyTest CTRL_SHIFT_S = new KeyTest() {
        @Override
        public boolean match(final NativeEvent event) {
            return event.getCtrlKey() && event.getShiftKey() && event.getKeyCode() == 'S';
        }
    };
    private static final KeyTest ALT_W = new KeyTest() {
        @Override
        public boolean match(final NativeEvent event) {
            return event.getAltKey() && !event.getShiftKey() && event.getKeyCode() == 'W';
        }
    };
    private static final KeyTest ALT_SHIFT_W = new KeyTest() {
        @Override
        public boolean match(final NativeEvent event) {
            return event.getAltKey() && event.getShiftKey() && event.getKeyCode() == 'W';
        }
    };

    private final ClientDispatchAsync dispatcher;
    private final DocumentTypeCache documentTypeCache;
    private final MenuListPresenter menuListPresenter;
    private final ClientSecurityContext clientSecurityContext;
    private final Map<String, EntityPlugin<?>> pluginMap = new HashMap<>();
    private final KeyboardInterceptor keyboardInterceptor;
    private TabData selectedTab;
    private ExplorerData selectedExplorerItem;

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
                new ContentTabSelectionChangeHandler() {
                    @Override
                    public void onTabSelectionChange(final ContentTabSelectionChangeEvent event) {
                        selectedTab = event.getTabData();
                    }
                }));

        // 1. Handle entity creation events.
        registerHandler(getEventBus().addHandler(CreateEntityEvent.getType(), new CreateEntityEvent.Handler() {
            @Override
            public void onCreate(final CreateEntityEvent event) {
                final EntityPlugin<?> plugin = pluginMap.get(event.getEntityType());
                if (plugin != null) {
                    plugin.createEntity(event.getPresenter(), event.getFolder(), event.getEntityName());
                }
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
                getEventBus().addHandler(ExplorerTreeSelectEvent.getType(), new ExplorerTreeSelectEvent.Handler() {
                    @Override
                    public void onSelect(final ExplorerTreeSelectEvent event) {
                        // Remember the selected item.
                        selectedExplorerItem = event.getSelectedItem();

                        if (event.getSelectedItem() != null && !event.isRightClick()
                                && event.getSelectedItem() instanceof EntityData) {
                            final EntityData entityData = (EntityData) event.getSelectedItem();
                            final EntityPlugin<?> plugin = pluginMap.get(entityData.getType());
                            if (plugin != null) {
                                plugin.open(entityData.getDocRef(), event.isDoubleSelect());
                            }
                        }
                    }
                }));

        // 5. Handle save events.
        registerHandler(getEventBus().addHandler(SaveEntityEvent.getType(), new SaveEntityEvent.Handler() {
            @Override
            public void onSave(final SaveEntityEvent event) {
                if (isDirty(event.getTabData())) {
                    final EntityTabData entityTabData = event.getTabData();
                    final EntityPlugin<?> plugin = pluginMap.get(entityTabData.getType());
                    if (plugin != null) {
                        plugin.save(entityTabData);
                    }
                }
            }
        }));

        // 6. Handle save as events.
        registerHandler(getEventBus().addHandler(SaveAsEntityEvent.getType(), new SaveAsEntityEvent.Handler() {
            @Override
            public void onSaveAs(final SaveAsEntityEvent event) {
                final EntityPlugin<?> plugin = pluginMap.get(event.getTabData().getType());
                if (plugin != null) {
                    plugin.copy(event.getDialog(), event.getTabData(), event.getEntityName());
                }
            }
        }));

        // 7. Save all entities - handled directly.

        // 8.1. Handle entity copy events.
        registerHandler(getEventBus().addHandler(CopyEntityEvent.getType(), new CopyEntityEvent.Handler() {
            @Override
            public void onCopy(final CopyEntityEvent event) {
                final EntityPlugin<?> plugin = pluginMap.get(event.getDocument().getType());
                if (plugin != null) {
                    plugin.copyEntity(event.getPresenter(), event.getDocument(), event.getFolder(),
                            event.getName());
                }
            }
        }));

        // 8.2. Handle entity move events.
        registerHandler(getEventBus().addHandler(MoveEntityEvent.getType(), new MoveEntityEvent.Handler() {
            @Override
            public void onMove(final MoveEntityEvent event) {
                final EntityPlugin<?> plugin = pluginMap.get(event.getDocument().getType());
                if (plugin != null) {
                    plugin.moveEntity(event.getPresenter(), event.getDocument(), event.getFolder());
                }
            }
        }));

        // 9. Handle entity rename events.
        registerHandler(getEventBus().addHandler(RenameEntityEvent.getType(), new RenameEntityEvent.Handler() {
            @Override
            public void onRename(final RenameEntityEvent event) {
                final EntityPlugin<?> plugin = pluginMap.get(event.getDocument().getType());
                if (plugin != null) {
                    plugin.renameEntity(event.getDialog(), event.getDocument(), event.getEntityName());
                }
            }
        }));

        // 10. Handle entity delete events.
        registerHandler(
                getEventBus().addHandler(ExplorerTreeDeleteEvent.getType(), new ExplorerTreeDeleteEvent.Handler() {
                    @Override
                    public void onDelete(final ExplorerTreeDeleteEvent event) {
                        deleteItem(getSelectedEntityData());
                    }
                }));

        // 11. Handle entity reload events.
        registerHandler(getEventBus().addHandler(ReloadEntityEvent.getType(), new ReloadEntityEvent.Handler() {
            @Override
            public void onReload(final ReloadEntityEvent event) {
                final EntityPlugin<?> plugin = pluginMap.get(event.getEntity().getType());
                if (plugin != null) {
                    plugin.reload(DocRef.create(event.getEntity()));
                }
            }
        }));

        // Not handled as it is done directly.

        registerHandler(getEventBus().addHandler(ShowNewMenuEvent.getType(), new ShowNewMenuEvent.Handler() {
            @Override
            public void onShow(final ShowNewMenuEvent event) {
                getNewMenuItems(selectedExplorerItem, new AsyncCallbackAdaptor<List<Item>>() {
                    @Override
                    public void onSuccess(final List<Item> children) {
                        menuListPresenter.setData(children);

                        final PopupPosition popupPosition = new PopupPosition(event.getX(), event.getY());
                        ShowPopupEvent.fire(EntityPluginEventManager.this, menuListPresenter, PopupType.POPUP,
                                popupPosition, null, event.getElement());
                    }
                });
            }
        }));
        registerHandler(getEventBus().addHandler(ShowExplorerMenuEvent.getType(), new ShowExplorerMenuEvent.Handler() {
            @Override
            public void onShow(final ShowExplorerMenuEvent event) {
                final ExplorerData explorerData = event.getExplorerData();
                final boolean doc = getDocRef(explorerData) != null;

                fetchPermissions(explorerData, new AsyncCallbackAdaptor<ExplorerPermissions>() {
                    @Override
                    public void onSuccess(final ExplorerPermissions documentPermissions) {
                        documentTypeCache.fetch(new AsyncCallbackAdaptor<DocumentTypes>() {
                            @Override
                            public void onSuccess(final DocumentTypes documentTypes) {
                                final boolean allowRead = doc && documentPermissions
                                        .hasDocumentPermission(DocumentPermissionNames.READ);
                                final boolean allowUpdate = doc && documentPermissions
                                        .hasDocumentPermission(DocumentPermissionNames.UPDATE);
                                final boolean allowDelete = doc && documentPermissions
                                        .hasDocumentPermission(DocumentPermissionNames.DELETE);
                                final boolean owner = doc && documentPermissions
                                        .hasDocumentPermission(DocumentPermissionNames.OWNER);

                                final List<Item> menuItems = new ArrayList<Item>();

                                // Add 'New' menu item.
                                final List<Item> children = createNewMenuItems(explorerData, documentPermissions,
                                        documentTypes);
                                final boolean allowNew = children != null && children.size() > 0;

                                if (allowNew) {
                                    final Item newItem = new SimpleParentMenuItem(1, GlyphIcons.NEW_ITEM, GlyphIcons.NEW_ITEM, "New",
                                            null, children != null && children.size() > 0, null) {
                                        @Override
                                        public void getChildren(final AsyncCallback<List<Item>> callback) {
                                            callback.onSuccess(children);
                                        }
                                    };
                                    menuItems.add(newItem);
                                    menuItems.add(new Separator(2));
                                }

                                menuItems.add(createCopyMenuItem(3, allowRead));
                                menuItems.add(createMoveMenuItem(4, allowUpdate));
                                menuItems.add(createRenameMenuItem(5, allowUpdate));
                                menuItems.add(createDeleteMenuItem(6, allowDelete));

                                if (owner) {
                                    menuItems.add(new Separator(7));
                                    menuItems.add(createPermissionsMenuItem(8, owner));
                                }

                                menuListPresenter.setData(menuItems);
                                final PopupPosition popupPosition = new PopupPosition(event.getX(), event.getY());
                                ShowPopupEvent.fire(EntityPluginEventManager.this, menuListPresenter, PopupType.POPUP,
                                        popupPosition, null);
                            }
                        });
                    }
                });
            }
        }));
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        super.onReveal(event);

        // Add menu bar item menu.
        event.getMenuItems().addMenuItem(MenuKeys.MAIN_MENU, new SimpleParentMenuItem(1, "Item", null) {
            @Override
            public void getChildren(final AsyncCallback<List<Item>> callback) {
                final ExplorerData explorerData = selectedExplorerItem;
                final boolean doc = getDocRef(explorerData) != null;

                fetchPermissions(explorerData, new AsyncCallbackAdaptor<ExplorerPermissions>() {
                    @Override
                    public void onSuccess(final ExplorerPermissions documentPermissions) {
                        documentTypeCache.fetch(new AsyncCallbackAdaptor<DocumentTypes>() {
                            @Override
                            public void onSuccess(final DocumentTypes documentTypes) {
                                final boolean allowRead = doc && documentPermissions
                                        .hasDocumentPermission(DocumentPermissionNames.READ);
                                final boolean allowUpdate = doc && documentPermissions
                                        .hasDocumentPermission(DocumentPermissionNames.UPDATE);
                                final boolean allowDelete = doc && documentPermissions
                                        .hasDocumentPermission(DocumentPermissionNames.DELETE);
                                final boolean owner = doc && documentPermissions
                                        .hasDocumentPermission(DocumentPermissionNames.OWNER);

                                final List<Item> menuItems = new ArrayList<Item>();

                                // Add 'New' menu item.
                                final List<Item> children = createNewMenuItems(explorerData, documentPermissions,
                                        documentTypes);
                                final boolean allowNew = children != null && children.size() > 0;

                                if (allowNew) {
                                    final Item newItem = new SimpleParentMenuItem(1, GlyphIcons.NEW_ITEM, GlyphIcons.NEW_ITEM, "New",
                                            null, children != null && children.size() > 0, null) {
                                        @Override
                                        public void getChildren(final AsyncCallback<List<Item>> callback) {
                                            callback.onSuccess(children);
                                        }
                                    };
                                    menuItems.add(newItem);
                                    menuItems.add(new Separator(2));
                                }

                                menuItems.add(createCloseMenuItem(isTabItemSelected(selectedTab)));
                                menuItems.add(createCloseAllMenuItem(isTabItemSelected(selectedTab)));
                                menuItems.add(new Separator(5));
                                menuItems.add(createSaveMenuItem(6, isDirty(selectedTab)));
                                menuItems.add(createSaveAsMenuItem(7, isEntityTabData(selectedTab)));
                                menuItems.add(createSaveAllMenuItem(8, isTabItemSelected(selectedTab)));
                                menuItems.add(new Separator(9));
                                menuItems.add(createCopyMenuItem(10, allowRead));
                                menuItems.add(createMoveMenuItem(11, allowUpdate));
                                menuItems.add(createRenameMenuItem(12, allowUpdate));
                                menuItems.add(createDeleteMenuItem(13, allowDelete));

                                if (owner) {
                                    menuItems.add(new Separator(14));
                                    menuItems.add(createPermissionsMenuItem(15, owner));
                                }

                                callback.onSuccess(menuItems);
                            }
                        });
                    }
                });
            }
        });
    }

    private void getNewMenuItems(final ExplorerData explorerData, final AsyncCallback<List<Item>> callback) {
        fetchPermissions(explorerData, new AsyncCallbackAdaptor<ExplorerPermissions>() {
            @Override
            public void onSuccess(final ExplorerPermissions documentPermissions) {
                documentTypeCache.fetch(new AsyncCallbackAdaptor<DocumentTypes>() {
                    @Override
                    public void onSuccess(final DocumentTypes documentTypes) {
                        callback.onSuccess(createNewMenuItems(explorerData, documentPermissions, documentTypes));
                    }
                });
            }
        });
    }

    private void fetchPermissions(final ExplorerData explorerData,
                                  final AsyncCallbackAdaptor<ExplorerPermissions> callback) {
        final DocRef docRef = getDocRef(explorerData);
        final FetchExplorerPermissionsAction action = new FetchExplorerPermissionsAction(docRef);
        dispatcher.execute(action, callback);
    }

    private DocRef getDocRef(final ExplorerData explorerData) {
        DocRef docRef = null;
        if (explorerData != null && explorerData instanceof EntityData) {
            final EntityData entityData = (EntityData) explorerData;
            docRef = entityData.getDocRef();
        }
        return docRef;
    }

    private List<Item> createNewMenuItems(final ExplorerData explorerData,
                                          final ExplorerPermissions documentPermissions, final DocumentTypes documentTypes) {
        final List<Item> children = new ArrayList<Item>();

        for (final DocumentType documentType : documentTypes.getAllTypes()) {
            if (documentPermissions.hasCreatePermission(documentType)) {
                final Item item = new IconMenuItem(documentType.getPriority(), ImageIcon.create(ImageUtil.getImageURL() + documentType.getIconUrl()), null,
                        documentType.getDisplayType(), null, true, new Command() {
                    @Override
                    public void execute() {
                        ShowCreateEntityDialogEvent.fire(EntityPluginEventManager.this,
                                explorerData, documentType.getType(), documentType.getDisplayType(), true);
                    }
                });
                children.add(item);

                if (Folder.ENTITY_TYPE.equals(documentType.getType())) {
                    children.add(new Separator(documentType.getPriority()));
                }
            }
        }

        return children;
    }

    private MenuItem createCloseMenuItem(final boolean enabled) {
        final Command command = new Command() {
            @Override
            public void execute() {
                if (isTabItemSelected(selectedTab)) {
                    RequestCloseTabEvent.fire(EntityPluginEventManager.this, selectedTab);
                }
            }
        };

        keyboardInterceptor.addKeyTest(ALT_W, command);

        return new IconMenuItem(3, GlyphIcons.CLOSE, GlyphIcons.CLOSE, "Close", "Alt+W", enabled,
                command);
    }

    private MenuItem createCloseAllMenuItem(final boolean enabled) {
        final Command command = new Command() {
            @Override
            public void execute() {
                if (isTabItemSelected(selectedTab)) {
                    RequestCloseAllTabsEvent.fire(EntityPluginEventManager.this);
                }
            }
        };

        keyboardInterceptor.addKeyTest(ALT_SHIFT_W, command);

        return new IconMenuItem(4, GlyphIcons.CLOSE, GlyphIcons.CLOSE, "Close All",
                "Alt+Shift+W", enabled, command);
    }

    private MenuItem createSaveMenuItem(final int priority, final boolean enabled) {
        final Command command = new Command() {
            @Override
            public void execute() {
                if (isDirty(selectedTab)) {
                    final EntityTabData entityTabData = (EntityTabData) selectedTab;
                    SaveEntityEvent.fire(EntityPluginEventManager.this, entityTabData);
                }
            }
        };

        keyboardInterceptor.addKeyTest(CTRL_S, command);

        return new IconMenuItem(priority, GlyphIcons.SAVE, GlyphIcons.SAVE, "Save", "Ctrl+S",
                enabled, command);
    }

    private MenuItem createSaveAsMenuItem(final int priority, final boolean enabled) {
        final Command command = new Command() {
            @Override
            public void execute() {
                if (isEntityTabData(selectedTab)) {
                    final EntityTabData entityTabData = (EntityTabData) selectedTab;
                    ShowSaveAsEntityDialogEvent.fire(EntityPluginEventManager.this, entityTabData);
                }
            }
        };

        return new IconMenuItem(priority, GlyphIcons.SAVE_AS, GlyphIcons.SAVE_AS, "Save As", null,
                enabled, command);
    }

    private MenuItem createSaveAllMenuItem(final int priority, final boolean enabled) {
        final Command command = new Command() {
            @Override
            public void execute() {
                if (isTabItemSelected(selectedTab)) {
                    for (final EntityPlugin<?> plugin : pluginMap.values()) {
                        plugin.saveAll();
                    }
                }
            }
        };

        keyboardInterceptor.addKeyTest(CTRL_SHIFT_S, command);

        return new IconMenuItem(priority, GlyphIcons.SAVE, GlyphIcons.SAVE, "Save All",
                "Ctrl+Shift+S", enabled, command);
    }

    private MenuItem createCopyMenuItem(final int priority, final boolean enabled) {
        final Command command = new Command() {
            @Override
            public void execute() {
                final EntityData entityData = getSelectedEntityData();
                if (entityData != null) {
                    ShowCopyEntityDialogEvent.fire(EntityPluginEventManager.this, entityData);
                }
            }
        };

        return new IconMenuItem(priority, GlyphIcons.COPY, GlyphIcons.COPY, "Copy", null, enabled,
                command);
    }

    private MenuItem createMoveMenuItem(final int priority, final boolean enabled) {
        final Command command = new Command() {
            @Override
            public void execute() {
                final EntityData entityData = getSelectedEntityData();
                if (entityData != null) {
                    ShowMoveEntityDialogEvent.fire(EntityPluginEventManager.this, entityData);
                }
            }
        };

        return new IconMenuItem(priority, GlyphIcons.MOVE, GlyphIcons.MOVE, "Move", null, enabled,
                command);
    }

    private MenuItem createRenameMenuItem(final int priority, final boolean enabled) {
        final Command command = new Command() {
            @Override
            public void execute() {
                final EntityData entityData = getSelectedEntityData();
                if (entityData != null) {
                    ShowRenameEntityDialogEvent.fire(EntityPluginEventManager.this, entityData);
                }
            }
        };

        return new IconMenuItem(priority, GlyphIcons.EDIT, GlyphIcons.EDIT, "Rename", null,
                enabled, command);
    }

    private MenuItem createDeleteMenuItem(final int priority, final boolean enabled) {
        final Command command = new Command() {
            @Override
            public void execute() {
                final EntityData entityData = getSelectedEntityData();
                deleteItem(entityData);
            }
        };

        return new IconMenuItem(priority, GlyphIcons.DELETE, GlyphIcons.DELETE, "Delete", null,
                enabled, command);
    }

    private MenuItem createPermissionsMenuItem(final int priority, final boolean enabled) {
        final Command command = new Command() {
            @Override
            public void execute() {
                final EntityData entityData = getSelectedEntityData();
                if (entityData != null) {
                    ShowPermissionsEntityDialogEvent.fire(EntityPluginEventManager.this, entityData);
                }
            }
        };

        return new IconMenuItem(priority, GlyphIcons.PERMISSIONS, GlyphIcons.PERMISSIONS, "Permissions", null,
                enabled, command);
    }

    private void deleteItem(final EntityData entityData) {
        if (entityData != null) {
            final EntityPlugin<?> plugin = pluginMap.get(entityData.getType());
            if (plugin != null) {
                plugin.deleteEntity(entityData.getDocRef());
            }
        }
    }

    public void registerPlugin(final String entityType, final EntityPlugin<?> plugin) {
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

    public EntityData getSelectedEntityData() {
        if (selectedExplorerItem != null && selectedExplorerItem instanceof EntityData) {
            return (EntityData) selectedExplorerItem;
        }
        return null;
    }
}
