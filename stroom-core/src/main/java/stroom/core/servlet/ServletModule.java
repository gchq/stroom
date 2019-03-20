/*
 * Copyright 2018 Crown Copyright
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

package stroom.core.servlet;

import com.google.inject.AbstractModule;
import stroom.receive.common.DebugServlet;
import stroom.receive.common.ReceiveDataServlet;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.guice.FilterBinder;
import stroom.util.guice.FilterInfo;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.ResourcePaths;
import stroom.util.guice.ServletBinder;

import javax.servlet.http.HttpSessionListener;

public class ServletModule extends AbstractModule {
    private static final String MATCH_ALL_PATHS = "/*";

    @Override
    protected void configure() {
        bind(SessionListService.class).to(SessionListListener.class);

        GuiceUtil.buildMultiBinder(binder(), HttpSessionListener.class)
                .addBinding(SessionListListener.class);

        FilterBinder.create(binder())
                .bind(new FilterInfo(HttpServletRequestFilter.class.getSimpleName(), MATCH_ALL_PATHS),
                        HttpServletRequestFilter.class)
                .bind(new FilterInfo("rejectPostFilter", MATCH_ALL_PATHS)
                                .addparameter("rejectUri", "/"),
                        RejectPostFilter.class)
                .bind(new FilterInfo("cacheControlFilter", MATCH_ALL_PATHS)
                                .addparameter("seconds", "600"),
                        CacheControlFilter.class);

        ServletBinder.create(binder())
                .bind(ResourcePaths.ROOT_PATH + "/dashboard", DashboardServlet.class)
                .bind(ResourcePaths.ROOT_PATH + "/debug", DebugServlet.class)
                .bind(ResourcePaths.ROOT_PATH + "/dynamic.css", DynamicCSSServlet.class)
                .bind(ResourcePaths.ROOT_PATH + "/echo", EchoServlet.class)
                .bind(ResourcePaths.ROOT_PATH + "/datafeed", ReceiveDataServlet.class)
                .bind(ResourcePaths.ROOT_PATH + "/datafeed/*", ReceiveDataServlet.class)
                .bind(ResourcePaths.ROOT_PATH + "/sessionList", SessionListServlet.class)
                .bind(ResourcePaths.ROOT_PATH + "/ui", StroomServlet.class)
                .bind(ResourcePaths.ROOT_PATH + "/status", StatusServlet.class);

        TaskHandlerBinder.create(binder())
                .bind(SessionListAction.class, SessionListHandler.class)
                .bind(SessionListClusterTask.class, SessionListClusterHandler.class);
    }
}