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
import stroom.core.client.UrlParameters;
import stroom.core.client.presenter.Plugin;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestError;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
import stroom.document.client.event.CopyDocumentEvent;
import stroom.document.client.event.CreateDocumentEvent;
import stroom.document.client.event.DeleteDocumentEvent;
import stroom.document.client.event.MoveDocumentEvent;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.document.client.event.RefreshDocumentEvent;
import stroom.document.client.event.RenameDocumentEvent;
import stroom.document.client.event.ResultCallback;
import stroom.document.client.event.SaveAsDocumentEvent;
import stroom.document.client.event.SaveDocumentEvent;
import stroom.document.client.event.SetDocumentAsFavouriteEvent;
import stroom.document.client.event.ShowCopyDocumentDialogEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.document.client.event.ShowInfoDocumentDialogEvent;
import stroom.document.client.event.ShowMoveDocumentDialogEvent;
import stroom.document.client.event.ShowPermissionsDialogEvent;
import stroom.document.client.event.ShowRenameDocumentDialogEvent;
import stroom.explorer.client.event.CreateNewDocumentEvent;
import stroom.explorer.client.event.ExplorerTaskListener;
import stroom.explorer.client.event.ExplorerTreeDeleteEvent;
import stroom.explorer.client.event.ExplorerTreeSelectEvent;
import stroom.explorer.client.event.HighlightExplorerNodeEvent;
import stroom.explorer.client.event.LocateDocEvent;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.explorer.client.event.ShowEditNodeTagsDialogEvent;
import stroom.explorer.client.event.ShowExplorerMenuEvent;
import stroom.explorer.client.event.ShowFindEvent;
import stroom.explorer.client.event.ShowFindInContentEvent;
import stroom.explorer.client.event.ShowNewMenuEvent;
import stroom.explorer.client.event.ShowRecentItemsEvent;
import stroom.explorer.client.event.ShowRemoveNodeTagsDialogEvent;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypeGroup;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerFavouriteResource;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodePermissions;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerServiceCopyRequest;
import stroom.explorer.shared.ExplorerServiceCreateRequest;
import stroom.explorer.shared.ExplorerServiceDeleteRequest;
import stroom.explorer.shared.ExplorerServiceMoveRequest;
import stroom.explorer.shared.ExplorerServiceRenameRequest;
import stroom.explorer.shared.NodeFlag;
import stroom.explorer.shared.PermissionInheritance;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.client.event.ExportConfigEvent;
import stroom.importexport.client.event.ImportConfigEvent;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent.DependencyType;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.task.client.DefaultTaskListener;
import stroom.task.client.TaskHandlerFactory;
import stroom.util.client.ClipboardUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.IconParentMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItem;
import stroom.widget.menu.client.presenter.Separator;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.tab.client.event.RequestCloseAllTabsEvent;
import stroom.widget.tab.client.event.RequestCloseOtherTabsEvent;
import stroom.widget.tab.client.event.RequestCloseSavedTabsEvent;
import stroom.widget.tab.client.event.RequestCloseTabEvent;
import stroom.widget.tab.client.event.ShowTabMenuEvent;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.Future;
import stroom.widget.util.client.FutureImpl;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class DocumentPluginEventManager extends Plugin {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    private static final ExplorerFavouriteResource EXPLORER_FAV_RESOURCE = GWT.create(ExplorerFavouriteResource.class);
    private final TaskHandlerFactory explorerListener = new ExplorerTaskListener(this);

    private final HasSaveRegistry hasSaveRegistry;
    private final RestFactory restFactory;
    private final DocumentTypeCache documentTypeCache;
    private final DocumentPluginRegistry documentPluginRegistry;
    private final ClientSecurityContext securityContext;
    private TabData selectedTab;
    private MultiSelectionModel<ExplorerNode> selectionModel;

    @Inject
    public DocumentPluginEventManager(final EventBus eventBus,
                                      final HasSaveRegistry hasSaveRegistry,
                                      final RestFactory restFactory,
                                      final DocumentTypeCache documentTypeCache,
                                      final DocumentPluginRegistry documentPluginRegistry,
                                      final ClientSecurityContext securityContext) {
        super(eventBus);
        this.hasSaveRegistry = hasSaveRegistry;
        this.restFactory = restFactory;
        this.documentTypeCache = documentTypeCache;
        this.documentPluginRegistry = documentPluginRegistry;
        this.securityContext = securityContext;

        KeyBinding.addCommand(Action.ITEM_CLOSE, () -> {
            if (isTabItemSelected(selectedTab)) {
                RequestCloseTabEvent.fire(DocumentPluginEventManager.this, selectedTab);
            }
        });
        KeyBinding.addCommand(Action.ITEM_CLOSE_ALL, () -> RequestCloseAllTabsEvent.fire(this));

        KeyBinding.addCommand(Action.ITEM_SAVE, () -> {
            if (isDirty(selectedTab)) {
                final HasSave hasSave = (HasSave) selectedTab;
                hasSave.save();
            }
        });
        KeyBinding.addCommand(Action.ITEM_SAVE_ALL, hasSaveRegistry::save);
        KeyBinding.addCommand(Action.FIND, () -> ShowFindEvent.fire(this));
        KeyBinding.addCommand(Action.FIND_IN_CONTENT, () -> ShowFindInContentEvent.fire(this));
        KeyBinding.addCommand(Action.RECENT_ITEMS, () -> ShowRecentItemsEvent.fire(this));
        KeyBinding.addCommand(Action.LOCATE, () -> {
            final DocRef selectedDoc = getSelectedDoc(selectedTab);
            if (selectedDoc != null) {
                LocateDocEvent.fire(this, selectedDoc);
            }
        });
    }

    @Override
    protected void onBind() {
        super.onBind();

        // track the currently selected content tab.
        registerHandler(getEventBus().addHandler(ContentTabSelectionChangeEvent.getType(), e ->
                selectedTab = e.getTabData()));

        // Add support to locate items in the explorer tree.
        registerHandler(getEventBus().addHandler(LocateDocEvent.getType(), e -> {
            if (e.getDocRef() != null) {
                highlight(e.getDocRef(), explorerListener);
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
                                plugin.open(
                                        explorerNode.getDocRef(),
                                        event.getSelectionType().isDoubleSelect(),
                                        false,
                                        new DefaultTaskListener(this));
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
//                                        .href(uiConfig.getUrl().getDocumentPermissions() +
//                                        event.getExplorerNode().getUuid())
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
//                GWT.log("reloading " + event.getDocRef().getName());
                plugin.reload(event.getDocRef());
            }
        }));

        // 5. Handle save events.
        registerHandler(getEventBus().addHandler(SaveDocumentEvent.getType(), event -> {
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
            final DocumentTabData tabData = event.getTabData();
            final DocumentPlugin<?> plugin = documentPluginRegistry.get(tabData.getType());
            if (plugin != null) {
                // Get the explorer node for the docref.
                TaskHandlerFactory taskHandlerFactory = null;
                if (tabData instanceof TaskHandlerFactory) {
                    taskHandlerFactory = (TaskHandlerFactory) tabData;
                }

                restFactory
                        .create(EXPLORER_RESOURCE)
                        .method(res -> res.getFromDocRef(tabData.getDocRef()))
                        .onSuccess(explorerNode -> {
                            // Now we have the explorer node proceed with the save as.
                            plugin.saveAs(tabData, explorerNode);
                        })
                        .taskHandlerFactory(taskHandlerFactory)
                        .exec();
            }
        }));

        //////////////////////////////
        // START EXPLORER EVENTS
        ///////////////////////////////

        // 1. Handle entity creation events.
        registerHandler(getEventBus().addHandler(CreateDocumentEvent.getType(), event ->
                create(event.getDocType(),
                        event.getDocName(),
                        event.getDestinationFolder(),
                        event.getPermissionInheritance(),
                        explorerNode -> {
                            // Hide the create document presenter.
                            event.getHidePopupRequestEvent().hide();

                            highlight(explorerNode);

                            // The initiator of this event can now do what they want with the docref.
                            event.getNewDocConsumer().accept(explorerNode);
                        }, explorerListener,
                        event.getHidePopupRequestEvent())));

        // 8.1. Handle entity open events.
        registerHandler(getEventBus().addHandler(OpenDocumentEvent.getType(), event ->
                open(event.getDocRef(),
                        event.isForceOpen(),
                        event.isFullScreen(),
                        explorerListener)));

        // 8.2. Handle entity copy events.
        registerHandler(getEventBus().addHandler(CopyDocumentEvent.getType(), event -> copy(
                event.getExplorerNodes(),
                event.getDestinationFolder(),
                event.isAllowRename(),
                event.getDocName(),
                event.getPermissionInheritance(), result -> {
                    // Hide the copy document presenter.
                    event.getHidePopupRequestEvent().hide();

                    if (result.getMessage().length() > 0) {
                        AlertEvent.fireInfo(DocumentPluginEventManager.this,
                                "Unable to copy some items",
                                result.getMessage(),
                                null);
                    }

                    if (result.getExplorerNodes().size() > 0) {
                        highlight(result.getExplorerNodes().get(0));
                    }
                }, explorerListener,
                event.getHidePopupRequestEvent())));

        // 8.3. Handle entity move events.
        registerHandler(getEventBus().addHandler(MoveDocumentEvent.getType(), event -> move(
                event.getExplorerNodes(), event.getDestinationFolder(), event.getPermissionInheritance(), result -> {
                    // Hide the move document presenter.
                    event.getHidePopupRequestEvent().hide();

                    if (result.getMessage().length() > 0) {
                        AlertEvent.fireInfo(DocumentPluginEventManager.this,
                                "Unable to move some items",
                                result.getMessage(),
                                null);
                    }

                    if (result.getExplorerNodes().size() > 0) {
                        highlight(result.getExplorerNodes().get(0));
                    }
                }, explorerListener,
                event.getHidePopupRequestEvent())));

        // 8.4. Handle entity delete events.
        registerHandler(getEventBus().addHandler(DeleteDocumentEvent.getType(), event -> {
            final Runnable action = () ->
                    delete(event.getDocRefs(), result ->
                            handleDeleteResult(result, event.getCallback()), explorerListener);

            if (event.getConfirm()) {
                final int cnt = GwtNullSafe.size(event.getDocRefs());
                final String msg = GwtNullSafe.size(event.getDocRefs()) > 1
                        ? "Are you sure you want to delete these " + cnt + " items?"
                        : "Are you sure you want to delete this item?";
                ConfirmEvent.fire(DocumentPluginEventManager.this, msg, ok -> {
                    if (ok) {
                        action.run();
                    }
                });
            } else {
                action.run();
            }
        }));

        // 9. Handle entity rename events.
        registerHandler(getEventBus().addHandler(RenameDocumentEvent.getType(), event -> {
            // Hide the rename document presenter.
            event.getHidePopupRequestEvent().hide();

            rename(event.getExplorerNode(), event.getDocName(), explorerNode -> {
                highlight(explorerNode);
                RefreshDocumentEvent.fire(this, explorerNode.getDocRef());
            }, explorerListener, event.getHidePopupRequestEvent());
        }));

        // 10. Handle entity delete events.
        registerHandler(getEventBus().addHandler(ExplorerTreeDeleteEvent.getType(), event -> {
            if (getSelectedItems().size() > 0) {
                fetchPermissions(getSelectedItems(), documentPermissionMap ->
                        documentTypeCache.fetch(documentTypes -> {
                            final List<ExplorerNode> deletableItems = getExplorerNodeListWithPermission(
                                    documentPermissionMap,
                                    DocumentPermissionNames.DELETE,
                                    false);
                            if (deletableItems.size() > 0) {
                                deleteItems(deletableItems, explorerListener);
                            }
                        }, explorerListener), explorerListener);
            }
        }));

        // Handle setting document as Favourite events
        registerHandler(getEventBus().addHandler(SetDocumentAsFavouriteEvent.getType(), event -> {
            setAsFavourite(event.getDocRef(), event.getSetFavourite(), explorerListener);
        }));

        //////////////////////////////
        // END EXPLORER EVENTS
        ///////////////////////////////


        // Handle the display of the `New` item menu
        registerHandler(getEventBus().addHandler(ShowNewMenuEvent.getType(), event -> {
            if (getSelectedItems().size() == 1) {
                final ExplorerNode primarySelection = getPrimarySelection();
                getNewMenuItems(primarySelection).onSuccess(children ->
                        ShowMenuEvent
                                .builder()
                                .items(children)
                                .popupPosition(event.getPopupPosition())
                                .addAutoHidePartner(event.getElement())
                                .fire(this));
            }
        }));

        // Handle key bind for creating a doc in the selected folder
        registerHandler(getEventBus().addHandler(CreateNewDocumentEvent.getType(), event -> {
            final List<ExplorerNode> selectedItems = getSelectedItems();
            // TODO The CreateDocumentPresenter has an exp tree picker on it so debatable whether
            //  we should just show the dialog regardless of selection/perms and let the dialog
            //  deal with it.
            if (selectedItems.size() == 1) {
                final ExplorerNode explorerNode = selectedItems.get(0);
                fetchPermissions(selectedItems, documentPermissions -> {
                    final ExplorerNodePermissions permissions = documentPermissions.get(explorerNode);
                    final String type = event.getDocumentType();
                    if (permissions.hasCreatePermission(type)) {
                        documentTypeCache.fetch(documentTypes -> {
                            GwtNullSafe.consume(documentTypes.getDocumentType(type), documentType -> {
                                fireShowCreateDocumentDialogEvent(documentType, explorerNode);
                            });
                        }, explorerListener);
                    }
                }, explorerListener);
            }
        }));

        // Handle the display of the explorer item context menu
        registerHandler(getEventBus().addHandler(ShowExplorerMenuEvent.getType(), event -> {
            final List<ExplorerNode> selectedItems = getSelectedItems();
            final boolean singleSelection = selectedItems.size() == 1;
            final ExplorerNode primarySelection = getPrimarySelection();

            if (selectedItems.size() > 0 && !ExplorerConstants.isFavouritesNode(primarySelection)) {
                showItemContextMenu(event, selectedItems, singleSelection, primarySelection);
            }
        }));

        // Handle the context menu for open tabs
        registerHandler(getEventBus().addHandler(ShowTabMenuEvent.getType(), event -> {
            final List<Item> menuItems = new ArrayList<>();

            menuItems.add(createCloseMenuItem(1, event.getTabData()));
            menuItems.add(createCloseOthersMenuItem(2, event.getTabData()));
            menuItems.add(createCloseSavedMenuItem(3, event.getTabData()));
            menuItems.add(createCloseAllMenuItem(4, event.getTabData()));
            menuItems.add(new Separator(5));
            menuItems.add(createSaveMenuItem(6, event.getTabData()));
            menuItems.add(createSaveAllMenuItem(8));
            menuItems.add(new Separator(9));
            menuItems.add(createLocateMenuItem(10, event.getTabData()));
            menuItems.add(addToFavouritesMenuItem(11, event.getTabData()));

            ShowMenuEvent
                    .builder()
                    .items(menuItems)
                    .popupPosition(event.getPopupPosition())
                    .fire(this);
        }));
    }

    private void showItemContextMenu(final ShowExplorerMenuEvent event,
                                     final List<ExplorerNode> selectedItems,
                                     final boolean singleSelection,
                                     final ExplorerNode primarySelection) {
        fetchPermissions(selectedItems, documentPermissionMap ->
                documentTypeCache.fetch(documentTypes -> {
                    final List<Item> menuItems = new ArrayList<>();

                    // Only allow the new menu to appear if we have a single selection.
                    addNewMenuItem(menuItems,
                            singleSelection,
                            documentPermissionMap,
                            primarySelection,
                            documentTypes);

                    addModifyMenuItems(menuItems, singleSelection, documentPermissionMap);

                    ShowMenuEvent
                            .builder()
                            .items(menuItems)
                            .popupPosition(event.getPopupPosition())
                            .allowCloseOnMoveLeft() // Right arrow opens menu, left closes it
                            .fire(this);
                }, explorerListener), explorerListener);
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

    private void deleteItems(final List<ExplorerNode> explorerNodeList, final TaskHandlerFactory taskHandlerFactory) {
        if (explorerNodeList != null && explorerNodeList.size() > 0) {
            final List<DocRef> docRefs = explorerNodeList
                    .stream()
                    .map(ExplorerNode::getDocRef)
                    .collect(Collectors.toList());
            DeleteDocumentEvent.fire(
                    DocumentPluginEventManager.this,
                    docRefs,
                    true,
                    taskHandlerFactory);
        }
    }

    private void handleDeleteResult(final BulkActionResult result, ResultCallback callback) {
        boolean success = true;
        if (result.getMessage().length() > 0) {
            AlertEvent.fireInfo(DocumentPluginEventManager.this,
                    "Unable to delete some items",
                    result.getMessage(),
                    null);

            success = false;
        }

        RequestCloseTabEvent.fire(DocumentPluginEventManager.this, selectedTab);

        // Refresh the tree
        RefreshExplorerTreeEvent.fire(DocumentPluginEventManager.this);

        if (callback != null) {
            callback.onResult(success);
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


    public void create(final String docType,
                       final String docName,
                       final ExplorerNode destinationFolder,
                       final PermissionInheritance permissionInheritance,
                       final Consumer<ExplorerNode> consumer,
                       final TaskHandlerFactory taskHandlerFactory,
                       final HidePopupRequestEvent hidePopupRequestEvent) {
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.create(new ExplorerServiceCreateRequest(
                        docType,
                        docName,
                        destinationFolder,
                        permissionInheritance)))
                .onSuccess(consumer)
                .onFailure(new DefaultErrorHandler(this, hidePopupRequestEvent::reset))
                .taskHandlerFactory(taskHandlerFactory)
                .exec();
    }

    private void copy(final List<ExplorerNode> explorerNodes,
                      final ExplorerNode destinationFolder,
                      final boolean allowRename,
                      final String newName,
                      final PermissionInheritance permissionInheritance,
                      final Consumer<BulkActionResult> consumer,
                      final TaskHandlerFactory taskHandlerFactory,
                      final HidePopupRequestEvent hidePopupRequestEvent) {
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.copy(new ExplorerServiceCopyRequest(
                        explorerNodes,
                        destinationFolder,
                        allowRename,
                        newName,
                        permissionInheritance)))
                .onSuccess(consumer)
                .onFailure(new DefaultErrorHandler(this, hidePopupRequestEvent::reset))
                .taskHandlerFactory(taskHandlerFactory)
                .exec();
    }

    private void move(final List<ExplorerNode> explorerNodes,
                      final ExplorerNode destinationFolder,
                      final PermissionInheritance permissionInheritance,
                      final Consumer<BulkActionResult> consumer,
                      final TaskHandlerFactory taskHandlerFactory,
                      final HidePopupRequestEvent hidePopupRequestEvent) {
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.move(new ExplorerServiceMoveRequest(
                        explorerNodes,
                        destinationFolder,
                        permissionInheritance)))
                .onSuccess(consumer)
                .onFailure(new DefaultErrorHandler(this, hidePopupRequestEvent::reset))
                .taskHandlerFactory(taskHandlerFactory)
                .exec();
    }

    private void rename(final ExplorerNode explorerNode,
                        final String docName,
                        final Consumer<ExplorerNode> consumer,
                        final TaskHandlerFactory taskHandlerFactory,
                        final HidePopupRequestEvent hidePopupRequestEvent) {
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.rename(new ExplorerServiceRenameRequest(explorerNode, docName)))
                .onSuccess(consumer)
                .onFailure(new DefaultErrorHandler(this, hidePopupRequestEvent::reset))
                .taskHandlerFactory(taskHandlerFactory)
                .exec();
    }

    public void delete(final List<DocRef> docRefs,
                       final Consumer<BulkActionResult> consumer,
                       final TaskHandlerFactory taskHandlerFactory) {
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.delete(new ExplorerServiceDeleteRequest(docRefs)))
                .onSuccess(consumer)
                .taskHandlerFactory(taskHandlerFactory)
                .exec();
    }

    private void setAsFavourite(final DocRef docRef,
                                final boolean setFavourite,
                                final TaskHandlerFactory taskHandlerFactory) {
        if (setFavourite) {
            restFactory
                    .create(EXPLORER_FAV_RESOURCE)
                    .call(res -> res.createUserFavourite(docRef))
                    .onSuccess(result -> RefreshExplorerTreeEvent.fire(DocumentPluginEventManager.this))
                    .taskHandlerFactory(taskHandlerFactory)
                    .exec();
        } else {
            restFactory
                    .create(EXPLORER_FAV_RESOURCE)
                    .call(res -> res.deleteUserFavourite(docRef))
                    .onSuccess(result -> RefreshExplorerTreeEvent.fire(DocumentPluginEventManager.this))
                    .taskHandlerFactory(taskHandlerFactory)
                    .exec();
        }
    }

    public void open(final DocRef docRef,
                     final boolean forceOpen,
                     final boolean fullScreen,
                     final TaskHandlerFactory taskHandlerFactory) {
        final DocumentPlugin<?> documentPlugin = documentPluginRegistry.get(docRef.getType());
        if (documentPlugin != null) {
            // Decorate the DocRef with its name from the info service (required by the doc presenter)
            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(res -> res.decorate(docRef))
                    .onSuccess(decoratedDocRef -> {
                        if (decoratedDocRef != null) {
                            documentPlugin.open(decoratedDocRef, forceOpen, fullScreen,
                                    new DefaultTaskListener(this));
                            highlight(decoratedDocRef, explorerListener);
                        }
                    })
                    .taskHandlerFactory(taskHandlerFactory)
                    .exec();
        } else {
            throw new IllegalArgumentException("Document type '" + docRef.getType() + "' not registered");
        }
    }

    /**
     * Highlights the supplied document item in the explorer tree.
     */
    public void highlight(final ExplorerNode explorerNode) {
        HighlightExplorerNodeEvent.fire(DocumentPluginEventManager.this, explorerNode);
    }

    public void highlight(final DocRef docRef,
                          final TaskHandlerFactory taskHandlerFactory) {
        // Obtain the Explorer node for the provided DocRef
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.getFromDocRef(docRef))
                .onSuccess(this::highlight)
                .taskHandlerFactory(taskHandlerFactory)
                .exec();
    }

    private List<ExplorerNode> getExplorerNodeListWithPermission(
            final Map<ExplorerNode, ExplorerNodePermissions> documentPermissionMap,
            final String permission,
            final boolean includeSystemNodes) {
        final List<ExplorerNode> list = new ArrayList<>();
        for (final Map.Entry<ExplorerNode, ExplorerNodePermissions> entry : documentPermissionMap.entrySet()) {
            if ((includeSystemNodes || !DocumentTypes.isSystem(entry.getKey().getType()))
                    && entry.getValue().hasDocumentPermission(permission)) {
                list.add(entry.getKey());
            }
        }

        list.sort(Comparator.comparing(HasDisplayValue::getDisplayValue));
        return list;
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        super.onReveal(event);

//        final FutureImpl<List<Item>> future = new FutureImpl<>();
//        final List<ExplorerNode> selectedItems = getSelectedItems();
//        final boolean singleSelection = selectedItems.size() == 1;
//        final ExplorerNode primarySelection = getPrimarySelection();
//
//        fetchPermissions(selectedItems,
//                documentPermissionMap -> documentTypeCache.fetch(documentTypes -> {
//                    final List<Item> menuItems = new ArrayList<>();
//
////                    // Only allow the new menu to appear if we have a single selection.
////                    addNewMenuItem(menuItems,
////                            singleSelection,
////                            documentPermissionMap,
////                            primarySelection,
////                            documentTypes);
////                    menuItems.add(createCloseMenuItem(isTabItemSelected(selectedTab)));
//                    menuItems.add(createCloseAllMenuItem(isTabItemSelected(selectedTab)));
////                    menuItems.add(new Separator(5));
////                    menuItems.add(createSaveMenuItem(6, isDirty(selectedTab)));
//                    menuItems.add(createSaveAllMenuItem(8, hasSaveRegistry.isDirty()));
////                    menuItems.add(new Separator(9));
////                    addModifyMenuItems(menuItems, singleSelection, documentPermissionMap);
//
//                    future.setResult(menuItems);
//                }));
//
//        // Add menu bar item menu.
//        event.getMenuItems()
//                .addMenuItem(MenuKeys.MAIN_MENU, new IconParentMenuItem.Builder()
//                        .priority(11)
//                        .text("Item")
//                        .children(future)
//                        .build());
    }

    private Future<List<Item>> getNewMenuItems(final ExplorerNode explorerNode) {
        final FutureImpl<List<Item>> future = new FutureImpl<>();

        List<ExplorerNode> explorerNodes = Collections.emptyList();
        if (explorerNode != null) {
            explorerNodes = Collections.singletonList(explorerNode);
        }

        fetchPermissions(explorerNodes, documentPermissions ->
                documentTypeCache.fetch(documentTypes -> {
                    if (documentPermissions.containsKey(explorerNode)) {
                        future.setResult(createNewMenuItems(explorerNode,
                                documentPermissions.get(explorerNode),
                                documentTypes));
                    } else {
                        future.setResult(Collections.emptyList());
                    }
                }, explorerListener), explorerListener);
        return future;
    }

    private void fetchPermissions(final List<ExplorerNode> explorerNodes,
                                  final Consumer<Map<ExplorerNode, ExplorerNodePermissions>> consumer,
                                  final TaskHandlerFactory taskHandlerFactory) {
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.fetchExplorerPermissions(explorerNodes))
                .onSuccess(response -> {
                    final Map<ExplorerNode, ExplorerNodePermissions> map = response.stream().collect(Collectors.toMap(
                            ExplorerNodePermissions::getExplorerNode,
                            Function.identity()));
                    consumer.accept(map);
                })
                .taskHandlerFactory(taskHandlerFactory)
                .exec();
    }

    private boolean addFavouritesMenuItem(final List<Item> menuItems,
                                          final boolean singleSelection,
                                          final int priority) {
        final ExplorerNode primarySelection = getPrimarySelection();

        // Add the favourites menu item if an item is selected, and it's not a root-level node or a favourite folder
        // item
        if (singleSelection && primarySelection != null && primarySelection.getDepth() > 0) {
            final boolean isFavourite = primarySelection.hasNodeFlag(NodeFlag.FAVOURITE);
            menuItems.add(new IconMenuItem.Builder()
                    .priority(priority)
                    .icon(isFavourite
                            ? SvgImage.FAVOURITES_OUTLINE
                            : SvgImage.FAVOURITES)
                    .text(isFavourite
                            ? "Remove from Favourites"
                            : "Add to Favourites")
                    .command(() -> {
                        toggleFavourite(primarySelection.getDocRef(), isFavourite);
                        selectionModel.clear();
                    })
                    .build());
            return true;
        }
        return false;
    }

    private void toggleFavourite(final DocRef docRef, final boolean isFavourite) {
        SetDocumentAsFavouriteEvent.fire(DocumentPluginEventManager.this, docRef, !isFavourite);
    }

    private void addNewMenuItem(final List<Item> menuItems,
                                final boolean singleSelection,
                                final Map<ExplorerNode, ExplorerNodePermissions> documentPermissionMap,
                                final ExplorerNode primarySelection,
                                final DocumentTypes documentTypes) {
        boolean enabled = false;
        List<Item> children = null;

        // Only allow the new menu to appear if we have a single selection.
        if (singleSelection && primarySelection != null) {
            // Add 'New' menu item.
            final ExplorerNodePermissions documentPermissions = documentPermissionMap.get(primarySelection);
            children = createNewMenuItems(primarySelection, documentPermissions, documentTypes);
            enabled = !children.isEmpty();
        }

        final Item newItem = new IconParentMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.ADD)
                .text("New")
                .children(children)
                .enabled(enabled)
                .build();
        menuItems.add(newItem);
        menuItems.add(new Separator(2));
    }

    private List<Item> createNewMenuItems(final ExplorerNode explorerNode,
                                          final ExplorerNodePermissions documentPermissions,
                                          final DocumentTypes documentTypes) {
        final List<Item> children = new ArrayList<>();
        //noinspection SimplifyStreamApiCallChains
        final List<DocumentType> availableTypes = documentTypes.getTypes()
                .stream()
                .filter(documentPermissions::hasCreatePermission)
                .collect(Collectors.toList());

        // Group all document types
        final Map<DocumentTypeGroup, List<DocumentType>> groupedTypes = availableTypes.stream()
                .collect(Collectors.groupingBy(DocumentType::getGroup, Collectors.toList()));

        // Add each type group as a sorted list of menu items
        groupedTypes
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getPriority()))
                .forEach(entry -> {
                    final DocumentTypeGroup group = entry.getKey();
                    final List<DocumentType> types = entry.getValue();
                    if (DocumentTypeGroup.STRUCTURE.equals(group) && types != null && types.size() == 1) {
                        children.add(createIconMenuItemFromDocumentType(types.get(0), explorerNode));
                        if (groupedTypes.keySet().size() > 1) {
                            children.add(new Separator(1));
                        }
                    } else if (types != null && !types.isEmpty()) {
                        final List<Item> grandChildren = types.stream()
                                .sorted(Comparator.comparing(DocumentType::getDisplayType))
                                .map(type -> (Item) createIconMenuItemFromDocumentType(type, explorerNode))
                                .collect(Collectors.toList());

                        // Add the group level item with its children
                        children.add(new IconParentMenuItem.Builder()
                                .text(group.getDisplayName())
                                .children(grandChildren)
                                .build());
                    }
                });
        return children;
    }

    private void fireShowCreateDocumentDialogEvent(final DocumentType documentType,
                                                   final ExplorerNode explorerNode) {
        final Consumer<ExplorerNode> newDocumentConsumer = newDocNode -> {
            final DocRef docRef = newDocNode.getDocRef();
            // Open the document in the content pane.
            final DocumentPlugin<?> plugin = documentPluginRegistry.get(docRef.getType());
            if (plugin != null) {
                plugin.open(docRef, true, false, new DefaultTaskListener(this));
            }
        };

        ShowCreateDocumentDialogEvent.fire(
                DocumentPluginEventManager.this,
                "New " + documentType.getDisplayType(),
                explorerNode,
                documentType.getType(),
                "",
                true,
                newDocumentConsumer);
    }

    private IconMenuItem createIconMenuItemFromDocumentType(
            final DocumentType documentType,
            final ExplorerNode explorerNode) {

        return new IconMenuItem.Builder()
                .priority(1)
                .icon(documentType.getIcon())
                .text(documentType.getDisplayType())
                .command(() ->
                        fireShowCreateDocumentDialogEvent(documentType, explorerNode))
                .action(KeyBinding.getCreateActionByType(documentType.getType()).orElse(null))
                .build();
    }

    private void addModifyMenuItems(final List<Item> menuItems,
                                    final boolean singleSelection,
                                    final Map<ExplorerNode, ExplorerNodePermissions> documentPermissionMap) {
        final List<ExplorerNode> readableItems = getExplorerNodeListWithPermission(documentPermissionMap,
                DocumentPermissionNames.READ,
                false);
        final ExplorerNode singleReadableItem = readableItems.stream()
                .findFirst()
                .orElse(null);
        final List<ExplorerNode> updatableItems = getExplorerNodeListWithPermission(documentPermissionMap,
                DocumentPermissionNames.UPDATE,
                false);
        final List<ExplorerNode> deletableItems = getExplorerNodeListWithPermission(documentPermissionMap,
                DocumentPermissionNames.DELETE,
                false);

        // Actions allowed based on permissions of selection
        final boolean allowRead = !readableItems.isEmpty();
        final boolean allowUpdate = !updatableItems.isEmpty();
        final boolean allowDelete = !deletableItems.isEmpty();
        final boolean isInfoEnabled = singleSelection & allowRead;
        final boolean isRemoveTagsEnabled = updatableItems.size() > 1;

        // Feeds are a special case so can't be copied or renamed, see https://github.com/gchq/stroom/issues/3048
        final boolean hasFeed = readableItems.stream()
                .anyMatch(item -> FeedDoc.DOCUMENT_TYPE.equals(item.getType()));

        // Feeds are a special case so can't be renamed, see https://github.com/gchq/stroom/issues/2912
        final boolean isRenameEnabled = singleSelection
                && allowUpdate
                && !hasFeed;

        // Feeds are a special case so can't be copied, see https://github.com/gchq/stroom/issues/3048
        final boolean isCopyEnabled = allowRead && !hasFeed;

        final boolean wasAdded = addFavouritesMenuItem(menuItems, singleSelection, 10);
        if (wasAdded) {
            menuItems.add(new Separator(12));
        }

        menuItems.add(createInfoMenuItem(singleReadableItem, 20, isInfoEnabled, explorerListener));
        menuItems.add(createEditOrAddTagsMenuItem(updatableItems, 21, allowUpdate));
        if (updatableItems.size() > 1) {
            menuItems.add(createRemoveTagsMenuItem(updatableItems, 22, isRemoveTagsEnabled));
        }
        menuItems.add(createCopyMenuItem(readableItems, 23, isCopyEnabled));

        menuItems.add(createCopyAsMenuItem(readableItems, 24));

        menuItems.add(createMoveMenuItem(updatableItems, 25, allowUpdate));
        menuItems.add(createRenameMenuItem(updatableItems, 26, isRenameEnabled));
        menuItems.add(createDeleteMenuItem(deletableItems, 27, allowDelete));

        if (securityContext.hasAppPermission(PermissionNames.IMPORT_CONFIGURATION)) {
            menuItems.add(createImportMenuItem(28));
        }
        if (securityContext.hasAppPermission(PermissionNames.EXPORT_CONFIGURATION)) {
            menuItems.add(createExportMenuItem(29, readableItems));
        }

        // Only allow users to change permissions if they have a single item selected.
        if (singleSelection) {
            final List<ExplorerNode> ownedItems = getExplorerNodeListWithPermission(documentPermissionMap,
                    DocumentPermissionNames.OWNER,
                    true);
            if (ownedItems.size() == 1) {
                menuItems.add(new Separator(30));
                menuItems.add(createShowDependenciesFromMenuItem(ownedItems.get(0), 31));
                menuItems.add(createShowDependantsMenuItem(ownedItems.get(0), 32));
                menuItems.add(new Separator(33));
                menuItems.add(createPermissionsMenuItem(ownedItems.get(0), 34, true));
            }
        }
    }

    private MenuItem createCloseMenuItem(final int priority, final TabData selectedTab) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.CLOSE)
                .iconColour(IconColour.RED)
                .text("Close")
                .action(Action.ITEM_CLOSE)
                .enabled(isTabItemSelected(selectedTab))
                .command(() -> RequestCloseTabEvent.fire(DocumentPluginEventManager.this, selectedTab))
                .build();
    }

    private MenuItem createCloseOthersMenuItem(final int priority, final TabData selectedTab) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.CLOSE)
                .iconColour(IconColour.RED)
                .text("Close Others")
                .enabled(isTabItemSelected(selectedTab))
                .command(() -> RequestCloseOtherTabsEvent.fire(DocumentPluginEventManager.this, selectedTab))
                .build();
    }

    private MenuItem createCloseSavedMenuItem(final int priority, final TabData selectedTab) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.CLOSE)
                .iconColour(IconColour.RED)
                .text("Close Saved")
                .enabled(isTabItemSelected(selectedTab))
                .command(() -> RequestCloseSavedTabsEvent.fire(DocumentPluginEventManager.this))
                .build();
    }

    private MenuItem createCloseAllMenuItem(final int priority, final TabData selectedTab) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.CLOSE)
                .iconColour(IconColour.RED)
                .text("Close All")
                .action(Action.ITEM_CLOSE_ALL)
                .enabled(isTabItemSelected(selectedTab))
                .command(() -> RequestCloseAllTabsEvent.fire(DocumentPluginEventManager.this))
                .build();
    }

    private MenuItem createSaveMenuItem(final int priority, final TabData selectedTab) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.SAVE)
                .text("Save")
                .action(Action.ITEM_SAVE)
                .enabled(isDirty(selectedTab))
                .command(() -> {
                    if (isDirty(selectedTab)) {
                        final HasSave hasSave = (HasSave) selectedTab;
                        hasSave.save();
                    }
                })
                .build();
    }

    private MenuItem createSaveAllMenuItem(final int priority) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.SAVE)
                .text("Save All")
                .action(Action.ITEM_SAVE_ALL)
                .enabled(hasSaveRegistry.isDirty())
                .command(hasSaveRegistry::save)
                .build();
    }

    private MenuItem createLocateMenuItem(final int priority, final TabData selectedTab) {
        final DocRef selectedDoc = getSelectedDoc(selectedTab);
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.LOCATE)
                .text("Locate in Explorer")
                .action(Action.LOCATE)
                .enabled(selectedDoc != null)
                .command(() -> {
                    if (selectedDoc != null) {
                        LocateDocEvent.fire(DocumentPluginEventManager.this, selectedDoc);
                    }
                })
                .build();
    }

    private MenuItem addToFavouritesMenuItem(final int priority, final TabData selectedTab) {
        final DocRef selectedDoc = getSelectedDoc(selectedTab);
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.FAVOURITES)
                .text("Add to Favourites")
                .enabled(selectedDoc != null)
                .command(() -> {
                    if (selectedDoc != null) {
                        SetDocumentAsFavouriteEvent.fire(
                                DocumentPluginEventManager.this, selectedDoc, true);
                    }
                })
                .build();
    }

    private DocRef getSelectedDoc(final TabData selectedTab) {
        if (selectedTab instanceof DocumentTabData) {
            final DocumentTabData documentTabData = (DocumentTabData) selectedTab;
            return documentTabData.getDocRef();
        }
        return null;
    }

    private MenuItem createInfoMenuItem(final ExplorerNode explorerNode,
                                        final int priority,
                                        final boolean enabled,
                                        final TaskHandlerFactory taskHandlerFactory) {
        final Command command;
        if (enabled && explorerNode != null) {
            command = () -> {
                // Should only be one item as info is not supported for multi selection
                // in the tree
                restFactory
                        .create(EXPLORER_RESOURCE)
                        .method(res -> res.info(explorerNode.getDocRef()))
                        .onSuccess(explorerNodeInfo -> {
                            ShowInfoDocumentDialogEvent.fire(
                                    DocumentPluginEventManager.this,
                                    explorerNodeInfo);
                        })
                        .onFailure(this::handleFailure)
                        .taskHandlerFactory(taskHandlerFactory)
                        .exec();
            };
        } else {
            command = null;
        }

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.INFO)
                .text("Info")
                .enabled(enabled && explorerNode != null)
                .command(command)
                .build();
    }

    private void handleFailure(final RestError t) {
        AlertEvent.fireError(
                DocumentPluginEventManager.this,
                t.getMessage(),
                null);
    }

    private MenuItem createEditOrAddTagsMenuItem(final List<ExplorerNode> explorerNodes,
                                                 final int priority,
                                                 final boolean enabled) {
        final Command command = enabled && explorerNodes != null
                ? () -> ShowEditNodeTagsDialogEvent.fire(DocumentPluginEventManager.this, explorerNodes)
                : null;

        final String text = GwtNullSafe.size(explorerNodes) > 1
                ? "Add Tags"
                : "Edit Tags";

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.TAGS)
                .text(text)
                .enabled(enabled && explorerNodes != null)
                .command(command)
                .build();
    }

    private MenuItem createRemoveTagsMenuItem(final List<ExplorerNode> explorerNodes,
                                              final int priority,
                                              final boolean enabled) {
        final Command command = enabled && explorerNodes != null
                ? () -> ShowRemoveNodeTagsDialogEvent.fire(DocumentPluginEventManager.this, explorerNodes)
                : null;

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.TAGS)
                .text("Remove Tags")
                .enabled(enabled && explorerNodes != null)
                .command(command)
                .build();
    }

    private MenuItem createCopyLinkMenuItem(final ExplorerNode explorerNode, final int priority) {
        // Generate a URL that can be used to open a new Stroom window with the target document loaded
        final String docUrl = Window.Location.createUrlBuilder()
                .setPath("/")
                .setParameter(UrlParameters.ACTION, UrlParameters.OPEN_DOC_ACTION)
                .setParameter(UrlParameters.DOC_TYPE_QUERY_PARAM, explorerNode.getType())
                .setParameter(UrlParameters.DOC_UUID_QUERY_PARAM, explorerNode.getUuid())
                .buildString();

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.SHARE)
                .text("Copy Link to Clipboard")
                .command(() -> ClipboardUtil.copy(docUrl))
                .build();
    }

    private MenuItem createCopyMenuItem(final List<ExplorerNode> explorerNodeList,
                                        final int priority,
                                        final boolean enabled) {
        final Command command = () -> ShowCopyDocumentDialogEvent.fire(DocumentPluginEventManager.this,
                explorerNodeList);

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.COPY)
                .text("Copy")
                .enabled(enabled)
                .command(command)
                .build();
    }

    private MenuItem createCopyAsMenuItem(final List<ExplorerNode> explorerNodes,
                                          final int priority) {
        List<Item> children = createCopyAsChildMenuItems(explorerNodes);

        return new IconParentMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.COPY)
                .text("Copy As")
                .children(children)
                .enabled(true)
                .build();
    }

    private List<Item> createCopyAsChildMenuItems(final List<ExplorerNode> explorerNodes) {
        final List<Item> children = new ArrayList<>();
        final int count = explorerNodes.size();
        int priority = 1;
        if (count == 1) {
            children.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.COPY)
                    .text("Copy Name to Clipboard")
                    .enabled(true)
                    .command(() -> copyAs(explorerNodes, ExplorerNode::getName, "\n"))
                    .build());

            children.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.COPY)
                    .text("Copy UUID to Clipboard")
                    .enabled(true)
                    .command(() -> copyAs(explorerNodes, ExplorerNode::getUuid, "\n"))
                    .build());
        } else if (count > 1) {
            children.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.COPY)
                    .text("Copy Names to Clipboard (lines)")
                    .enabled(true)
                    .command(() -> copyAs(explorerNodes, ExplorerNode::getName, "\n"))
                    .build());
            children.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.COPY)
                    .text("Copy Names to Clipboard (comma delimited)")
                    .enabled(true)
                    .command(() -> copyAs(explorerNodes, ExplorerNode::getName, ","))
                    .build());
            children.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.COPY)
                    .text("Copy UUIDs to Clipboard (lines)")
                    .enabled(true)
                    .command(() -> copyAs(explorerNodes, ExplorerNode::getUuid, "\n"))
                    .build());
            children.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.COPY)
                    .text("Copy UUIDs to Clipboard (comma delimited)")
                    .enabled(true)
                    .command(() -> copyAs(explorerNodes, ExplorerNode::getUuid, ","))
                    .build());
        }

        if (explorerNodes.size() == 1) {
            children.add(createCopyLinkMenuItem(explorerNodes.get(0), priority++));
        }

        return children;
    }

    private void copyAs(final List<ExplorerNode> nodes,
                        final Function<ExplorerNode, String> extractor,
                        final String delimter) {
        final String value;
        if (nodes.isEmpty()) {
            value = "";
        } else if (nodes.size() == 1) {
            value = GwtNullSafe.getOrElse(nodes.get(0), extractor, "");
        } else {
            value = nodes.stream()
                    .map(extractor)
                    .collect(Collectors.joining(delimter));
        }
        if (!GwtNullSafe.isBlankString(value)) {
            ClipboardUtil.copy(value);
        }
    }

    private MenuItem createMoveMenuItem(final List<ExplorerNode> explorerNodeList,
                                        final int priority,
                                        final boolean enabled) {
        final Command command = () -> ShowMoveDocumentDialogEvent.fire(DocumentPluginEventManager.this,
                explorerNodeList);

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.MOVE)
                .text("Move")
                .enabled(enabled)
                .command(command)
                .build();
    }

    private MenuItem createRenameMenuItem(final List<ExplorerNode> explorerNodeList,
                                          final int priority,
                                          final boolean enabled) {
        final Command command = () ->
                renameItems(explorerNodeList);

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.EDIT)
                .text("Rename")
                .enabled(enabled)
                .command(command)
                .build();
    }

    private MenuItem createDeleteMenuItem(final List<ExplorerNode> explorerNodeList,
                                          final int priority,
                                          final boolean enabled) {
        final Command command = () ->
                deleteItems(explorerNodeList, explorerListener);

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.DELETE)
                .text("Delete")
                .enabled(enabled)
                .command(command)
                .build();
    }

    private MenuItem createPermissionsMenuItem(final ExplorerNode explorerNode,
                                               final int priority,
                                               final boolean enabled) {
        final Command command = () -> {
            if (explorerNode != null) {
                ShowPermissionsDialogEvent.fire(DocumentPluginEventManager.this, explorerNode);
            }
        };

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.LOCKED)
                .text("Permissions")
                .enabled(enabled)
                .command(command)
                .build();
    }

    private MenuItem createImportMenuItem(final int priority) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.UPLOAD)
                .text("Import")
                .command(() -> ImportConfigEvent.fire(DocumentPluginEventManager.this))
                .build();
    }

    private MenuItem createExportMenuItem(final int priority,
                                          final List<ExplorerNode> readableItems) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.DOWNLOAD)
                .text("Export")
                .command(() -> ExportConfigEvent.fire(DocumentPluginEventManager.this, readableItems))
                .build();
    }

    private MenuItem createShowDependantsMenuItem(final ExplorerNode explorerNode, final int priority) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.DEPENDENCIES)
                .text("Dependants")
                .command(() -> ShowDocRefDependenciesEvent.fire(
                        DocumentPluginEventManager.this,
                        explorerNode.getDocRef(),
                        DependencyType.DEPENDANT))
                .build();
    }

    private MenuItem createShowDependenciesFromMenuItem(final ExplorerNode explorerNode, final int priority) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.DEPENDENCIES)
                .text("Dependencies")
                .command(() -> ShowDocRefDependenciesEvent.fire(
                        DocumentPluginEventManager.this,
                        explorerNode.getDocRef(),
                        DependencyType.DEPENDENCY))
                .build();
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
            @SuppressWarnings("PatternVariableCanBeUsed") // cos GWT
            final HasSave hasSave = (HasSave) tabData;
            return hasSave.isDirty();
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
