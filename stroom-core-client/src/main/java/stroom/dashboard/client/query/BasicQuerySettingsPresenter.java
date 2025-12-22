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
import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.BasicSettingsView;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.explorer.shared.NodeFlag;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.ModelStringUtil;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;

public class BasicQuerySettingsPresenter
        extends BasicSettingsTabPresenter<BasicQuerySettingsPresenter.BasicQuerySettingsView>
        implements Focus {

    private final DocSelectionBoxPresenter dataSourceSelectionPresenter;

    @Inject
    public BasicQuerySettingsPresenter(final EventBus eventBus, final BasicQuerySettingsView view,
                                       final DocSelectionBoxPresenter dataSourceSelectionPresenter) {
        super(eventBus, view);
        this.dataSourceSelectionPresenter = dataSourceSelectionPresenter;

        view.setDataSourceSelectionView(dataSourceSelectionPresenter.getView());

//        final String[] types = dataSourceTypes.getTypes();
//        dataSourceSelectionPresenter.setIncludedTypes(types);
        dataSourceSelectionPresenter.setNodeFlags(NodeFlag.DATA_SOURCE);
        dataSourceSelectionPresenter.setRequiredPermissions(DocumentPermission.USE);
//        dataSourceSelectionPresenter.setSelectionTypes(types);
    }

    @Override
    public void focus() {
        getView().focus();
    }

    DocRef getDataSource() {
        return dataSourceSelectionPresenter.getSelectedEntityReference();
    }

    private void setDataSource(final DocRef dataSourceRef) {
        dataSourceSelectionPresenter.setSelectedEntityReference(dataSourceRef, true);
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final QueryComponentSettings settings = (QueryComponentSettings) componentConfig.getSettings();
        setDataSource(settings.getDataSource());

        Automate automate = settings.getAutomate();
        if (automate == null) {
            automate = Automate.builder().build();
        }

        getView().setQueryOnOpen(automate.isOpen());
        getView().setAutoRefresh(automate.isRefresh());
        getView().setRefreshInterval(automate.getRefreshInterval());
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        final ComponentConfig result = super.write(componentConfig);
        final QueryComponentSettings oldSettings = (QueryComponentSettings) result.getSettings();
        final QueryComponentSettings newSettings = writeSettings(oldSettings);
        return result.copy().settings(newSettings).build();
    }

    private QueryComponentSettings writeSettings(final QueryComponentSettings settings) {
        return settings
                .copy()
                .dataSource(getDataSource())
                .automate(Automate.builder()
                        .open(getView().isQueryOnOpen())
                        .refresh(getView().isAutoRefresh())
                        .refreshInterval(getView().getRefreshInterval())
                        .build())
                .build();
    }

    @Override
    public boolean validate() {
        boolean valid = false;

        try {
            final String interval = getView().getRefreshInterval();
            final int millis = ModelStringUtil.parseDurationString(interval).intValue();

            if (millis < QueryPresenter.TEN_SECONDS) {
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

        final QueryComponentSettings oldSettings = (QueryComponentSettings) componentConfig.getSettings();
        final QueryComponentSettings newSettings = writeSettings(oldSettings);

        final boolean equal = Objects.equals(oldSettings.getDataSource(), newSettings.getDataSource()) &&
                Objects.equals(oldSettings.getAutomate(), newSettings.getAutomate());

        return !equal;
    }


    // --------------------------------------------------------------------------------


    public interface BasicQuerySettingsView extends BasicSettingsView {

        void setDataSourceSelectionView(View view);

        boolean isQueryOnOpen();

        void setQueryOnOpen(boolean queryOnOpen);

        boolean isAutoRefresh();

        void setAutoRefresh(boolean autoRefresh);

        String getRefreshInterval();

        void setRefreshInterval(String refreshInterval);
    }
}
