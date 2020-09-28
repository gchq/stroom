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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.core.client.HasSave;
import stroom.core.client.HasSaveRegistry;
import stroom.core.client.KeyboardInterceptor;
import stroom.core.client.KeyboardInterceptor.KeyTest;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docref.HasDisplayValue;
import stroom.document.client.event.CopyDocumentEvent;
import stroom.document.client.event.CreateDocumentEvent;
import stroom.document.client.event.MoveDocumentEvent;
import stroom.document.client.event.RefreshDocumentEvent;
import stroom.document.client.event.RenameDocumentEvent;
import stroom.document.client.event.SaveAsDocumentEvent;
import stroom.document.client.event.ShowCopyDocumentDialogEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.document.client.event.ShowInfoDocumentDialogEvent;
import stroom.document.client.event.ShowMoveDocumentDialogEvent;
import stroom.document.client.event.ShowPermissionsDialogEvent;
import stroom.document.client.event.ShowRenameDocumentDialogEvent;
import stroom.document.client.event.WriteDocumentEvent;
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
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodePermissions;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerServiceCopyRequest;
import stroom.explorer.shared.ExplorerServiceCreateRequest;
import stroom.explorer.shared.ExplorerServiceDeleteRequest;
import stroom.explorer.shared.ExplorerServiceMoveRequest;
import stroom.explorer.shared.ExplorerServiceRenameRequest;
import stroom.explorer.shared.PermissionInheritance;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.shared.DocumentPermissionNames;
import stroom.svg.client.SvgIcon;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.ImageUtil;
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

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DocumentPluginEventManager extends Plugin {
    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    private static final KeyTest CTRL_S = event -> event.getCtrlKey() && !event.getShiftKey() && event.getKeyCode() == 'S';
    private static final KeyTest CTRL_SHIFT_S = event -> event.getCtrlKey() && event.getShiftKey() && event.getKeyCode() == 'S';
    private static final KeyTest ALT_W = event -> event.getAltKey() && !event.getShiftKey() && event.getKeyCode() == 'W';
    private static final KeyTest ALT_SHIFT_W = event -> event.getAltKey() && event.getShiftKey() && event.getKeyCode() == 'W';

    private final HasSaveRegistry hasSaveRegistry;
    private final RestFactory restFactory;
    private final DocumentTypeCache documentTypeCache;
    private final MenuListPresenter menuListPresenter;
    private final DocumentPluginRegistry documentPluginRegistry;
    private final KeyboardInterceptor keyboardInterceptor;
    private TabData selectedTab;
    private MultiSelectionModel<ExplorerNode> selectionModel;
    private final UiConfigCache clientPropertyCache;

    @Inject
    public DocumentPluginEventManager(final EventBus eventBus,
                                      final HasSaveRegistry hasSaveRegistry,
                                      final KeyboardInterceptor keyboardInterceptor,
                                      final RestFactory restFactory,
                                      final DocumentTypeCache documentTypeCache,
                                      final MenuListPresenter menuListPresenter,
                                      final DocumentPluginRegistry documentPluginRegistry,
                                      final UiConfigCache clientPropertyCache) {
        super(eventBus);
        this.hasSaveRegistry = hasSaveRegistry;
        this.keyboardInterceptor = keyboardInterceptor;
        this.restFactory = restFactory;
        this.documentTypeCache = documentTypeCache;
        this.menuListPresenter = menuListPresenter;
        this.documentPluginRegistry = documentPluginRegistry;
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected void onBind() {
        super.onBind();

        // track the currently selected content tab.
        registerHandler(getEventBus().addHandler(ContentTabSelectionChangeEvent.getType(), e -> selectedTab = e.getTabData()));

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
                            final DocumentPlugin<?> plugin = documentPluginRegistry.get(explorerNode.getType());
                            if (plugin != null) {
                                plugin.open(explorerNode.getDocRef(), event.getSelectionType().isDoubleSelect());
                            }
                        }
                    }
                }));

//        clientPropertyCache.get()
//                .onSuccess(uiConfig -> {
//                    registerHandler(
//                            getEventBus().addHandler(ShowPermissionsDialogEvent.getType(), event -> {
//                                final Hyperlink hyperlink = new Hyperlink.Builder()
//                                        .text("Permissions")
//                                        .href(uiConfig.getUrl().getDocumentPermissions() + event.getExplorerNode().getUuid())
//                                        .type(HyperlinkType.TAB + "|Document Permissions")
//                                        .icon(SvgPresets.PERMISSIONS)
//                                        .build();
//                                HyperlinkEvent.fire(this, hyperlink);
//                            }));
//                });


        // 11. Handle entity reload events.
        registerHandler(getEventBus().addHandler(RefreshDocumentEvent.getType(), event -> {
            final DocumentPlugin<?> plugin = documentPluginRegistry.get(event.getDocRef().getType());
            if (plugin != null) {
                plugin.reload(event.getDocRef());
            }
        }));

        // 5. Handle save events.
        registerHandler(getEventBus().addHandler(WriteDocumentEvent.getType(), event -> {
            if (isDirty(event.getTabData())) {
                final DocumentTabData entityTabData = event.getTabData();
                final DocumentPlugin<?> plugin = documentPluginRegistry.get(entityTabData.getType());
                if (plugin != null) {
                    plugin.save(entityTabData);
                }
            }
        }));

        // 6. Handle save as events.
        registerHandler(getEventBus().addHandler(SaveAsDocumentEvent.getType(), event -> {
            final DocumentPlugin<?> plugin = documentPluginRegistry.get(event.getDocRef().getType());
            if (plugin != null) {
                plugin.saveAs(event.getDocRef());
            }
        }));

        //////////////////////////////
        // START EXPLORER EVENTS
        ///////////////////////////////

        // 1. Handle entity creation events.
        registerHandler(getEventBus().addHandler(CreateDocumentEvent.getType(), event -> {
            create(event.getDocType(), event.getDocName(), event.getDestinationFolderRef(), event.getPermissionInheritance(), docRef -> {
                // Hide the create document presenter.
                HidePopupEvent.fire(DocumentPluginEventManager.this, event.getPresenter());

                highlight(docRef);

                // The initiator of this event can now do what they want with the docref.
                event.getNewDocConsumer().accept(docRef);
            });
        }));

        // 8.1. Handle entity copy events.
        registerHandler(getEventBus().addHandler(CopyDocumentEvent.getType(), event -> {
            copy(event.getDocRefs(), event.getDestinationFolderRef(), event.getPermissionInheritance(), result -> {
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
            move(event.getDocRefs(), event.getDestinationFolderRef(), event.getPermissionInheritance(), result -> {
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

            rename(event.getDocRef(), event.getDocName(), docRef -> {
                highlight(docRef);
                RefreshDocumentEvent.fire(this, docRef);
            });
        }));

        // 10. Handle entity delete events.
        registerHandler(getEventBus().addHandler(ExplorerTreeDeleteEvent.getType(), event -> {
            if (getSelectedItems().size() > 0) {
                fetchPermissions(getSelectedItems(), documentPermissionMap ->
                        documentTypeCache.fetch(documentTypes -> {
                            final List<ExplorerNode> deletableItems = getExplorerNodeListWithPermission(documentPermissionMap, DocumentPermissionNames.DELETE, false);
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
                fetchPermissions(selectedItems, documentPermissionMap ->
                        documentTypeCache.fetch(documentTypes -> {
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


    private void renameItems(final List<ExplorerNode> explorerNodeList) {
        final List<ExplorerNode> dirtyList = new ArrayList<>();
        final List<ExplorerNode> cleanList = new ArrayList<>();

        explorerNodeList.forEach(node -> {
            final DocRef docRef = node.getDocRef();
            final DocumentPlugin<?> plugin = documentPluginRegistry.get(docRef.getType());
            if (plugin != null && plugin.isDirty(docRef)) {
                dirtyList.add(node);
            } else {
                cleanList.add(node);
            }
        });

        if (dirtyList.size() > 0) {
            final DocRef docRef = dirtyList.get(0).getDocRef();
            AlertEvent.fireWarn(this, "You must save changes to " + docRef.getType() + " '"
                    + docRef.getDisplayValue()
                    + "' before it can be renamed.", null);
        } else if (cleanList.size() > 0) {
            ShowRenameDocumentDialogEvent.fire(DocumentPluginEventManager.this, cleanList);
        }
    }

    private void deleteItems(final List<ExplorerNode> explorerNodeList) {
        if (explorerNodeList != null) {
            final List<DocRef> docRefs = explorerNodeList.stream().map(ExplorerNode::getDocRef).collect(Collectors.toList());
            if (docRefs.size() > 0) {
                ConfirmEvent.fire(DocumentPluginEventManager.this, "Are you sure you want to delete these items?", ok -> {
                    if (ok) {
                        delete(docRefs, result -> {
                            if (result.getMessage().length() > 0) {
                                AlertEvent.fireInfo(DocumentPluginEventManager.this, "Unable to delete some items", result.getMessage(), null);
                            }

                            // Refresh the explorer tree so the documents are marked as deleted.
                            RefreshExplorerTreeEvent.fire(DocumentPluginEventManager.this);
                        });
                    }
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


    public void create(final String docType, final String docName, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance, final Consumer<DocRef> consumer) {
        final Rest<DocRef> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(EXPLORER_RESOURCE)
                .create(new ExplorerServiceCreateRequest(docType, docName, destinationFolderRef, permissionInheritance));
    }

    private void copy(final List<DocRef> docRefs, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance, final Consumer<BulkActionResult> consumer) {
        final Rest<BulkActionResult> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(EXPLORER_RESOURCE)
                .copy(new ExplorerServiceCopyRequest(docRefs, destinationFolderRef, permissionInheritance));
    }

    private void move(final List<DocRef> docRefs, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance, final Consumer<BulkActionResult> consumer) {
        final Rest<BulkActionResult> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(EXPLORER_RESOURCE)
                .move(new ExplorerServiceMoveRequest(docRefs, destinationFolderRef, permissionInheritance));
    }

    private void rename(final DocRef docRef, final String docName, final Consumer<DocRef> consumer) {
        final Rest<DocRef> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(EXPLORER_RESOURCE)
                .rename(new ExplorerServiceRenameRequest(docRef, docName));
    }

    private void delete(final List<DocRef> docRefs, final Consumer<BulkActionResult> consumer) {
        final Rest<BulkActionResult> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(EXPLORER_RESOURCE)
                .delete(new ExplorerServiceDeleteRequest(docRefs));
    }

    /**
     * This method will highlight the supplied document item in the explorer tree.
     */
    public void highlight(final DocRef docRef) {
        // Open up parent items.
        final ExplorerNode documentData = ExplorerNode.create(docRef);
        HighlightExplorerNodeEvent.fire(DocumentPluginEventManager.this, documentData);
    }


    private List<ExplorerNode> getExplorerNodeListWithPermission(final Map<ExplorerNode, ExplorerNodePermissions> documentPermissionMap, final String permission, final boolean includeSystemNodes) {
        final List<ExplorerNode> list = new ArrayList<>();
        for (final Map.Entry<ExplorerNode, ExplorerNodePermissions> entry : documentPermissionMap.entrySet()) {
            if ((includeSystemNodes || !DocumentTypes.isSystem(entry.getKey().getType())) && entry.getValue().hasDocumentPermission(permission)) {
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

                fetchPermissions(selectedItems, documentPermissionMap -> documentTypeCache.fetch(documentTypes -> {
                    final List<Item> menuItems = new ArrayList<>();

                    // Only allow the new menu to appear if we have a single selection.
                    addNewMenuItem(menuItems, singleSelection, documentPermissionMap, primarySelection, documentTypes);
                    menuItems.add(createCloseMenuItem(isTabItemSelected(selectedTab)));
                    menuItems.add(createCloseAllMenuItem(isTabItemSelected(selectedTab)));
                    menuItems.add(new Separator(5));
                    menuItems.add(createSaveMenuItem(6, isDirty(selectedTab)));
                    menuItems.add(createSaveAllMenuItem(8, hasSaveRegistry.isDirty()));
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

        List<ExplorerNode> explorerNodes = Collections.emptyList();
        if (explorerNode != null) {
            explorerNodes = Collections.singletonList(explorerNode);
        }

        fetchPermissions(explorerNodes, documentPermissions ->
                documentTypeCache.fetch(documentTypes ->
                        future.setResult(createNewMenuItems(explorerNode, documentPermissions.get(explorerNode), documentTypes))));
        return future;
    }

    private void fetchPermissions(final List<ExplorerNode> explorerNodes, final Consumer<Map<ExplorerNode, ExplorerNodePermissions>> consumer) {
        final Rest<Set<ExplorerNodePermissions>> rest = restFactory.create();
        rest
                .onSuccess(response -> {
                    final Map<ExplorerNode, ExplorerNodePermissions> map = response.stream().collect(Collectors.toMap(ExplorerNodePermissions::getExplorerNode, Function.identity()));
                    consumer.accept(map);
                })
                .call(EXPLORER_RESOURCE)
                .fetchExplorerPermissions(explorerNodes);
    }

//    private DocRef getDocRef(final ExplorerNode explorerNode) {
//        DocRef docRef = null;
//        if (explorerNode != null && explorerNode instanceof EntityData) {
//            final EntityData entityData = (EntityData) explorerNode;
//            docRef = entityData.getDocRef();
//        }
//        return docRef;
//    }

    private void addNewMenuItem(final List<Item> menuItems, final boolean singleSelection, final Map<ExplorerNode, ExplorerNodePermissions> documentPermissionMap, final ExplorerNode primarySelection, final DocumentTypes documentTypes) {
        // Only allow the new menu to appear if we have a single selection.
        if (singleSelection) {
            // Add 'New' menu item.
            final ExplorerNodePermissions documentPermissions = documentPermissionMap.get(primarySelection);
            final List<Item> children = createNewMenuItems(primarySelection, documentPermissions,
                    documentTypes);
            final boolean allowNew = children.size() > 0;

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
                                          final ExplorerNodePermissions documentPermissions,
                                          final DocumentTypes documentTypes) {
        final List<Item> children = new ArrayList<>();

        for (final DocumentType documentType : documentTypes.getNonSystemTypes()) {
            if (documentPermissions.hasCreatePermission(documentType)) {
                final Consumer<DocRef> newDocumentConsumer = docRef -> {
                    // Open the document in the content pane.
                    final DocumentPlugin<?> plugin = documentPluginRegistry.get(docRef.getType());
                    if (plugin != null) {
                        plugin.open(docRef, true);
                    }
                };

                final Item item = new IconMenuItem(
                        documentType.getPriority(),
                        new SvgIcon(ImageUtil.getImageURL() + documentType.getIconUrl(), 18, 18),
                        null,
                        documentType.getDisplayType(), null, true, () ->
                        ShowCreateDocumentDialogEvent.fire(
                                DocumentPluginEventManager.this,
                                explorerNode,
                                documentType.getType(),
                                documentType.getDisplayType(),
                                true,
                                newDocumentConsumer));
                children.add(item);

                if (DocumentTypes.isFolder(documentType.getType())) {
                    children.add(new Separator(documentType.getPriority()));
                }
            }
        }

        return children;
    }

    private void addModifyMenuItems(final List<Item> menuItems, final boolean singleSelection, final Map<ExplorerNode, ExplorerNodePermissions> documentPermissionMap) {
        final List<ExplorerNode> readableItems = getExplorerNodeListWithPermission(documentPermissionMap, DocumentPermissionNames.READ, false);
        final List<ExplorerNode> updatableItems = getExplorerNodeListWithPermission(documentPermissionMap, DocumentPermissionNames.UPDATE, false);
        final List<ExplorerNode> deletableItems = getExplorerNodeListWithPermission(documentPermissionMap, DocumentPermissionNames.DELETE, false);

        // Actions allowed based on permissions of selection
        final boolean allowRead = readableItems.size() > 0;
        final boolean allowUpdate = updatableItems.size() > 0;
        final boolean allowDelete = deletableItems.size() > 0;

        menuItems.add(createInfoMenuItem(readableItems, 3, singleSelection & allowRead));
        menuItems.add(createCopyMenuItem(readableItems, 4, allowRead));
        menuItems.add(createMoveMenuItem(updatableItems, 5, allowUpdate));
        menuItems.add(createRenameMenuItem(updatableItems, 6, singleSelection && allowUpdate));
        menuItems.add(createDeleteMenuItem(deletableItems, 7, allowDelete));

        // Only allow users to change permissions if they have a single item selected.
        if (singleSelection) {
            final List<ExplorerNode> ownedItems = getExplorerNodeListWithPermission(documentPermissionMap, DocumentPermissionNames.OWNER, true);
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
                final HasSave hasSave = (HasSave) selectedTab;
                hasSave.save();
            }
        };

        keyboardInterceptor.addKeyTest(CTRL_S, command);

        return new IconMenuItem(priority, SvgPresets.SAVE, SvgPresets.SAVE, "Save", "Ctrl+S",
                enabled, command);
    }

    private MenuItem createSaveAllMenuItem(final int priority, final boolean enabled) {
        final Command command = hasSaveRegistry::save;

        keyboardInterceptor.addKeyTest(CTRL_SHIFT_S, command);

        return new IconMenuItem(priority, SvgPresets.SAVE, SvgPresets.SAVE, "Save All",
                "Ctrl+Shift+S", enabled, command);
    }

    private MenuItem createInfoMenuItem(final List<ExplorerNode> explorerNodeList, final int priority, final boolean enabled) {
        final Command command = () ->
                explorerNodeList.forEach(explorerNode -> {
                    final Rest<DocRefInfo> rest = restFactory.create();
                    rest
                            .onSuccess(s -> ShowInfoDocumentDialogEvent.fire(DocumentPluginEventManager.this, s))
                            .onFailure(t -> AlertEvent.fireError(DocumentPluginEventManager.this, t.getMessage(), null))
                            .call(EXPLORER_RESOURCE)
                            .info(explorerNode.getDocRef());
                });

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
        final Command command = () -> renameItems(explorerNodeList);

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

        return new IconMenuItem(priority, SvgPresets.LOCKED_AMBER, SvgPresets.LOCKED_AMBER, "Permissions", null,
                enabled, command);
    }

    void registerPlugin(final String entityType, final DocumentPlugin<?> plugin) {
        documentPluginRegistry.register(entityType, plugin);
        hasSaveRegistry.register(plugin);
    }

    private boolean isTabItemSelected(final TabData tabData) {
        return tabData != null;
    }

    private boolean isDirty(final TabData tabData) {
        if (tabData instanceof HasSave) {
            final HasSave hasSave = (HasSave) tabData;
            if (hasSave.isDirty()) {
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
