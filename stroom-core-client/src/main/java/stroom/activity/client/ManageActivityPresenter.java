/*
 * Copyright 2018 Crown Copyright
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

package stroom.activity.client;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.activity.client.ManageActivityPresenter.ManageActivityView;
import stroom.activity.shared.Activity;
import stroom.activity.shared.FindActivityCriteria;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.EntityServiceDeleteAction;
import stroom.entity.shared.EntityServiceLoadAction;
import stroom.entity.shared.EntityServiceSaveAction;
import stroom.entity.shared.StringCriteria.MatchStyle;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

import javax.inject.Inject;


public class ManageActivityPresenter extends
        MyPresenterWidget<ManageActivityView> implements ManageActivityUiHandlers, HasHandlers {
    public static final String LIST = "LIST";

    private final ActivityListPresenter listPresenter;
    private final Provider<ActivityEditPresenter> editProvider;
    private final ClientDispatchAsync dispatcher;
    private FindActivityCriteria criteria = new FindActivityCriteria();
    private ButtonView newButton;
    private ButtonView openButton;
    private ButtonView deleteButton;

    @Inject
    public ManageActivityPresenter(final EventBus eventBus,
                                   final ManageActivityView view,
                                   final ActivityListPresenter listPresenter,
                                   final Provider<ActivityEditPresenter> editProvider,
                                   final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.editProvider = editProvider;
        this.dispatcher = dispatcher;

        getView().setUiHandlers(this);

        setInSlot(LIST, listPresenter);

        newButton = listPresenter.addButton(SvgPresets.NEW_ITEM);
        openButton = listPresenter.addButton(SvgPresets.EDIT);
        deleteButton = listPresenter.addButton(SvgPresets.DELETE);

        listPresenter.setCriteria(criteria);
    }

    @Override
    protected void onBind() {
        registerHandler(listPresenter.getView().getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onOpen();
            }
        }));
        if (newButton != null) {
            registerHandler(newButton.addClickHandler(event -> {
                if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                    onNew();
                }
            }));
        }
        if (openButton != null) {
            registerHandler(openButton.addClickHandler(event -> {
                if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                    onOpen();
                }
            }));
        }
        if (deleteButton != null) {
            registerHandler(deleteButton.addClickHandler(event -> {
                if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                    onDelete();
                }
            }));
        }

        super.onBind();
    }

    private void enableButtons() {
        final boolean enabled = listPresenter.getSelectedItem() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    private void onOpen() {
        final Activity e = listPresenter.getSelectedItem();
        if (e != null) {
            // Load the activity.
            dispatcher.exec(new EntityServiceLoadAction<Activity>(DocRef.create(e), null)).onSuccess(this::onEdit);
        }
    }

    @SuppressWarnings("unchecked")
    private void onEdit(final Activity e) {
        if (e != null) {
            final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers() {
                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    listPresenter.refresh();
                }
            };

            if (editProvider != null) {
                final ActivityEditPresenter editor = editProvider.get();
                editor.show(e, popupUiHandlers);
            }
        }
    }

    private void onDelete() {
        final Activity entity = listPresenter.getSelectedItem();
        if (entity != null) {
            ConfirmEvent.fire(this, "Are you sure you want to delete the selected " + getEntityDisplayType() + "?",
                    result -> {
                        if (result) {
                            // Load the activity.
                            dispatcher.exec(new EntityServiceLoadAction<Activity>(DocRef.create(entity), null)).onSuccess(e -> {
                                if (e != null) {
                                    // Delete the activity
                                    dispatcher.exec(new EntityServiceDeleteAction<>(e)).onSuccess(res -> {
                                        listPresenter.refresh();
                                        listPresenter.getView().getSelectionModel().clear();
                                    });
                                }
                            });
                        }
                    });
        }
    }

    private String getEntityDisplayType() {
        return "activity";
    }

    public void setCriteria(final FindActivityCriteria criteria) {
        this.criteria = criteria;
        listPresenter.setCriteria(criteria);
    }

    @Override
    public void changeNameFilter(final String name) {
        if (criteria == null) {
            return;
        }

        String filter = name;
        if (filter != null) {
            filter = filter.trim();
            if (filter.length() == 0) {
                filter = null;
            }
        }

        if ((filter == null && criteria.getName() == null) || (filter != null && filter.equals(criteria.getName().getString()))) {
            return;
        }

        if (name.length() > 0) {
            criteria.getName().setString(name);
            criteria.getName().setMatchStyle(MatchStyle.WildStandAndEnd);
            criteria.getName().setCaseInsensitive(true);
        } else {
            criteria.getName().clear();
        }

        listPresenter.refresh();
    }

    private void onNew() {
        onEdit(new Activity());
    }

    public Activity getSelected() {
        return listPresenter.getSelectedItem();
    }

    interface ManageActivityView extends View, HasUiHandlers<ManageActivityUiHandlers> {
    }
}
