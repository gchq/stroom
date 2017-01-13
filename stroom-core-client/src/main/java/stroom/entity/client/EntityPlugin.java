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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.PresenterWidget;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.presenter.ConfirmCallback;
import stroom.content.client.event.SelectContentTabEvent;
import stroom.core.client.ContentManager;
import stroom.core.client.ContentManager.CloseCallback;
import stroom.core.client.ContentManager.CloseHandler;
import stroom.core.client.presenter.Plugin;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.EntityEditPresenter;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.EntityServiceCopyAction;
import stroom.entity.shared.EntityServiceCreateAction;
import stroom.entity.shared.EntityServiceDeleteAction;
import stroom.entity.shared.EntityServiceLoadAction;
import stroom.entity.shared.EntityServiceMoveAction;
import stroom.entity.shared.EntityServiceSaveAction;
import stroom.entity.shared.HasFolder;
import stroom.entity.shared.NamedEntity;
import stroom.explorer.client.event.HighlightExplorerItemEvent;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.explorer.shared.EntityData;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.widget.popup.client.event.HidePopupEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class EntityPlugin<E extends NamedEntity> extends Plugin {
    private final ClientDispatchAsync dispatcher;
    private final ClientSecurityContext securityContext;
    private final Map<DocRef, EntityTabData> entityToTabDataMap = new HashMap<>();
    private final Map<EntityTabData, DocRef> tabDataToEntityMap = new HashMap<>();
    private final ContentManager contentManager;
    private final EntityPluginEventManager entityPluginEventManager;

    @Inject
    public EntityPlugin(final EventBus eventBus, final ClientDispatchAsync dispatcher,
            final ClientSecurityContext securityContext, final ContentManager contentManager,
            final EntityPluginEventManager entityPluginEventManager) {
        super(eventBus);
        this.contentManager = contentManager;
        this.entityPluginEventManager = entityPluginEventManager;

        this.dispatcher = dispatcher;
        this.securityContext = securityContext;

        // Register this plugin.
        entityPluginEventManager.registerPlugin(getType(), this);
    }

    /**
     * 1. This method will create a new entity and show it in the content pane.
     */
    public void createEntity(final Presenter<?, ?> popup, final DocRef folder, final String entityName) {
        create(getType(), entityName, folder, new CreateCallback() {
            @Override
            public void onCreate(final DocRef docRef) {
                // Hide the create entity presenter.
                HidePopupEvent.fire(EntityPlugin.this, popup);

                highlight(docRef);

                // Open the item in the content pane.
                open(docRef, true);
            }
        });
    }

    /**
     * 4. This method will open an entity and show it in the content pane.
     */
    @SuppressWarnings("unchecked")
    public EntityEditPresenter<?, E> open(final DocRef docRef, final boolean forceOpen) {
        EntityEditPresenter<?, E> presenter = null;

        final EntityTabData existing = entityToTabDataMap.get(docRef);
        // If we already have a tab item for this entity then make sure it is
        // visible.
        if (existing != null) {
            // Start spinning.
            TaskStartEvent.fire(this, "Opening entity");

            // Tell the content presenter to select this existing tab.
            SelectContentTabEvent.fire(this, existing);

            // Stop spinning.
            TaskEndEvent.fire(EntityPlugin.this);

            if (existing instanceof EntityEditPresenter) {
                presenter = (EntityEditPresenter<?, E>) existing;
            }

        } else if (forceOpen) {
            // Start spinning.
            TaskStartEvent.fire(this, "Opening entity");

            // If the item isn't already open but we are forcing it open then,
            // create a new presenter and register it as open.
            final EntityEditPresenter<?, E> entityEditPresenter = (EntityEditPresenter<?, E>) createEditor();
            presenter = entityEditPresenter;

            if (entityEditPresenter instanceof EntityTabData) {
                final EntityTabData tabData = (EntityTabData) entityEditPresenter;

                // Register the tab as being open.
                entityToTabDataMap.put(docRef, tabData);
                tabDataToEntityMap.put(tabData, docRef);

                // Load the entity and show the tab.
                load(docRef, new LoadCallback<E>() {
                    @Override
                    public void onLoad(final E entity) {
                        // Read the newly loaded entity.
                        entityEditPresenter.read(entity);

                        // Open the tab.
                        final CloseHandler closeHandler = new EntityCloseHandler(tabData);
                        contentManager.open(closeHandler, tabData, entityEditPresenter);

                        // Stop spinning.
                        TaskEndEvent.fire(EntityPlugin.this);
                    }
                });

            } else {
                // Stop spinning.
                TaskEndEvent.fire(EntityPlugin.this);
            }
        }

        return presenter;
    }

    /**
     * 5. This method will save an entity.
     */
    @SuppressWarnings("unchecked")
    public void save(final EntityTabData tabData) {
        if (tabData != null && tabData instanceof EntityEditPresenter<?, ?>) {
            final EntityEditPresenter<?, E> presenter = (EntityEditPresenter<?, E>) tabData;
            if (presenter.isDirty()) {
                final E entity = presenter.getEntity();
                presenter.write(entity);
                save(entity, new SaveCallback<E>() {
                    @Override
                    public void onSave(final E entity) {
                        presenter.read(entity);
                    }
                });
            }
        }
    }

    /**
     * 6. This method will save an entity as a copy with a different name.
     */
    @SuppressWarnings("unchecked")
    public void copy(final PresenterWidget<?> dialog, final EntityTabData tabData, final String name) {
        if (tabData != null && tabData instanceof EntityEditPresenter<?, ?>) {
            final EntityEditPresenter<?, E> presenter = (EntityEditPresenter<?, E>) tabData;
            final E entity = presenter.getEntity();
            presenter.write(presenter.getEntity());

            final DocRef oldEntityReference = DocRefUtil.create(entity);

            DocRef folder = null;
            if (entity instanceof HasFolder) {
                folder = DocRefUtil.create(((HasFolder) entity).getFolder());
            }

            copy(entity, folder, name, new SaveCallback<E>() {
                @Override
                public void onSave(final E entity) {
                    // Hide the save as presenter.
                    HidePopupEvent.fire(EntityPlugin.this, dialog);

                    // Create an entity item so we can open it in the editor and
                    // select it in the explorer tree.
                    final DocRef docRef = DocRefUtil.create(entity);
                    highlight(docRef);

                    // The entity we had open before is now effectively closed
                    // and the new one open so record this fact so that we can
                    // open the old one again and the new one won't open twice.
                    entityToTabDataMap.remove(oldEntityReference);
                    entityToTabDataMap.put(docRef, tabData);
                    tabDataToEntityMap.remove(tabData);
                    tabDataToEntityMap.put(tabData, docRef);

                    // Update the item in the content pane.
                    presenter.read(entity);
                }
            });
        }
    }

    /**
     * 7. This method will save an entity as a copy with a different name.
     */
    public void saveAll() {
        for (final EntityTabData tabData : tabDataToEntityMap.keySet()) {
            save(tabData);
        }
    }

    // /**
    // * 2. This method will close a tab in the content pane.
    // */
    // @SuppressWarnings("unchecked")
    // public void close(final EntityTabData tabData,
    // final boolean logoffAfterClose) {
    // if (tabData != null && tabData instanceof EntityEditPresenter<?, ?>) {
    // final EntityEditPresenter<?, E> presenter = (EntityEditPresenter<?, E>)
    // tabData;
    // if (presenter.isDirty()) {
    // ConfirmEvent
    // .fire(EntityPlugin.this,
    // presenter.getEntity().getType()
    // + " '"
    // + presenter.getEntity().getName()
    // + "' has unsaved changes. Are you sure you want to close this item?",
    // new ConfirmCallback() {
    // @Override
    // public void onResult(final boolean result) {
    // if (result) {
    // presenter.closing();
    // removeTab(tabData, logoffAfterClose);
    // }
    // }
    // });
    // } else {
    // presenter.closing();
    // removeTab(tabData, logoffAfterClose);
    // }
    // }
    // }
    //
    // /**
    // * 3. This method will close all open tabs in the content pane.
    // */
    // public void closeAll(final boolean logoffAfterClose) {
    // final List<EntityTabData> tabs = new ArrayList<EntityTabData>(
    // tabDataToEntityMap.keySet());
    // for (final EntityTabData tabData : tabs) {
    // close(tabData, logoffAfterClose);
    // }
    // }

    /**
     * 8.1. This method will copy an entity.
     */
    public void copyEntity(final PresenterWidget<?> popup, final DocRef docRef, final DocRef folder,
            final String name) {
        // We need to load the entity here as it might not have been loaded yet.
        load(docRef, new LoadCallback<E>() {
            @Override
            public void onLoad(final E entity) {
                copyEntity(popup, entity, folder, name);
            }
        });
    }

    private void copyEntity(final PresenterWidget<?> popup, final E entity, final DocRef folder, final String name) {
        copy(entity, folder, name, new SaveCallback<E>() {
            @Override
            public void onSave(final E entity) {
                // Hide the copy entity presenter.
                HidePopupEvent.fire(EntityPlugin.this, popup);

                // Create an entity item so we can open it in the editor and
                // select it in the explorer tree.
                final DocRef item = DocRefUtil.create(entity);
                highlight(item);
            }
        });
    }

    /**
     * 8.2. This method will move an entity.
     */
    @SuppressWarnings("unchecked")
    public void moveEntity(final PresenterWidget<?> popup, final DocRef document, final DocRef folder) {
        // Find out if we currently have the entity open.
        final EntityTabData tabData = entityToTabDataMap.get(document);
        if (tabData != null && tabData instanceof EntityEditPresenter<?, ?>) {
            final EntityEditPresenter<?, E> editPresenter = (EntityEditPresenter<?, E>) tabData;
            // Find out if the existing item is dirty.
            if (editPresenter.isDirty()) {
                ConfirmEvent.fire(EntityPlugin.this,
                        "You must save changes to " + document.getType() + " '"
                                + document.getDisplayValue()
                                + "' before it can be moved. Would you like to save the current changes now?",
                        new ConfirmCallback() {
                            @Override
                            public void onResult(final boolean result) {
                                if (result) {
                                    editPresenter.write(editPresenter.getEntity());
                                    moveEntity(popup, editPresenter.getEntity(), folder, tabData, editPresenter);
                                }
                            }
                        });
            } else {
                moveEntity(popup, editPresenter.getEntity(), folder, tabData, editPresenter);
            }
        } else {
            // We need to load the entity here as it hasn't been loaded yet.
            load(document, new LoadCallback<E>() {
                @Override
                public void onLoad(final E entity) {
                    moveEntity(popup, entity, folder, null, null);
                }
            });
        }
    }

    private void moveEntity(final PresenterWidget<?> popup, final E entity, final DocRef folder,
            final EntityTabData tabData, final EntityEditPresenter<?, E> editPresenter) {
        move(entity, folder, new SaveCallback<E>() {
            @Override
            public void onSave(final E entity) {
                // Hide the copy entity presenter.
                HidePopupEvent.fire(EntityPlugin.this, popup);

                // Create an entity item so we can open it in the editor and
                // select it in the explorer tree.
                final DocRef docRef = DocRefUtil.create(entity);
                highlight(docRef);

                if (editPresenter != null) {
                    editPresenter.read(entity);
                }
            }
        });
    }

    /**
     * 9. This method will rename an entity.
     */
    @SuppressWarnings("unchecked")
    public void renameEntity(final PresenterWidget<?> dialog, final DocRef docRef,
            final String entityName) {
        // Find out if we currently have the entity open.
        final EntityTabData tabData = entityToTabDataMap.get(docRef);
        if (tabData != null && tabData instanceof EntityEditPresenter<?, ?>) {
            final EntityEditPresenter<?, E> editPresenter = (EntityEditPresenter<?, E>) tabData;
            // Find out if the existing item is dirty.
            if (editPresenter.isDirty()) {
                ConfirmEvent.fire(EntityPlugin.this,
                        "You must save changes to " + docRef.getType() + " '"
                                + docRef.getDisplayValue()
                                + "' before it can be renamed. Would you like to save the current changes now?",
                        new ConfirmCallback() {
                            @Override
                            public void onResult(final boolean result) {
                                if (result) {
                                    editPresenter.write(editPresenter.getEntity());
                                    renameEntity(dialog, editPresenter.getEntity(), entityName, tabData, editPresenter);
                                }
                            }
                        });
            } else {
                renameEntity(dialog, editPresenter.getEntity(), entityName, tabData, editPresenter);
            }
        } else {
            // We need to load the entity here as it hasn't been loaded yet.
            load(docRef, new LoadCallback<E>() {
                @Override
                public void onLoad(final E entity) {
                    renameEntity(dialog, entity, entityName, null, null);
                }
            });
        }
    }

    private void renameEntity(final PresenterWidget<?> popup, final E entity, final String entityName,
            final EntityTabData tabData, final EntityEditPresenter<?, E> editPresenter) {
        entity.setName(entityName);
        save(entity, new SaveCallback<E>() {
            @Override
            public void onSave(final E entity) {
                // Hide the rename entity presenter.
                HidePopupEvent.fire(EntityPlugin.this, popup);

                // Create an entity item so we can open it in the feed editor
                // and select it in the explorer tree.
                final DocRef docRef = DocRefUtil.create(entity);
                highlight(docRef);

                if (editPresenter != null) {
                    editPresenter.read(entity);
                }
            }
        });
    }

    /**
     * 10. This method will delete an entity.
     */
    @SuppressWarnings("unchecked")
    public void deleteEntity(final DocRef docRef) {
        // Find out if we currently have the entity open.
        final EntityTabData tabData = entityToTabDataMap.get(docRef);
        if (tabData != null && tabData instanceof EntityEditPresenter<?, ?>) {
            final EntityEditPresenter<?, E> editPresenter = (EntityEditPresenter<?, E>) tabData;
            // Find out if the existing item is dirty.
            if (editPresenter.isDirty()) {
                ConfirmEvent.fire(EntityPlugin.this,
                        "You have unsaved changed for " + docRef.getType() + " '"
                                + docRef.getDisplayValue() + "'.  Are you sure you want to delete it?",
                        new ConfirmCallback() {
                            @Override
                            public void onResult(final boolean result) {
                                if (result) {
                                    deleteEntity(editPresenter.getEntity(), tabData);
                                }
                            }
                        });
            } else {
                ConfirmEvent.fire(EntityPlugin.this,
                        "You have " + docRef.getType() + " '" + docRef.getDisplayValue()
                                + "' currently open for editing. Are you sure you want to delete it?",
                        new ConfirmCallback() {
                            @Override
                            public void onResult(final boolean result) {
                                if (result) {
                                    deleteEntity(editPresenter.getEntity(), tabData);
                                }
                            }
                        });
            }
        } else {
            ConfirmEvent.fire(EntityPlugin.this, "Are you sure you want to delete " + docRef.getType() + " '"
                    + docRef.getDisplayValue() + "'?", new ConfirmCallback() {
                @Override
                public void onResult(final boolean result) {
                    if (result) {
                        // We need to load the entity here as it hasn't
                        // been loaded yet.
                        load(docRef, new LoadCallback<E>() {
                            @Override
                            public void onLoad(final E entity) {
                                deleteEntity(entity, null);
                            }
                        });
                    }
                }
            });
        }
    }

    /**
     * 11. This method will reload an entity.
     */
    @SuppressWarnings("unchecked")
    public void reload(final DocRef docRef) {
        // Get the existing tab data for this entity.
        final EntityTabData tabData = entityToTabDataMap.get(docRef);
        // If we have an entity edit presenter then reload the entity.
        if (tabData != null && tabData instanceof EntityEditPresenter<?, ?>) {
            final EntityEditPresenter<?, E> presenter = (EntityEditPresenter<?, E>) tabData;

            // Start spinning.
            TaskStartEvent.fire(this, "Reloading entity");

            // Reload the entity.
            load(docRef, new LoadCallback<E>() {
                @Override
                public void onLoad(final E entity) {
                    // Read the reloaded entity.
                    presenter.read(entity);

                    // Stop spinning.
                    TaskEndEvent.fire(EntityPlugin.this);
                }
            });
        }
    }

    private void deleteEntity(final E entity, final EntityTabData tabData) {
        delete(entity, new DeleteCallback<E>() {
            @Override
            public void onDelete(final E entity) {
                if (tabData != null) {
                    // Cleanup reference to this tab data.
                    removeTabData(tabData);
                    contentManager.forceClose(tabData);
                }
                // Refresh the explorer tree so the entity is marked as deleted.
                RefreshExplorerTreeEvent.fire(EntityPlugin.this);
            }
        });
    }

    private void removeTabData(final EntityTabData tabData) {
        final DocRef docRef = tabDataToEntityMap.remove(tabData);
        entityToTabDataMap.remove(docRef);
    }

    /**
     * This method will highlight the supplied entity item in the explorer tree.
     */
    public void highlight(final DocRef docRef) {
        final Set<String> requiredPermissions = new HashSet<>();
        requiredPermissions.add(DocumentPermissionNames.USE);

        // Open up parent items.
        final EntityData entityData = EntityData.create(docRef);
        HighlightExplorerItemEvent.fire(EntityPlugin.this, entityData);
    }

    protected abstract EntityEditPresenter<?, ?> createEditor();

    protected boolean allowNullFolder() {
        return false;
    }

    // @Override
    // public void onReveal(final BeforeRevealMenubarEvent event) {
    // if (securityContext.hasAppPermission(getType(), DocumentPermissionNames.UPDATE)) {
    // final String displayType = getEntityDisplayType();
    // event.getMenuItems().addMenuItem(MenuKeys.NEW_MENU,
    // new CommandMenuItem(getMenuPositon(), displayType, null, true, new
    // Command() {
    // @Override
    // public void execute() {
    // createEntity(displayType);
    // }
    // }));
    // }
    // }

//    protected void createEntity(final String type, final String displayType) {
//        final EntityData entityData = entityPluginEventManager.getSelectedEntityData();
//        ShowCreateEntityDialogEvent.fire(EntityPlugin.this, entityData, type, displayType, allowNullFolder());
//    }

    public void create(final String type, final String name, final DocRef folder,
            final CreateCallback callback) {
        dispatcher.execute(new EntityServiceCreateAction(type, name, folder), new AsyncCallbackAdaptor<DocRef>() {
            @Override
            public void onSuccess(final DocRef result) {
                callback.onCreate(result);
            }
        });
    }

    public void load(final DocRef docRef, final LoadCallback<E> callback) {
        dispatcher.execute(new EntityServiceLoadAction<E>(docRef, fetchSet()),
                new AsyncCallbackAdaptor<E>() {
                    @Override
                    public void onSuccess(final E result) {
                        callback.onLoad(result);
                    }
                });
    }

    public void save(final E entity, final SaveCallback<E> callback) {
        dispatcher.execute(new EntityServiceSaveAction<E>(entity), new AsyncCallbackAdaptor<E>() {
            @Override
            public void onSuccess(final E result) {
                callback.onSave(result);
            }
        });
    }

    private void copy(final E entity, final DocRef folder, final String name, final SaveCallback<E> callback) {
        dispatcher.execute(new EntityServiceCopyAction<E>(entity, folder, name), new AsyncCallbackAdaptor<E>() {
            @Override
            public void onSuccess(final E result) {
                callback.onSave(result);
            }
        });
    }

    private void move(final E entity, final DocRef folder, final SaveCallback<E> callback) {
        dispatcher.execute(new EntityServiceMoveAction<E>(entity, folder), new AsyncCallbackAdaptor<E>() {
            @Override
            public void onSuccess(final E result) {
                callback.onSave(result);
            }
        });
    }

    private void delete(final E entity, final DeleteCallback<E> callback) {
        dispatcher.execute(new EntityServiceDeleteAction<E>(entity), new AsyncCallbackAdaptor<E>() {
            @Override
            public void onSuccess(final E result) {
                callback.onDelete(result);
            }
        });
    }

    /**
     * @return Any thing to fetch?
     */
    protected Set<String> fetchSet() {
        return null;
    }

    public abstract String getType();

    public interface CreateCallback {
        void onCreate(DocRef entity);
    }

    public interface LoadCallback<E extends NamedEntity> {
        void onLoad(E entity);
    }

    public interface SaveCallback<E extends NamedEntity> {
        void onSave(E entity);
    }

    public interface DeleteCallback<E extends NamedEntity> {
        void onDelete(E entity);
    }

    private class EntityCloseHandler implements CloseHandler {
        private final EntityTabData tabData;

        public EntityCloseHandler(final EntityTabData tabData) {
            this.tabData = tabData;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onCloseRequest(final CloseCallback callback) {
            if (tabData != null && tabData instanceof EntityEditPresenter<?, ?>) {
                final EntityEditPresenter<?, E> presenter = (EntityEditPresenter<?, E>) tabData;
                if (presenter.isDirty()) {
                    ConfirmEvent.fire(EntityPlugin.this,
                            presenter.getEntity().getType() + " '" + presenter.getEntity().getName()
                                    + "' has unsaved changes. Are you sure you want to close this item?",
                            new ConfirmCallback() {
                                @Override
                                public void onResult(final boolean result) {
                                    actuallyClose(tabData, callback, presenter, result);
                                }
                            });
                } else {
                    actuallyClose(tabData, callback, presenter, true);
                }
            }
        }

        private void actuallyClose(final EntityTabData tabData, final CloseCallback callback,
                                   final EntityEditPresenter<?, E> presenter, final boolean ok) {
            if (ok) {
                // Tell the presenter we are closing.
                presenter.onClose();
                // Cleanup reference to this tab data.
                removeTabData(tabData);
            }
            // Tell the callback to close the tab if ok.
            callback.closeTab(ok);
        }
    }
}
