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
import stroom.entity.shared.StringCriteria.MatchStyle;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import javax.inject.Inject;
import java.util.function.Consumer;


public class ManageActivityPresenter extends
        MyPresenterWidget<ManageActivityView> implements ManageActivityUiHandlers, HasHandlers {
    public static final String LIST = "LIST";

    private final ActivityListPresenter listPresenter;
    private final Provider<ActivityEditPresenter> editProvider;
    private final ClientDispatchAsync dispatcher;
    private final ClientPropertyCache clientPropertyCache;
    private FindActivityCriteria criteria = new FindActivityCriteria();
    private ButtonView newButton;
    private ButtonView openButton;
    private ButtonView deleteButton;

    @Inject
    public ManageActivityPresenter(final EventBus eventBus,
                                   final ManageActivityView view,
                                   final ActivityListPresenter listPresenter,
                                   final Provider<ActivityEditPresenter> editProvider,
                                   final ClientDispatchAsync dispatcher,
                                   final ClientPropertyCache clientPropertyCache) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.editProvider = editProvider;
        this.dispatcher = dispatcher;
        this.clientPropertyCache = clientPropertyCache;

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
                hide();
//                onOpen();
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

    public void showInitial(final Consumer<Activity> consumer) {
        clientPropertyCache.get().onSuccess(clientProperties -> {
            final boolean show = clientProperties.getBoolean(ClientProperties.ACTIVITY_CHOOSE_ON_STARTUP, false);
            if (show) {
                show(consumer);
            } else {
                consumer.accept(null);
            }
        });
    }

    public void show(final Consumer<Activity> consumer) {
        final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                hide();
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                consumer.accept(getSelected());
            }
        };
        clientPropertyCache.get().onSuccess(clientProperties -> {
            final String title = clientProperties.get(ClientProperties.ACTIVITY_MANAGER_TITLE);
            final PopupSize popupSize = new PopupSize(1000, 600, true);
            ShowPopupEvent.fire(ManageActivityPresenter.this, ManageActivityPresenter.this,
                    PopupType.CLOSE_DIALOG, null, popupSize, title, popupUiHandlers, null);
            enableButtons();
        });
    }

    public void hide() {
        HidePopupEvent.fire(ManageActivityPresenter.this, ManageActivityPresenter.this);
    }

    private void enableButtons() {
        final boolean enabled = getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
        if (enabled) {
            EnablePopupEvent.fire(this, this);
        } else {
            DisablePopupEvent.fire(this, this);
        }
    }

    private void onOpen() {
        final Activity e = getSelected();
        if (e != null) {
            // Load the activity.
            dispatcher.exec(new EntityServiceLoadAction<Activity>(DocRef.create(e), null)).onSuccess(this::onEdit);
        }
    }

    @SuppressWarnings("unchecked")
    private void onEdit(final Activity e) {
        if (e != null) {
            if (editProvider != null) {
                final ActivityEditPresenter editor = editProvider.get();
                editor.show(e, activity -> {
                    listPresenter.refresh();
                    setSelected(activity);
                });
            }
        }
    }

    private void onDelete() {
        final Activity entity = getSelected();
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

    void setSelected(final Activity activity) {
        listPresenter.getSelectionModel().clear();
        if (activity != null) {
            listPresenter.getSelectionModel().setSelected(activity);
        }
    }

    Activity getSelected() {
        return listPresenter.getSelectionModel().getSelected();
    }

    interface ManageActivityView extends View, HasUiHandlers<ManageActivityUiHandlers> {
    }
}
