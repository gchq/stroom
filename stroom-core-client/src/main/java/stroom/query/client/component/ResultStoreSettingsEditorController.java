/*
 * Copyright 2023 Crown Copyright
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

package stroom.query.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.query.api.LifespanInfo;
import stroom.query.api.ResultStoreInfo;
import stroom.query.client.ResultStoreModel;
import stroom.query.shared.UpdateStoreRequest;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Changes a result store's settings ([ResultStoreSettingsEditor]) in a dialog made for one use,
/// saving them on OK.
public class ResultStoreSettingsEditorController extends Controller {

    private final ResultStoreSettingsEditor view;
    private final ResultStoreModel resultStoreModel;

    /// Made by GIN, with its settings.
    ///
    /// @param eventBus         The application's event bus.
    /// @param view             The settings.
    /// @param resultStoreModel Saves them.
    @Inject
    public ResultStoreSettingsEditorController(final EventBus eventBus,
                                               final ResultStoreSettingsEditor view,
                                               final ResultStoreModel resultStoreModel) {
        super(eventBus);
        this.view = view;
        this.resultStoreModel = resultStoreModel;
        initWidget(view);
    }

    /// Shows a store's settings in a dialog, owned by `opener` until it is closed, to change them.
    ///
    /// @param opener          What shows it.
    /// @param resultStoreInfo The store.
    /// @param title           The dialog's caption.
    /// @param onClosed        Told whether the settings were saved.
    public void show(final HasControllerNode opener,
                     final ResultStoreInfo resultStoreInfo,
                     final String title,
                     final Consumer<Boolean> onClosed) {
        view.setLifespans(resultStoreInfo.getSearchProcessLifespan(), resultStoreInfo.getStoreLifespan());
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX())
                .caption(title)
                .onShow(event -> view.focus())
                .onHideRequest(e -> onHideRequest(resultStoreInfo, e, onClosed))
                .fire();
    }

    /// OK saves the settings once every duration is set; a failed save says why and leaves the
    /// dialog open. Package-private for the tests.
    ///
    /// @param resultStoreInfo The result store.
    /// @param e               The request to hide the dialog.
    /// @param onClosed        Told whether the settings were saved, once the dialog closes.
    void onHideRequest(final ResultStoreInfo resultStoreInfo,
                       final HidePopupRequestEvent e,
                       final Consumer<Boolean> onClosed) {
        if (!e.isOk()) {
            onClosed.accept(false);
            e.hide();
            return;
        }
        final LifespanInfo searchProcessLifespan = view.getSearchProcessLifespan();
        final LifespanInfo storeLifespan = view.getStoreLifespan();
        final String notSet = getFirstDurationNotSet(searchProcessLifespan, storeLifespan);
        if (notSet != null) {
            AlertEvent.fireWarn(this, notSet + " must be set, e.g. 10m, 1h or 1d.", e::reset);
            return;
        }
        final UpdateStoreRequest updateStoreRequest = new UpdateStoreRequest(resultStoreInfo.getQueryKey(),
                searchProcessLifespan,
                storeLifespan);
        try {
            resultStoreModel.updateSettings(resultStoreInfo.getNodeName(), updateStoreRequest, saved -> {
                onClosed.accept(saved);
                e.hide();
            }, RestErrorHandler.forPopup(this, e), taskMonitor());
        } catch (final RuntimeException ex) {
            AlertEvent.fireError(this, ex.getMessage(), e::reset);
        }
    }

    // Every duration needs a value: the server can't read a blank one, and 'never expire' isn't
    // something these settings offer. Returns the label of the first that is blank, or null.
    private static String getFirstDurationNotSet(final LifespanInfo searchProcessLifespan,
                                                 final LifespanInfo storeLifespan) {
        if (NullSafe.isBlankString(searchProcessLifespan.getTimeToIdle())) {
            return "Search Process Time To Idle";
        } else if (NullSafe.isBlankString(searchProcessLifespan.getTimeToLive())) {
            return "Search Process Time To Live";
        } else if (NullSafe.isBlankString(storeLifespan.getTimeToIdle())) {
            return "Store Time To Idle";
        } else if (NullSafe.isBlankString(storeLifespan.getTimeToLive())) {
            return "Store Time To Live";
        }
        return null;
    }

}
