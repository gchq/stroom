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

import stroom.activity.client.CurrentActivity;
import stroom.alert.client.event.AlertEvent;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.QueryConfig;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class QueryInfoPresenter extends MyPresenterWidget<QueryInfoPresenter.QueryInfoView> {

    private static final String DEFAULT_QUERY_INFO_POPUP_TITLE = "Please Provide Query Info";
    private static final String DEFAULT_QUERY_INFO_VALIDATION_REGEX = "^[\\s\\S]{3,}$";

    private final CurrentActivity currentActivity;
    private boolean queryInfoPopupEnabled = false;
    private String queryInfoPopupTitle = DEFAULT_QUERY_INFO_POPUP_TITLE;
    private String queryInfoPopupValidationRegex = DEFAULT_QUERY_INFO_VALIDATION_REGEX;

    @Inject
    public QueryInfoPresenter(final EventBus eventBus,
                              final QueryInfoView view,
                              final UiConfigCache uiConfigCache,
                              final CurrentActivity currentActivity) {
        super(eventBus, view);
        this.currentActivity = currentActivity;

        uiConfigCache.get()
                .onSuccess(uiConfig -> {
                    final QueryConfig queryConfig = uiConfig.getQuery();
                    queryInfoPopupEnabled = queryConfig.getInfoPopup().isEnabled();
                    queryInfoPopupTitle = queryConfig.getInfoPopup().getTitle();
                    queryInfoPopupValidationRegex = queryConfig.getInfoPopup().getValidationRegex();
                })
                .onFailure(caught -> AlertEvent.fireError(QueryInfoPresenter.this, caught.getMessage(), null));
    }

    public void show(final String queryInfo, final Consumer<State> consumer) {
        currentActivity.getActivity(activity -> {
            boolean required = true;
            if (activity != null && activity.getDetails() != null) {
                final String value = activity.getDetails().value("requireQueryInfo");
                if (value != null && value.equalsIgnoreCase("false")) {
                    required = false;
                }
            }

            if (queryInfoPopupEnabled && required) {
                getView().setQueryInfo(queryInfo);
                final PopupSize popupSize = PopupSize.resizable(640, 480);
                ShowPopupEvent.builder(this)
                        .popupType(PopupType.OK_CANCEL_DIALOG)
                        .popupSize(popupSize)
                        .caption(queryInfoPopupTitle)
                        .onShow(e -> getView().focus())
                        .onHideRequest(event -> {
                            if (event.isOk()) {
                                boolean valid = true;
                                if (queryInfoPopupValidationRegex != null
                                        && !queryInfoPopupValidationRegex.isEmpty()) {
                                    valid = false;
                                    try {
                                        valid = getView().getQueryInfo().matches(queryInfoPopupValidationRegex);
                                    } catch (final RuntimeException e) {
                                        AlertEvent.fireErrorFromException(QueryInfoPresenter.this, e, null);
                                    }
                                }

                                if (valid) {
                                    event.hide();
                                    consumer.accept(new State(getView().getQueryInfo(), true));
                                } else {
                                    AlertEvent.fireWarn(QueryInfoPresenter.this,
                                            "The text entered is not valid",
                                            null);
                                }
                            } else {
                                event.hide();
                                consumer.accept(new State(getView().getQueryInfo(), false));
                            }
                        })
                        .fire();
            } else {
                consumer.accept(new State(null, true));
            }
        });
    }

    public interface QueryInfoView extends View, Focus {

        String getQueryInfo();

        void setQueryInfo(String queryInfo);
    }

    public class State {

        private final String queryInfo;
        private final boolean ok;

        State(final String queryInfo, final boolean ok) {
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
