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

package stroom.dashboard.client.main;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dashboard.client.flexlayout.FlexLayoutChangeHandler;
import stroom.dashboard.client.flexlayout.PositionAndSize;
import stroom.dashboard.shared.DashboardConfig.TabVisibility;
import stroom.dashboard.shared.LayoutConfig;
import stroom.dashboard.shared.TabConfig;

public class DashboardLayoutPresenter extends MyPresenterWidget<DashboardLayoutPresenter.DashboardLayoutView> {
    @Inject
    public DashboardLayoutPresenter(final EventBus eventBus, final DashboardLayoutView view) {
        super(eventBus, view);
    }

    public void setComponents(final Components components) {
        getView().setComponents(components);
    }

    public LayoutConfig getLayoutData() {
        return getView().getLayoutData();
    }

    public void setLayoutData(final LayoutConfig layoutData) {
        getView().setLayoutData(layoutData);
    }

    public void setTabVisibility(final TabVisibility tabVisibility) {
        getView().setTabVisibility(tabVisibility);
    }

    public void setFlexLayoutChangeHandler(final FlexLayoutChangeHandler changeHandler) {
        getView().setFlexLayoutChangeHandler(changeHandler);
    }

    public void closeTab(final TabConfig tabData) {
        getView().closeTab(tabData);
    }

    public PositionAndSize getPositionAndSize(final Object object) {
        return getView().getPositionAndSize(object);
    }

    public interface DashboardLayoutView extends View {
        void setComponents(Components components);

        LayoutConfig getLayoutData();

        void setLayoutData(LayoutConfig layoutData);

        void setTabVisibility(TabVisibility tabVisibility);

        void closeTab(TabConfig tabData);

        void setFlexLayoutChangeHandler(FlexLayoutChangeHandler flexLayoutChangeHandler);

        PositionAndSize getPositionAndSize(Object object);
    }
}
