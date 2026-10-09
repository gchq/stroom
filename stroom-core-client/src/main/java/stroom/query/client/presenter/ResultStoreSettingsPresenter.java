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

package stroom.query.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.query.api.LifespanInfo;
import stroom.query.api.ResultStoreInfo;
import stroom.query.client.presenter.ResultStoreSettingsPresenter.ResultStoreSettingsView;
import stroom.query.shared.UpdateStoreRequest;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class ResultStoreSettingsPresenter extends MyPresenterWidget<ResultStoreSettingsView> {

    private final ResultStoreModel resultStoreModel;

    private ResultStoreInfo resultStoreInfo;

    @Inject
    public ResultStoreSettingsPresenter(final EventBus eventBus,
                                        final ResultStoreSettingsView view,
                                        final ResultStoreModel resultStoreModel) {
        super(eventBus, view);
        this.resultStoreModel = resultStoreModel;
    }

    void show(final ResultStoreInfo resultStoreInfo, final String title, final Consumer<Boolean> consumer) {
        read(resultStoreInfo);

        final PopupSize popupSize = PopupSize.resizableX();
        ShowPopupEvent
                .builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(title)
                .onShow(event -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String notSet = getFirstDurationNotSet();
                        if (notSet != null) {
                            AlertEvent.fireWarn(this,
                                    notSet + " must be set, e.g. 10m, 1h or 1d.",
                                    e::reset);
                            return;
                        }
                        final UpdateStoreRequest updateStoreRequest = write();
                        try {
                            // A failed save says why and leaves the dialog open
                            resultStoreModel.updateSettings(resultStoreInfo.getNodeName(),
                                    updateStoreRequest, r -> {
                                        consumer.accept(r);
                                        e.hide();
                                    }, RestErrorHandler.forPopup(this, e), this);
                        } catch (final RuntimeException ex) {
                            AlertEvent.fireError(ResultStoreSettingsPresenter.this,
                                    ex.getMessage(), e::reset);
                        }
                    } else {
                        consumer.accept(false);
                        e.hide();
                    }
                }).fire();
    }

    // Every duration needs a value: the server can't read a blank one, and 'never expire' isn't
    // something these settings offer. Returns the label of the first that is blank, or null.
    private String getFirstDurationNotSet() {
        if (NullSafe.isBlankString(getView().getSearchProcessTimeToIdle())) {
            return "Search Process Time To Idle";
        } else if (NullSafe.isBlankString(getView().getSearchProcessTimeToLive())) {
            return "Search Process Time To Live";
        } else if (NullSafe.isBlankString(getView().getStoreTimeToIdle())) {
            return "Store Time To Idle";
        } else if (NullSafe.isBlankString(getView().getStoreTimeToLive())) {
            return "Store Time To Live";
        }
        return null;
    }

    private void read(final ResultStoreInfo resultStoreInfo) {
        this.resultStoreInfo = resultStoreInfo;

        final LifespanInfo searchProcessLifespan = resultStoreInfo.getSearchProcessLifespan();
        final LifespanInfo storeLifespan = resultStoreInfo.getStoreLifespan();

        getView().setSearchProcessTimeToIdle(searchProcessLifespan.getTimeToIdle());
        getView().setSearchProcessTimeToLive(searchProcessLifespan.getTimeToLive());
        getView().setSearchProcessDestroyOnTabClose(searchProcessLifespan.isDestroyOnTabClose());
        getView().setSearchProcessDestroyOnWindowClose(searchProcessLifespan.isDestroyOnWindowClose());

        getView().setStoreTimeToIdle(storeLifespan.getTimeToIdle());
        getView().setStoreTimeToLive(storeLifespan.getTimeToLive());
        getView().setStoreDestroyOnTabClose(storeLifespan.isDestroyOnTabClose());
        getView().setStoreDestroyOnWindowClose(storeLifespan.isDestroyOnWindowClose());
    }

    private UpdateStoreRequest write() {
        final LifespanInfo searchProcessLifespan = new LifespanInfo(getView().getSearchProcessTimeToIdle(),
                getView().getSearchProcessTimeToLive(),
                getView().isSearchProcessDestroyOnTabClose(),
                getView().isSearchProcessDestroyOnWindowClose());

        final LifespanInfo storeLifespan = new LifespanInfo(getView().getStoreTimeToIdle(),
                getView().getStoreTimeToLive(),
                getView().isStoreDestroyOnTabClose(),
                getView().isStoreDestroyOnWindowClose());

        return new UpdateStoreRequest(resultStoreInfo.getQueryKey(), searchProcessLifespan, storeLifespan);
    }

    public interface ResultStoreSettingsView extends View, Focus {

        String getSearchProcessTimeToIdle();

        void setSearchProcessTimeToIdle(String searchProcessTimeToIdle);

        String getSearchProcessTimeToLive();

        void setSearchProcessTimeToLive(String searchProcessTimeToLive);

        boolean isSearchProcessDestroyOnTabClose();

        void setSearchProcessDestroyOnTabClose(boolean searchProcessDestroyOnTabClose);

        boolean isSearchProcessDestroyOnWindowClose();

        void setSearchProcessDestroyOnWindowClose(boolean searchProcessDestroyOnWindowClose);

        String getStoreTimeToIdle();

        void setStoreTimeToIdle(String storeTimeToIdle);

        String getStoreTimeToLive();

        void setStoreTimeToLive(String storeTimeToLive);

        boolean isStoreDestroyOnTabClose();

        void setStoreDestroyOnTabClose(boolean storeDestroyOnTabClose);

        boolean isStoreDestroyOnWindowClose();

        void setStoreDestroyOnWindowClose(boolean storeDestroyOnWindowClose);
    }
}
