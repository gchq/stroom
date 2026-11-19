/*
 * Copyright 2026 Crown Copyright
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

package stroom.security.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.identity.shared.SigningKeyResource;
import stroom.security.identity.shared.SigningKeyRow;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

/// The internal identity provider's signing keys ([SigningKeyList]), fetched when asked.
public class SigningKeyListController extends Controller {

    private static final SigningKeyResource SIGNING_KEY_RESOURCE = GWT.create(SigningKeyResource.class);

    private final SigningKeyList view;
    private final RestFactory restFactory;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param restFactory       Lists the keys.
    /// @param dateTimeFormatter Formats times in the user's preferences.
    @Inject
    public SigningKeyListController(final EventBus eventBus,
                                    final SigningKeyList view,
                                    final RestFactory restFactory,
                                    final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        view.setDateFormat(dateTimeFormatter::format);
    }

    /// @return The selection.
    public MultiSelectionModelImpl<SigningKeyRow> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// The toolbar beside the list's pager, for the screen's own buttons.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    /// Fetches the keys again.
    public void refresh() {
        restFactory
                .create(SIGNING_KEY_RESOURCE)
                .method(SigningKeyResource::list)
                .onSuccess(view::setData)
                .onFailure(restError -> {
                    AlertEvent.fireError(this, restError.getMessage(), null);
                    view.setData(List.of());
                })
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }
}
