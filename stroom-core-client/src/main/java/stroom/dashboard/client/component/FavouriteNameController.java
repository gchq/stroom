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

package stroom.dashboard.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.shared.StoredQuery;
import stroom.dashboard.shared.StoredQueryResource;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The dialog that names a favourite query ([FavouriteName]), made for one use: creates the
/// favourite, or renames it.
public class FavouriteNameController extends Controller {

    private static final StoredQueryResource STORED_QUERY_RESOURCE = GWT.create(StoredQueryResource.class);

    private final FavouriteName view;
    private final RestFactory restFactory;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param restFactory Saves the favourite.
    @Inject
    public FavouriteNameController(final EventBus eventBus,
                                   final FavouriteName view,
                                   final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        view.setOnEnter(() -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Asks for a favourite's name in a dialog, owned by `opener` until it is closed, and saves it.
    ///
    /// @param opener   What shows it.
    /// @param query    The favourite, new (with no id) or one to rename.
    /// @param onSaved  Given the favourite once it is saved.
    public void show(final HasControllerNode opener,
                     final StoredQuery query,
                     final Consumer<StoredQuery> onSaved) {
        view.setText(query.getName());
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(query.getId() == null
                        ? "Create New Favourite"
                        : "Rename Favourite")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String name = NullSafe.trim(view.getText());
                        if (name.isEmpty()) {
                            AlertEvent.fireWarn(this, "You must provide a name", e::reset);
                        } else {
                            save(query.copy().name(name).favourite(true).build(), onSaved, e);
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void save(final StoredQuery query,
                      final Consumer<StoredQuery> onSaved,
                      final HidePopupRequestEvent event) {
        restFactory
                .create(STORED_QUERY_RESOURCE)
                .method(res -> query.getId() == null
                        ? res.create(query)
                        : res.update(query))
                .onSuccess(result -> {
                    onSaved.accept(result);
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

}
