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

package stroom.visualisation.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.dashboard.client.vis.ClearFunctionCacheEvent;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.EntityEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.shared.DocRefUtil;
import stroom.security.client.ClientSecurityContext;
import stroom.visualisation.shared.Visualisation;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

public class VisualisationPresenter extends EntityEditTabPresenter<LinkTabPanelView, Visualisation> {
    private static final TabData SETTINGS_TAB = new TabDataImpl("Settings");

    private final VisualisationSettingsPresenter settingsPresenter;

    private int loadCount;

    @Inject
    public VisualisationPresenter(final EventBus eventBus, final LinkTabPanelView view, final VisualisationSettingsPresenter settingsPresenter,
                                  final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);
        this.settingsPresenter = settingsPresenter;

        settingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        addTab(SETTINGS_TAB);
        selectTab(SETTINGS_TAB);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS_TAB.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final Visualisation visualisation) {
        loadCount++;
        settingsPresenter.read(visualisation);

        if (loadCount > 1) {
            // Remove the visualisation function from the cache so dashboards
            // reload it.
            ClearFunctionCacheEvent.fire(this, DocRefUtil.create(visualisation));
        }
    }

    @Override
    protected void onWrite(final Visualisation visualisation) {
        settingsPresenter.write(visualisation);
    }

    @Override
    public String getType() {
        return Visualisation.ENTITY_TYPE;
    }
}
