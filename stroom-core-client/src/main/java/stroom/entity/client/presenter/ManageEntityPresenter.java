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

package stroom.entity.client.presenter;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.presenter.ConfirmCallback;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.EntityServiceDeleteAction;
import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.StringCriteria.MatchStyle;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

public abstract class ManageEntityPresenter<C extends FindNamedEntityCriteria, E extends NamedEntity> extends
        MyPresenterWidget<ManageEntityPresenter.ManageEntityView> implements ManageEntityUiHandlers, HasHandlers {
    public static final String LIST = "LIST";

    protected final ManageEntityListPresenter<C, E> listPresenter;
    protected final Provider<?> editProvider;
    protected final ManageNewEntityPresenter newPresenter;
    protected final ClientDispatchAsync dispatcher;

    private final GlyphButtonView newButton;
    private final GlyphButtonView openButton;
    private final GlyphButtonView deleteButton;

    protected E entity;
    protected C criteria;

    public ManageEntityPresenter(final EventBus eventBus, final ManageEntityView view,
                                 final ManageEntityListPresenter<C, E> listPresenter, final Provider<?> editProvider,
                                 final ManageNewEntityPresenter newPresenter,
                                 final ClientDispatchAsync dispatcher, final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.editProvider = editProvider;
        this.dispatcher = dispatcher;
        this.newPresenter = newPresenter;

        getView().setUiHandlers(this);

        setInSlot(LIST, listPresenter);

        newButton = listPresenter.addButton(GlyphIcons.NEW_ITEM);
        openButton = listPresenter.addButton(GlyphIcons.EDIT);
        deleteButton = listPresenter.addButton(GlyphIcons.DELETE);

        //final boolean updatePerm = securityContext.hasAppPermission(getEntityType(), DocumentPermissionNames.UPDATE);
//
//        if (!allowDelete() || !updatePerm) {
//            deleteButton.setVisible(false);
//        }
//        if (!allowNew() || !updatePerm) {
//            newButton.setVisible(false);
//        }

    }

    @Override
    protected void onBind() {
        registerHandler(listPresenter.getView().getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onOpen();
            }
        }));
        registerHandler(newButton.addClickHandler(event -> {
            if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                onNew();
            }
        }));
        registerHandler(openButton.addClickHandler(event -> {
            if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                onOpen();
            }
        }));
        registerHandler(deleteButton.addClickHandler(event -> {
            if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                onDelete();
            }
        }));

        super.onBind();
    }

    private void enableButtons() {
        final boolean enabled = listPresenter.getSelectedItem() != null;
        openButton.setEnabled(enabled);
        if (allowDelete()) {
            deleteButton.setEnabled(enabled);
        }
    }

    private void onOpen() {
        final E e = listPresenter.getSelectedItem();
        onEdit(e);
    }

    @SuppressWarnings("unchecked")
    public void onEdit(final E e) {
        if (e != null) {
            final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers() {
                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    listPresenter.refresh();
                }
            };

            if (editProvider != null) {
                final Object editor = editProvider.get();
                if (editor != null && editor instanceof ManageEntityEditPresenter) {
                    final ManageEntityEditPresenter<?, E> entityEditPresenter = (ManageEntityEditPresenter<?, E>) editor;
                    entityEditPresenter.showEntity(e, popupUiHandlers);
                }
            }
        }
    }

    private void onDelete() {
        final E e = listPresenter.getSelectedItem();
        if (e != null) {
            ConfirmEvent.fire(this, "Are you sure you want to delete the selected " + getEntityDisplayType() + "?",
                    new ConfirmCallback() {
                        @Override
                        public void onResult(final boolean result) {
                            if (result) {
                                final EntityServiceDeleteAction<E> action = new EntityServiceDeleteAction<E>(e);
                                dispatcher.execute(action, new AsyncCallbackAdaptor<E>() {
                                    @Override
                                    public void onSuccess(final E result) {
                                        listPresenter.refresh();
                                        listPresenter.getView().getSelectionModel().clear();
                                    }
                                });
                            }
                        }
                    });
        }
    }

    protected abstract String getEntityType();

    protected abstract String getEntityDisplayType();

    public void setCriteria(final C criteria) {
        this.criteria = criteria;
        listPresenter.setCriteria(criteria);
    }

    @Override
    public void changeNameFilter(final String name) {
        if (setNameFilter(name)) {
            listPresenter.refresh();
        }
    }

    /**
     * This sets the name filter to be used when fetching items. This method
     * returns false is the filter is set to the same value that is already set.
     *
     * @param nameFilter
     * @return
     */
    private boolean setNameFilter(final String nameFilter) {
        if (criteria == null) {
            return false;
        }

        String filter = nameFilter;
        if (filter != null) {
            filter = filter.trim();
            if (filter.length() == 0) {
                filter = null;
            }
        }

        if ((filter == null && criteria.getName() == null) || (filter != null && filter.equals(criteria.getName()))) {
            return false;
        }

        criteria.getName().setString(filter);
        criteria.getName().setMatchStyle(MatchStyle.WildStandAndEnd);
        return true;
    }

    private void onNew() {
        final PopupUiHandlers hidePopupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    entity = newEntity();
                    entity.setName(newPresenter.getName());
                }

                newPresenter.hide();

                if (ok) {
                    onEdit(entity);
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Ignore hide.
            }
        };

        newPresenter.show(hidePopupUiHandlers);
    }

    public void onNew(final E entity) {
        onEdit(entity);
    }

    protected boolean allowNew() {
        return true;
    }

    protected boolean allowDelete() {
        return true;
    }

    protected abstract E newEntity();

    public interface ManageEntityView extends View, HasUiHandlers<ManageEntityUiHandlers> {
    }
}
