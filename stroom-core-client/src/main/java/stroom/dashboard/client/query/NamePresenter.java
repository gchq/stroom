/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.dashboard.client.query;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.shared.StoredQuery;
import stroom.dashboard.shared.StoredQueryResource;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.widget.popup.client.event.DialogEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.popup.client.view.DialogActionUiHandlers;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class NamePresenter
        extends MyPresenterWidget<NamePresenter.NameView>
        implements DialogActionUiHandlers {

    private static final StoredQueryResource STORED_QUERY_RESOURCE = GWT.create(StoredQueryResource.class);

    private final RestFactory restFactory;

    @Inject
    public NamePresenter(final EventBus eventBus,
                         final NameView view,
                         final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
    }

    public void show(final StoredQuery queryEntity,
                     final Consumer<StoredQuery> consumer) {
        getView().setName(queryEntity.getName());
        getView().setUiHandlers(this);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(queryEntity.getId() == null
                        ? "Create New Favourite"
                        : "Rename Favourite")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        String entityName = getView().getName();
                        if (entityName != null) {
                            entityName = entityName.trim();
                        }

                        if (entityName == null || entityName.length() == 0) {
                            AlertEvent.fireWarn(this,
                                    "You must provide a name",
                                    e::reset);

                        } else {
                            queryEntity.setName(entityName);
                            queryEntity.setFavourite(true);
                            if (queryEntity.getId() == null) {
                                create(queryEntity, consumer, e);
                            } else {
                                update(queryEntity, consumer, e);
                            }
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void create(final StoredQuery query,
                        final Consumer<StoredQuery> consumer,
                        final HidePopupRequestEvent event) {
        restFactory
                .create(STORED_QUERY_RESOURCE)
                .method(res -> res.create(query))
                .onSuccess(result -> {
                    consumer.accept(result);
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(this)
                .exec();
    }

    private void update(final StoredQuery query,
                        final Consumer<StoredQuery> consumer,
                        final HidePopupRequestEvent event) {
        restFactory
                .create(STORED_QUERY_RESOURCE)
                .method(res -> res.update(query))
                .onSuccess(result -> {
                    consumer.accept(result);
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(this)
                .exec();
    }

    @Override
    public void onDialogAction(final DialogAction action) {
        DialogEvent.fire(this, this, action);
    }

    public interface NameView extends View, Focus, HasUiHandlers<DialogActionUiHandlers> {

        String getName();

        void setName(String name);
    }
}
