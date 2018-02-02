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

package stroom.dashboard.client.query;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.function.Consumer;

public class QueryInfoPresenter extends MyPresenterWidget<QueryInfoPresenter.QueryInfoView> {
    private static final String DEFAULT_QUERY_INFO_POPUP_TITLE = "Please Provide Query Info";
    private static final String DEFAULT_QUERY_INFO_VALIDATION_REGEX = "^[\\s\\S]{3,}$";

    private boolean queryInfoPopupEnabled = false;
    private String queryInfoPopupTitle = DEFAULT_QUERY_INFO_POPUP_TITLE;
    private String queryInfoPopupValidationRegex = DEFAULT_QUERY_INFO_VALIDATION_REGEX;

    @Inject
    public QueryInfoPresenter(final EventBus eventBus, final QueryInfoView view, final ClientPropertyCache clientPropertyCache) {
        super(eventBus, view);

        clientPropertyCache.get()
                .onSuccess(result -> {
                    queryInfoPopupEnabled = result.getBoolean(ClientProperties.QUERY_INFO_POPUP_ENABLED, false);
                    queryInfoPopupTitle = result.get(ClientProperties.QUERY_INFO_POPUP_TITLE, DEFAULT_QUERY_INFO_POPUP_TITLE);
                    queryInfoPopupValidationRegex = result.get(ClientProperties.QUERY_INFO_POPUP_VALIDATION_REGEX, DEFAULT_QUERY_INFO_VALIDATION_REGEX);
                })
                .onFailure(caught -> AlertEvent.fireError(QueryInfoPresenter.this, caught.getMessage(), null));
    }

    public void show(final String queryInfo, final Consumer<State> consumer) {
        if (queryInfoPopupEnabled) {
            getView().setQueryInfo(queryInfo);
            final PopupSize popupSize = new PopupSize(640, 480, true);
            ShowPopupEvent.fire(this,
                    this,
                    PopupType.OK_CANCEL_DIALOG,
                    popupSize, queryInfoPopupTitle,
                    new PopupUiHandlers() {
                        @Override
                        public void onHideRequest(final boolean autoClose, final boolean ok) {
                            if (ok) {
                                boolean valid = true;
                                if (queryInfoPopupValidationRegex != null && queryInfoPopupValidationRegex.length() > 0) {
                                    valid = false;
                                    try {
                                        valid = getView().getQueryInfo().matches(queryInfoPopupValidationRegex);
                                    } catch (final Exception e) {
                                        AlertEvent.fireErrorFromException(QueryInfoPresenter.this, e, null);
                                    }
                                }

                                if (valid) {
                                    HidePopupEvent.fire(QueryInfoPresenter.this, QueryInfoPresenter.this);
                                    consumer.accept(new State(getView().getQueryInfo(), true));
                                } else {
                                    AlertEvent.fireWarn(QueryInfoPresenter.this, "The text entered is not valid", null);
                                }
                            } else {
                                HidePopupEvent.fire(QueryInfoPresenter.this, QueryInfoPresenter.this);
                                consumer.accept(new State(getView().getQueryInfo(), false));
                            }
                        }

                        @Override
                        public void onHide(final boolean autoClose, final boolean ok) {
                        }
                    });
        } else {
            consumer.accept(new State(null, true));
        }
    }

    public interface QueryInfoView extends View {
        String getQueryInfo();

        void setQueryInfo(String queryInfo);

        void focus();
    }

    public class State {
        private final String queryInfo;
        private final boolean ok;

        public State(final String queryInfo, final boolean ok) {
            this.queryInfo = queryInfo;
            this.ok = ok;
        }

        public String getQueryInfo() {
            return queryInfo;
        }

        public boolean isOk() {
            return ok;
        }
    }
}