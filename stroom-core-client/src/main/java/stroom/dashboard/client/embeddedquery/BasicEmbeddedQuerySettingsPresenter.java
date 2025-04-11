/*
 * Copyright 2024 Crown Copyright
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

package stroom.dashboard.client.embeddedquery;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.BasicSettingsView;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryTablePreferences;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;

public class BasicEmbeddedQuerySettingsPresenter
        extends BasicSettingsTabPresenter<BasicEmbeddedQuerySettingsPresenter.BasicEmbeddedQuerySettingsView>
        implements Focus {

    static final int TEN_SECONDS = 10000;

    private final DocSelectionBoxPresenter querySelectionPresenter;

    @Inject
    public BasicEmbeddedQuerySettingsPresenter(final EventBus eventBus,
                                               final BasicEmbeddedQuerySettingsView view,
                                               final DocSelectionBoxPresenter querySelectionPresenter) {
        super(eventBus, view);
        this.querySelectionPresenter = querySelectionPresenter;

        view.setQuerySelectionView(querySelectionPresenter.getView());
        querySelectionPresenter.setIncludedTypes(QueryDoc.TYPE);
        querySelectionPresenter.setRequiredPermissions(DocumentPermission.USE);
    }

    @Override
    public void focus() {
        getView().focus();
    }

    DocRef getQuery() {
        return querySelectionPresenter.getSelectedEntityReference();
    }

    private void setQuery(final DocRef queryRef) {
        querySelectionPresenter.setSelectedEntityReference(queryRef, true);
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final EmbeddedQueryComponentSettings settings = (EmbeddedQueryComponentSettings) componentConfig.getSettings();
        setQuery(settings.getQueryRef());

        Automate automate = settings.getAutomate();
        if (automate == null) {
            automate = Automate.builder().build();
        }

        getView().setQueryOnOpen(automate.isOpen());
        getView().setAutoRefresh(automate.isRefresh());
        getView().setRefreshInterval(automate.getRefreshInterval());
        getView().setPageSize(NullSafe.getOrElse(
                settings,
                EmbeddedQueryComponentSettings::getQueryTablePreferences,
                QueryTablePreferences::getPageSize,
                100));
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        ComponentConfig result = super.write(componentConfig);
        final EmbeddedQueryComponentSettings oldSettings = (EmbeddedQueryComponentSettings) result.getSettings();
        final EmbeddedQueryComponentSettings newSettings = writeSettings(oldSettings);
        return result.copy().settings(newSettings).build();
    }

    private EmbeddedQueryComponentSettings writeSettings(final EmbeddedQueryComponentSettings settings) {
        QueryTablePreferences queryTablePreferences = settings.getQueryTablePreferences();
        QueryTablePreferences.Builder builder = QueryTablePreferences.copy(queryTablePreferences);
        builder.pageSize(getView().getPageSize());

        return settings
                .copy()
                .queryRef(getQuery())
                .automate(Automate.builder()
                        .open(getView().isQueryOnOpen())
                        .refresh(getView().isAutoRefresh())
                        .refreshInterval(getView().getRefreshInterval())
                        .build())
                .queryTablePreferences(builder.build())
                .build();
    }

    @Override
    public boolean validate() {
        boolean valid = false;

        try {
            final String interval = getView().getRefreshInterval();
            int millis = ModelStringUtil.parseDurationString(interval).intValue();

            if (millis < TEN_SECONDS) {
                throw new NumberFormatException("Query refresh interval must be greater than or equal to 10 seconds");
            }

            valid = true;
        } catch (final RuntimeException e) {
            AlertEvent.fireError(this, e.getMessage(), null);
        }

        return valid;
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        if (super.isDirty(componentConfig)) {
            return true;
        }

        final EmbeddedQueryComponentSettings oldSettings =
                (EmbeddedQueryComponentSettings) componentConfig.getSettings();
        final EmbeddedQueryComponentSettings newSettings = writeSettings(oldSettings);

        final boolean equal = Objects.equals(oldSettings.getQueryRef(), newSettings.getQueryRef()) &&
                              Objects.equals(oldSettings.getAutomate(), newSettings.getAutomate()) &&
                              Objects.equals(oldSettings.getQueryTablePreferences(),
                                      newSettings.getQueryTablePreferences());

        return !equal;
    }

    public interface BasicEmbeddedQuerySettingsView
            extends BasicSettingsView {

        void setQuerySelectionView(View view);

        boolean isQueryOnOpen();

        void setQueryOnOpen(boolean queryOnOpen);

        boolean isAutoRefresh();

        void setAutoRefresh(boolean autoRefresh);

        String getRefreshInterval();

        void setRefreshInterval(String refreshInterval);

        int getPageSize();

        void setPageSize(int pageSize);
    }
}
