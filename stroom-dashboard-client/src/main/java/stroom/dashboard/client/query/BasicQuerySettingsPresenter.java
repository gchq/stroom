/*
 * Copyright 2017 Crown Copyright
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
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.ModelStringUtil;

public class BasicQuerySettingsPresenter
        extends BasicSettingsTabPresenter<BasicQuerySettingsPresenter.BasicQuerySettingsView> {
    private final EntityDropDownPresenter dataSourceSelectionPresenter;

    @Inject
    public BasicQuerySettingsPresenter(final EventBus eventBus, final BasicQuerySettingsView view,
                                       final EntityDropDownPresenter dataSourceSelectionPresenter) {
        super(eventBus, view);
        this.dataSourceSelectionPresenter = dataSourceSelectionPresenter;

        view.setDataSourceSelectionView(dataSourceSelectionPresenter.getView());

//        final String[] types = dataSourceTypes.getTypes();
//        dataSourceSelectionPresenter.setIncludedTypes(types);
        dataSourceSelectionPresenter.setTags("DataSource");
        dataSourceSelectionPresenter.setRequiredPermissions(DocumentPermissionNames.USE);
//        dataSourceSelectionPresenter.setSelectionTypes(types);
    }

    private DocRef getDataSource() {
        return dataSourceSelectionPresenter.getSelectedEntityReference();
    }

    private void setDataSource(final DocRef dataSourceRef) {
        dataSourceSelectionPresenter.setSelectedEntityReference(dataSourceRef);
    }

    @Override
    public void read(final ComponentConfig componentData) {
        super.read(componentData);

        final QueryComponentSettings settings = (QueryComponentSettings) componentData.getSettings();
        setDataSource(settings.getDataSource());

        Automate automate = settings.getAutomate();
        if (automate == null) {
            automate = new Automate();
            settings.setAutomate(automate);
        }

        getView().setQueryOnOpen(automate.isOpen());
        getView().setAutoRefresh(automate.isRefresh());
        getView().setRefreshInterval(automate.getRefreshInterval());
    }

    @Override
    public void write(final ComponentConfig componentData) {
        super.write(componentData);

        final QueryComponentSettings settings = (QueryComponentSettings) componentData.getSettings();
        settings.setDataSource(getDataSource());

        Automate automate = settings.getAutomate();
        if (automate == null) {
            automate = new Automate();
            settings.setAutomate(automate);
        }

        automate.setOpen(getView().isQueryOnOpen());
        automate.setRefresh(getView().isAutoRefresh());
        automate.setRefreshInterval(getView().getRefreshInterval());
    }

    @Override
    public boolean validate() {
        boolean valid = false;

        try {
            final String interval = getView().getRefreshInterval();
            int millis = ModelStringUtil.parseDurationString(interval).intValue();

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
    public boolean isDirty(final ComponentConfig componentData) {
        if (super.isDirty(componentData)) {
            return true;
        }

        final QueryComponentSettings settings = (QueryComponentSettings) componentData.getSettings();
        Automate automate = settings.getAutomate();
        if (automate == null) {
            automate = new Automate();
            settings.setAutomate(automate);
        }

        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(settings.getDataSource(), getDataSource());
        builder.append(automate.isOpen(), getView().isQueryOnOpen());
        builder.append(automate.isRefresh(), getView().isAutoRefresh());
        builder.append(automate.getRefreshInterval(), getView().getRefreshInterval());

        return !builder.isEquals();
    }

    public interface BasicQuerySettingsView extends BasicSettingsTabPresenter.SettingsView {
        void setDataSourceSelectionView(View view);

        boolean isQueryOnOpen();

        void setQueryOnOpen(boolean queryOnOpen);

        boolean isAutoRefresh();

        void setAutoRefresh(boolean autoRefresh);

        String getRefreshInterval();

        void setRefreshInterval(String refreshInterval);
    }
}
