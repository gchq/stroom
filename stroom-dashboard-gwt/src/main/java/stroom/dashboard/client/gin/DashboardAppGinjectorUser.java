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

package stroom.dashboard.client.gin;

import stroom.about.client.gin.AboutModule;
import stroom.alert.client.gin.AlertGinjector;
import stroom.alert.client.gin.AlertModule;
import stroom.annotation.client.gin.AnnotationModule;
import stroom.core.client.presenter.CorePresenter;
import stroom.core.client.presenter.FullScreenPresenter;
import stroom.dashboard.client.embeddedquery.gin.EmbeddedQueryModule;
import stroom.dashboard.client.vis.gin.VisModule;
import stroom.dispatch.client.RestModule;
import stroom.entity.client.gin.EntityGinjector;
import stroom.entity.client.gin.EntityModule;
import stroom.preferences.client.gin.UserPreferencesGinjector;
import stroom.preferences.client.gin.UserPreferencesModule;
import stroom.query.client.gin.QueryModule;
import stroom.quickfilter.client.gin.QuickFilterModule;
import stroom.security.client.gin.SecurityGinjector;
import stroom.security.client.gin.SecurityModule;
import stroom.security.identity.client.gin.ChangePasswordGinjector;
import stroom.security.identity.client.gin.ChangePasswordModule;
import stroom.widget.popup.client.gin.PopupGinjector;
import stroom.widget.popup.client.gin.PopupModule;

import com.google.gwt.inject.client.GinModules;
import com.google.gwt.inject.client.Ginjector;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

@GinModules({
        RestModule.class,
        DashboardAppModule.class,
        PopupModule.class,
        AboutModule.class,
        AnnotationModule.class,
        AlertModule.class,
        SecurityModule.class,
        ChangePasswordModule.class,
        EntityModule.class,
        UserPreferencesModule.class,
        QueryModule.class,
        QuickFilterModule.class,
        DashboardModule.class,
        VisModule.class,
        EmbeddedQueryModule.class})
public interface DashboardAppGinjectorUser extends
        Ginjector,
        PopupGinjector,
        AlertGinjector,
        SecurityGinjector,
        EntityGinjector,
        DashboardGinjector,
        UserPreferencesGinjector,
        ChangePasswordGinjector {

    // Default implementation of standard resources
    EventBus getEventBus();

    // Presenters
    Provider<CorePresenter> getCorePresenter();

    Provider<FullScreenPresenter> getFullScreenPresenter();
}
