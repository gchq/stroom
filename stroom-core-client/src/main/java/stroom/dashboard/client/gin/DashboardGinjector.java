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

package stroom.dashboard.client.gin;

import stroom.dashboard.client.DashboardPlugin;
import stroom.dashboard.client.embeddedquery.gin.EmbeddedQueryGinjector;
import stroom.dashboard.client.embeddedquery.gin.EmbeddedQueryModule;
import stroom.dashboard.client.input.gin.InputGinjector;
import stroom.dashboard.client.input.gin.InputModule;
import stroom.dashboard.client.query.gin.QueryGinjector;
import stroom.dashboard.client.query.gin.QueryModule;
import stroom.dashboard.client.table.gin.TableGinjector;
import stroom.dashboard.client.table.gin.TableModule;
import stroom.dashboard.client.text.gin.TextGinjector;
import stroom.dashboard.client.text.gin.TextModule;
import stroom.dashboard.client.vis.gin.VisGinjector;
import stroom.dashboard.client.vis.gin.VisModule;

import com.google.gwt.inject.client.AsyncProvider;
import com.google.gwt.inject.client.GinModules;

@GinModules({
        QueryModule.class,
        TableModule.class,
        TextModule.class,
        VisModule.class,
        InputModule.class,
        EmbeddedQueryModule.class
})
public interface DashboardGinjector extends
        QueryGinjector,
        TableGinjector,
        TextGinjector,
        VisGinjector,
        InputGinjector,
        EmbeddedQueryGinjector {

    AsyncProvider<DashboardPlugin> getDashboardPlugin();
}
