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

import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.entity.shared.DocRef;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.query.shared.DataSource;
import stroom.query.shared.QueryData;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.EqualsBuilder;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

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
        dataSourceSelectionPresenter.setTags(DataSource.DATA_SOURCE);
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

        final QueryData settings = (QueryData) componentData.getSettings();
        setDataSource(settings.getDataSource());
    }

    @Override
    public void write(final ComponentConfig componentData) {
        super.write(componentData);

        final QueryData settings = (QueryData) componentData.getSettings();
        settings.setDataSource(getDataSource());
    }

    @Override
    public boolean isDirty(final ComponentConfig componentData) {
        if (super.isDirty(componentData)) {
            return true;
        }

        final QueryData settings = (QueryData) componentData.getSettings();

        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(settings.getDataSource(), getDataSource());

        return !builder.isEquals();
    }

    public interface BasicQuerySettingsView extends BasicSettingsTabPresenter.SettingsView {
        void setDataSourceSelectionView(View view);
    }
}
