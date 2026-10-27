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
import stroom.dashboard.client.main.RefreshInterval;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.explorer.shared.NodeFlag;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// A Query pane's Basic settings tab ([BasicQuerySettings]): its name, its data source, and when it
/// runs.
public class BasicQuerySettingsController extends BasicSettingsTabController<BasicQuerySettings> {

    private final DocSelectionController dataSource;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The content.
    /// @param docSelectionProvider Makes the data source's picker.
    @Inject
    public BasicQuerySettingsController(final EventBus eventBus,
                                        final BasicQuerySettings view,
                                        final Provider<DocSelectionController> docSelectionProvider) {
        super(eventBus, view);
        dataSource = make(docSelectionProvider);
        dataSource.attach(view.getDataSourceBox());
        dataSource.setNodeFlags(NodeFlag.DATA_SOURCE);
        dataSource.setRequiredPermissions(DocumentPermission.USE);
    }

    /// @return The data source chosen, or null if none is.
    public DocRef getDataSource() {
        return dataSource.getSelectedEntityReference();
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final QueryComponentSettings settings = (QueryComponentSettings) componentConfig.getSettings();
        dataSource.setSelectedEntityReference(settings.getDataSource(), true);

        Automate automate = settings.getAutomate();
        if (automate == null) {
            automate = Automate.builder().build();
        }

        getView().setQueryOnOpen(automate.isOpen());
        getView().setAutoRefresh(automate.isRefresh());
        // Dashboards saved before the interval had a default may have none.
        getView().setRefreshInterval(NullSafe.nonBlankStringElse(
                automate.getRefreshInterval(),
                Automate.DEFAULT_REFRESH_INTERVAL));
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
        final String problem = RefreshInterval.validate(getView().getRefreshInterval());
        if (problem != null) {
            AlertEvent.fireError(this, problem, null);
            return false;
        }
        return true;
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
}
