/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.document.client;

import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.core.client.KeyboardInterceptor;
import stroom.core.client.KeyboardInterceptor.KeyTest;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.event.CopyDocumentEvent;
import stroom.document.client.event.CreateDocumentEvent;
import stroom.document.client.event.MoveDocumentEvent;
import stroom.document.client.event.RefreshDocumentEvent;
import stroom.document.client.event.RenameDocumentEvent;
import stroom.document.client.event.ShowCopyDocumentDialogEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.document.client.event.ShowInfoDocumentDialogEvent;
import stroom.document.client.event.ShowMoveDocumentDialogEvent;
import stroom.document.client.event.ShowPermissionsDialogEvent;
import stroom.document.client.event.ShowRenameDocumentDialogEvent;
import stroom.document.client.event.WriteDocumentEvent;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.shared.PermissionInheritance;
import stroom.entity.shared.SharedDocRef;
import stroom.explorer.client.event.ExplorerTreeDeleteEvent;
import stroom.explorer.client.event.ExplorerTreeSelectEvent;
import stroom.explorer.client.event.HighlightExplorerNodeEvent;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.explorer.client.event.ShowExplorerMenuEvent;
import stroom.explorer.client.event.ShowNewMenuEvent;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerPermissions;
import stroom.explorer.shared.ExplorerServiceCopyAction;
import stroom.explorer.shared.ExplorerServiceCreateAction;
import stroom.explorer.shared.ExplorerServiceDeleteAction;
import stroom.explorer.shared.ExplorerServiceInfoAction;
import stroom.explorer.shared.ExplorerServiceMoveAction;
import stroom.explorer.shared.ExplorerServiceRenameAction;
import stroom.explorer.shared.FetchExplorerPermissionsAction;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.query.api.v2.DocRef;
import stroom.security.shared.DocumentPermissionNames;
import stroom.svg.client.SvgIcon;
import stroom.svg.client.SvgPresets;
import stroom.util.client.ImageUtil;
import stroom.util.shared.HasDisplayValue;
import stroom.util.shared.SharedMap;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItem;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.menu.client.presenter.Separator;
import stroom.widget.menu.client.presenter.SimpleParentMenuItem;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.event.RequestCloseAllTabsEvent;
import stroom.widget.tab.client.event.RequestCloseTabEvent;
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

public class DocumentPluginEventManager extends Plugin {
    private static final KeyTest CTRL_S = event -> event.getCtrlKey() && !event.getShiftKey() && event.getKeyCode() == 'S';
    private static final KeyTest CTRL_SHIFT_S = event -> event.getCtrlKey() && event.getShiftKey() && event.getKeyCode() == 'S';
    private static final KeyTest ALT_W = event -> event.getAltKey() && !event.getShiftKey() && event.getKeyCode() == 'W';
    private static final KeyTest ALT_SHIFT_W = event -> event.getAltKey() && event.getShiftKey() && event.getKeyCode() == 'W';

    private final ClientDispatchAsync dispatcher;
    private final DocumentTypeCache documentTypeCache;
    private final MenuListPresenter menuListPresenter;
    private final Map<String, DocumentPlugin<?>> pluginMap = new HashMap<>();
    private final KeyboardInterceptor keyboardInterceptor;
    private TabData selectedTab;
    private MultiSelectionModel<ExplorerNode> selectionModel;

    @Inject
    public DocumentPluginEventManager(final EventBus eventBus,
                                      final KeyboardInterceptor keyboardInterceptor,
                                      final ClientDispatchAsync dispatcher,
                                      final DocumentTypeCache documentTypeCache,
                                      final MenuListPresenter menuListPresenter) {
        super(eventBus);
        this.keyboardInterceptor = keyboardInterceptor;
        this.dispatcher = dispatcher;
        this.documentTypeCache = documentTypeCache;
        this.menuListPresenter = menuListPresenter;
    }

    @Override
    protected void onBind() {
        super.onBind();

        // track the currently selected content tab.
        registerHandler(getEventBus().addHandler(ContentTabSelectionChangeEvent.getType(),
                event -> selectedTab = event.getTabData()));


        // // 2. Handle requests to close tabs.
        // registerHandler(getEventBus().addHandler(
        // RequestCloseTabEvent.getType(), new RequestCloseTabHandler() {
        // @Override
        // public void onCloseTab(final RequestCloseTabEvent event) {
        // final TabData tabData = event.getTabData();
        // if (tabData instanceof EntityTabData) {
        // final EntityTabData entityTabData = (EntityTabData) tabData;
        // final DocumentPlugin<?> plugin = pluginMap
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
        // for (final DocumentPlugin<?> plugin : pluginMap.values()) {
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
                        final ExplorerNode explorerNode = event.getSelectionModel().getSelected();
                        if (explorerNode != null) {
                            final DocumentPlugin<?> plugin = pluginMap.get(explorerNode.getType());
                            if (plugin != null) {
                                plugin.open(explorerNode.getDocRef(), event.getSelectionType().isDoubleSelect());
                            }
                        }
                    }
                }));

        // 11. Handle entity reload events.
        registerHandler(getEventBus().addHandler(RefreshDocumentEvent.getType(), event -> {
            final DocumentPlugin<?> plugin = pluginMap.get(event.getDocRef().getType());
            if (plugin != null) {
                plugin.reload(event.getDocRef());
            }
        }));

        // 5. Handle save events.
        registerHandler(getEventBus().addHandler(WriteDocumentEvent.getType(), event -> {
            if (isDirty(event.getTabData())) {
                final DocumentTabData entityTabData = event.getTabData();
                final DocumentPlugin<?> plugin = pluginMap.get(entityTabData.getType());
                if (plugin != null) {
                    plugin.save(entityTabData);
                }
            }
        }));


        //////////////////////////////
        // START EXPLORER EVENTS
        ///////////////////////////////

        // 1. Handle entity creation events.
        registerHandler(getEventBus().addHandler(CreateDocumentEvent.getType(), event -> {
            create(event.getDocType(), event.getDocName(), event.getDestinationFolderRef(), event.getPermissionInheritance()).onSuccess(docRef -> {
                // Hide the create document presenter.
                HidePopupEvent.fire(DocumentPluginEventManager.this, event.getPresenter());

                highlight(docRef);

                // Open the document in the content pane.
                final DocumentPlugin<?> plugin = pluginMap.get(docRef.getType());
                if (plugin != null) {
                    plugin.open(docRef, true);
                }
            });
        }));

        // 8.1. Handle entity copy events.
        registerHandler(getEventBus().addHandler(CopyDocumentEvent.getType(), event -> {
            copy(event.getDocRefs(), event.getDestinationFolderRef(), event.getPermissionInheritance()).onSuccess(result -> {
                // Hide the copy document presenter.
                HidePopupEvent.fire(DocumentPluginEventManager.this, event.getPresenter());

                if (result.getMessage().length() > 0) {
                    AlertEvent.fireInfo(DocumentPluginEventManager.this, "Unable to copy some items", result.getMessage(), null);
                }

                if (result.getDocRefs().size() > 0) {
                    highlight(result.getDocRefs().get(0));
                }
            });
        }));

        // 8.2. Handle entity move events.
        registerHandler(getEventBus().addHandler(MoveDocumentEvent.getType(), event -> {
            move(event.getDocRefs(), event.getDestinationFolderRef(), event.getPermissionInheritance()).onSuccess(result -> {
                // Hide the move document presenter.
                HidePopupEvent.fire(DocumentPluginEventManager.this, event.getPresenter());

                if (result.getMessage().length() > 0) {
                    AlertEvent.fireInfo(DocumentPluginEventManager.this, "Unable to move some items", result.getMessage(), null);
                }

                if (result.getDocRefs().size() > 0) {
                    highlight(result.getDocRefs().get(0));
                }
            });
        }));

        // 9. Handle entity rename events.
        registerHandler(getEventBus().addHandler(RenameDocumentEvent.getType(), event -> {
            // Hide the rename document presenter.
            HidePopupEvent.fire(DocumentPluginEventManager.this, event.getPresenter());

            rename(event.getDocRef(), event.getDocName()).onSuccess(this::highlight);
        }));

        // 10. Handle entity delete events.
        registerHandler(getEventBus().addHandler(ExplorerTreeDeleteEvent.getType(), event -> {
            if (getSelectedItems().size() > 0) {
                fetchPermissions(getSelectedItems()).onSuccess(documentPermissionMap -> documentTypeCache.fetch().onSuccess(documentTypes -> {
                    final List<ExplorerNode> deletableItems = getExplorerNodeListWithPermission(documentPermissionMap, DocumentPermissionNames.DELETE);
                    if (deletableItems.size() > 0) {
                        deleteItems(deletableItems);
                    }
                }));
            }
        }));

//////////////////////////////
        // END EXPLORER EVENTS
        ///////////////////////////////


        // Not handled as it is done directly.

        registerHandler(getEventBus().addHandler(ShowNewMenuEvent.getType(), event -> {
            if (getSelectedItems().size() == 1) {
                final ExplorerNode primarySelection = getPrimarySelection();
                getNewMenuItems(primarySelection).onSuccess(children -> {
                    menuListPresenter.setData(children);

                    final PopupPosition popupPosition = new PopupPosition(event.getX(), event.getY());
                    ShowPopupEvent.fire(DocumentPluginEventManager.this, menuListPresenter, PopupType.POPUP,
                            popupPosition, null, event.getElement());
                });
            }
        }));
        registerHandler(getEventBus().addHandler(ShowExplorerMenuEvent.getType(), event -> {
            final List<ExplorerNode> selectedItems = getSelectedItems();
            final boolean singleSelection = selectedItems.size() == 1;
            final ExplorerNode primarySelection = getPrimarySelection();

            if (selectedItems.size() > 0) {
                fetchPermissions(selectedItems).onSuccess(documentPermissionMap ->
                        documentTypeCache.fetch().onSuccess(documentTypes -> {
                            final List<Item> menuItems = new ArrayList<>();

                            // Only allow the new menu to appear if we have a single selection.
                            addNewMenuItem(menuItems, singleSelection, documentPermissionMap, primarySelection, documentTypes);
                            addModifyMenuItems(menuItems, singleSelection, documentPermissionMap);

                            menuListPresenter.setData(menuItems);
                            final PopupPosition popupPosition = new PopupPosition(event.getX(), event.getY());
                            ShowPopupEvent.fire(DocumentPluginEventManager.this, menuListPresenter, PopupType.POPUP,
                                    popupPosition, null);
                        })
                );
            }
        }));
    }


    private void deleteItems(final List<ExplorerNode> explorerNodeList) {
        if (explorerNodeList != null) {
            final List<DocRef> docRefs = new ArrayList<>();
            for (final ExplorerNode explorerNode : explorerNodeList) {
                docRefs.add(explorerNode.getDocRef());
            }

            if (docRefs.size() > 0) {
                delete(docRefs).onSuccess(result -> {
                    if (result.getMessage().length() > 0) {
                        AlertEvent.fireInfo(DocumentPluginEventManager.this, "Unable to delete some items", result.getMessage(), null);
                    }

                    // Refresh the explorer tree so the documents are marked as deleted.
                    RefreshExplorerTreeEvent.fire(DocumentPluginEventManager.this);
                });
            }
        }
    }

//    private void deleteDocument(final DocRef document, final DocumentTabData tabData) {
//        delete(document).onSuccess(e -> {
//            if (tabData != null) {
//                // Cleanup reference to this tab data.
//                removeTabData(tabData);
//                contentManager.forceClose(tabData);
//            }
//            // Refresh the explorer tree so the document is marked as deleted.
//            RefreshExplorerTreeEvent.fire(DocumentPlugin.this);
//        });
//    }


//    /**
//     * 8.1. This method will copy documents.
//     */
//    void copyDocument(final PresenterWidget<?> popup, final DocRef document, final DocRef folder,
//                      final PermissionInheritance permissionInheritance) {
//        copy(document, folder, permissionInheritance).onSuccess(newDocRef -> {
//            // Hide the copy document presenter.
//            HidePopupEvent.fire(DocumentPluginEventManager.this, popup);
//
//            // Select it in the explorer tree.
//            highlight(newDocRef);
//        });
//    }


    public Future<SharedDocRef> create(final String docType, final String docName, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        return dispatcher.exec(new ExplorerServiceCreateAction(docType, docName, destinationFolderRef, permissionInheritance));
    }

    private Future<BulkActionResult> copy(final List<DocRef> docRefs, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        return dispatcher.exec(new ExplorerServiceCopyAction(docRefs, destinationFolderRef, permissionInheritance));
    }

    private Future<BulkActionResult> move(final List<DocRef> docRefs, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        return dispatcher.exec(new ExplorerServiceMoveAction(docRefs, destinationFolderRef, permissionInheritance));
    }

    private Future<SharedDocRef> rename(final DocRef docRef, final String docName) {
        return dispatcher.exec(new ExplorerServiceRenameAction(docRef, docName));
    }

    private Future<BulkActionResult> delete(final List<DocRef> docRefs) {
        return dispatcher.exec(new ExplorerServiceDeleteAction(docRefs));
    }

    /**
     * This method will highlight the supplied document item in the explorer tree.
     */
    public void highlight(final DocRef docRef) {
        // Open up parent items.
        final ExplorerNode documentData = ExplorerNode.create(docRef);
        HighlightExplorerNodeEvent.fire(DocumentPluginEventManager.this, documentData);
    }


    private List<ExplorerNode> getExplorerNodeListWithPermission(final SharedMap<ExplorerNode, ExplorerPermissions> documentPermissionMap, final String permission) {
        final List<ExplorerNode> list = new ArrayList<>();
        for (final Map.Entry<ExplorerNode, ExplorerPermissions> entry : documentPermissionMap.entrySet()) {
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
                final List<ExplorerNode> selectedItems = getSelectedItems();
                final boolean singleSelection = selectedItems.size() == 1;
                final ExplorerNode primarySelection = getPrimarySelection();

                fetchPermissions(selectedItems).onSuccess(documentPermissionMap -> documentTypeCache.fetch().onSuccess(documentTypes -> {
                    final List<Item> menuItems = new ArrayList<>();

                    // Only allow the new menu to appear if we have a single selection.
                    addNewMenuItem(menuItems, singleSelection, documentPermissionMap, primarySelection, documentTypes);
                    menuItems.add(createCloseMenuItem(isTabItemSelected(selectedTab)));
                    menuItems.add(createCloseAllMenuItem(isTabItemSelected(selectedTab)));
                    menuItems.add(new Separator(5));
                    menuItems.add(createSaveMenuItem(6, isDirty(selectedTab)));
                    menuItems.add(createSaveAllMenuItem(8, isTabItemSelected(selectedTab)));
                    menuItems.add(new Separator(9));
                    addModifyMenuItems(menuItems, singleSelection, documentPermissionMap);

                    future.setResult(menuItems);
                }));
                return future;
            }
        });
    }

    private Future<List<Item>> getNewMenuItems(final ExplorerNode explorerNode) {
        final FutureImpl<List<Item>> future = new FutureImpl<>();
        fetchPermissions(Collections.singletonList(explorerNode))
                .onSuccess(documentPermissions -> documentTypeCache.fetch()
                        .onSuccess(documentTypes -> future.setResult(createNewMenuItems(explorerNode, documentPermissions.get(explorerNode), documentTypes))));
        return future;
    }

    private Future<SharedMap<ExplorerNode, ExplorerPermissions>> fetchPermissions(final List<ExplorerNode> explorerNodeList) {
        final FetchExplorerPermissionsAction action = new FetchExplorerPermissionsAction(explorerNodeList);
        return dispatcher.exec(action);
    }

//    private DocRef getDocRef(final ExplorerNode explorerNode) {
//        DocRef docRef = null;
//        if (explorerNode != null && explorerNode instanceof EntityData) {
//            final EntityData entityData = (EntityData) explorerNode;
//            docRef = entityData.getDocRef();
//        }
//        return docRef;
//    }

    private void addNewMenuItem(final List<Item> menuItems, final boolean singleSelection, final SharedMap<ExplorerNode, ExplorerPermissions> documentPermissionMap, final ExplorerNode primarySelection, final DocumentTypes documentTypes) {
        // Only allow the new menu to appear if we have a single selection.
        if (singleSelection) {
            // Add 'New' menu item.
            final ExplorerPermissions documentPermissions = documentPermissionMap.get(primarySelection);
            final List<Item> children = createNewMenuItems(primarySelection, documentPermissions,
                    documentTypes);
            final boolean allowNew = children != null && children.size() > 0;

            if (allowNew) {
                final Item newItem = new SimpleParentMenuItem(1, SvgPresets.NEW_ITEM, SvgPresets.NEW_ITEM, "New",
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

    private List<Item> createNewMenuItems(final ExplorerNode explorerNode,
                                          final ExplorerPermissions documentPermissions, final DocumentTypes documentTypes) {
        final List<Item> children = new ArrayList<>();

        for (final DocumentType documentType : documentTypes.getAllTypes()) {
            if (documentPermissions.hasCreatePermission(documentType)) {
                final Item item = new IconMenuItem(documentType.getPriority(), new SvgIcon(ImageUtil.getImageURL() + documentType.getIconUrl(), 18, 18), null,
                        documentType.getDisplayType(), null, true, () -> ShowCreateDocumentDialogEvent.fire(DocumentPluginEventManager.this,
                        explorerNode, documentType.getType(), documentType.getDisplayType(), true));
                children.add(item);

                if (ExplorerConstants.FOLDER.equals(documentType.getType())) {
                    children.add(new Separator(documentType.getPriority()));
                }
            }
        }

        return children;
    }

    private void addModifyMenuItems(final List<Item> menuItems, final boolean singleSelection, final SharedMap<ExplorerNode, ExplorerPermissions> documentPermissionMap) {
        final List<ExplorerNode> readableItems = getExplorerNodeListWithPermission(documentPermissionMap, DocumentPermissionNames.READ);
        final List<ExplorerNode> updatableItems = getExplorerNodeListWithPermission(documentPermissionMap, DocumentPermissionNames.UPDATE);
        final List<ExplorerNode> deletableItems = getExplorerNodeListWithPermission(documentPermissionMap, DocumentPermissionNames.DELETE);

        // Folders are not valid items for requesting info
        final boolean containsFolder = documentPermissionMap.keySet().stream()
                    .findFirst().map(n -> n.getType().equals(ExplorerConstants.FOLDER))
                    .orElse(false);

        // Actions allowed based on permissions of selection
        final boolean allowRead = readableItems.size() > 0;
        final boolean allowUpdate = updatableItems.size() > 0;
        final boolean allowDelete = deletableItems.size() > 0;

        menuItems.add(createInfoMenuItem(readableItems, 3, singleSelection & allowRead & !containsFolder));
        menuItems.add(createCopyMenuItem(readableItems, 4, allowRead));
        menuItems.add(createMoveMenuItem(updatableItems, 5, allowUpdate));
        menuItems.add(createRenameMenuItem(updatableItems, 6, singleSelection && allowUpdate));
        menuItems.add(createDeleteMenuItem(deletableItems, 7, allowDelete));

        // Only allow users to change permissions if they have a single item selected.
        if (singleSelection) {
            final List<ExplorerNode> ownedItems = getExplorerNodeListWithPermission(documentPermissionMap, DocumentPermissionNames.OWNER);
            if (ownedItems.size() == 1) {
                menuItems.add(new Separator(8));
                menuItems.add(createPermissionsMenuItem(ownedItems.get(0), 8, true));
            }
        }
    }

    private MenuItem createCloseMenuItem(final boolean enabled) {
        final Command command = () -> {
            if (isTabItemSelected(selectedTab)) {
                RequestCloseTabEvent.fire(DocumentPluginEventManager.this, selectedTab);
            }
        };

        keyboardInterceptor.addKeyTest(ALT_W, command);

        return new IconMenuItem(3, SvgPresets.CLOSE, SvgPresets.CLOSE, "Close", "Alt+W", enabled,
                command);
    }

    private MenuItem createCloseAllMenuItem(final boolean enabled) {
        final Command command = () -> {
            if (isTabItemSelected(selectedTab)) {
                RequestCloseAllTabsEvent.fire(DocumentPluginEventManager.this);
            }
        };

        keyboardInterceptor.addKeyTest(ALT_SHIFT_W, command);

        return new IconMenuItem(4, SvgPresets.CLOSE, SvgPresets.CLOSE, "Close All",
                "Alt+Shift+W", enabled, command);
    }

    private MenuItem createSaveMenuItem(final int priority, final boolean enabled) {
        final Command command = () -> {
            if (isDirty(selectedTab)) {
                final DocumentTabData entityTabData = (DocumentTabData) selectedTab;
                WriteDocumentEvent.fire(DocumentPluginEventManager.this, entityTabData);
            }
        };

        keyboardInterceptor.addKeyTest(CTRL_S, command);

        return new IconMenuItem(priority, SvgPresets.SAVE, SvgPresets.SAVE, "Save", "Ctrl+S",
                enabled, command);
    }

    private MenuItem createSaveAllMenuItem(final int priority, final boolean enabled) {
        final Command command = () -> {
            if (isTabItemSelected(selectedTab)) {
                for (final DocumentPlugin<?> plugin : pluginMap.values()) {
                    plugin.saveAll();
                }
            }
        };

        keyboardInterceptor.addKeyTest(CTRL_SHIFT_S, command);

        return new IconMenuItem(priority, SvgPresets.SAVE, SvgPresets.SAVE, "Save All",
                "Ctrl+Shift+S", enabled, command);
    }


    private MenuItem createInfoMenuItem(final List<ExplorerNode> explorerNodeList, final int priority, final boolean enabled) {
        final Command command = () ->
                explorerNodeList.forEach(explorerNode ->
                        dispatcher.exec(new ExplorerServiceInfoAction(explorerNode.getDocRef()))
                                .onSuccess(s -> ShowInfoDocumentDialogEvent.fire(DocumentPluginEventManager.this, s))
                                .onFailure(t -> AlertEvent.fireError(DocumentPluginEventManager.this, t.getMessage(), null))
                );

        return new IconMenuItem(priority, SvgPresets.INFO, SvgPresets.INFO, "Info", null,
                enabled, command);
    }

    private MenuItem createCopyMenuItem(final List<ExplorerNode> explorerNodeList, final int priority, final boolean enabled) {
        final Command command = () -> ShowCopyDocumentDialogEvent.fire(DocumentPluginEventManager.this, explorerNodeList);

        return new IconMenuItem(priority, SvgPresets.COPY, SvgPresets.COPY, "Copy", null, enabled,
                command);
    }

    private MenuItem createMoveMenuItem(final List<ExplorerNode> explorerNodeList, final int priority, final boolean enabled) {
        final Command command = () -> ShowMoveDocumentDialogEvent.fire(DocumentPluginEventManager.this, explorerNodeList);

        return new IconMenuItem(priority, SvgPresets.MOVE, SvgPresets.MOVE, "Move", null, enabled,
                command);
    }

    private MenuItem createRenameMenuItem(final List<ExplorerNode> explorerNodeList, final int priority, final boolean enabled) {
        final Command command = () -> ShowRenameDocumentDialogEvent.fire(DocumentPluginEventManager.this, explorerNodeList);

        return new IconMenuItem(priority, SvgPresets.EDIT, SvgPresets.EDIT, "Rename", null,
                enabled, command);
    }

    private MenuItem createDeleteMenuItem(final List<ExplorerNode> explorerNodeList, final int priority, final boolean enabled) {
        final Command command = () -> deleteItems(explorerNodeList);

        return new IconMenuItem(priority, SvgPresets.DELETE, SvgPresets.DELETE, "Delete", null,
                enabled, command);
    }

    private MenuItem createPermissionsMenuItem(final ExplorerNode explorerNode, final int priority, final boolean enabled) {
        final Command command = () -> {
            if (explorerNode != null) {
                ShowPermissionsDialogEvent.fire(DocumentPluginEventManager.this, explorerNode);
            }
        };

        return new IconMenuItem(priority, SvgPresets.PERMISSIONS, SvgPresets.PERMISSIONS, "Permissions", null,
                enabled, command);
    }


    void registerPlugin(final String entityType, final DocumentPlugin<?> plugin) {
        pluginMap.put(entityType, plugin);
    }

    private boolean isTabItemSelected(final TabData tabData) {
        return tabData != null;
    }

    private boolean isEntityTabData(final TabData tabData) {
        if (isTabItemSelected(tabData)) {
            if (tabData instanceof DocumentEditPresenter<?, ?>) {
                return true;
            }
        }

        return false;
    }

    private boolean isDirty(final TabData tabData) {
        if (isEntityTabData(tabData)) {
            final DocumentEditPresenter<?, ?> editPresenter = (DocumentEditPresenter<?, ?>) tabData;
            if (editPresenter.isDirty()) {
                return true;
            }
        }

        return false;
    }

    private List<ExplorerNode> getSelectedItems() {
        if (selectionModel == null) {
            return Collections.emptyList();
        }

        return selectionModel.getSelectedItems();
    }

    private ExplorerNode getPrimarySelection() {
        if (selectionModel == null) {
            return null;
        }

        return selectionModel.getSelected();
    }
}
