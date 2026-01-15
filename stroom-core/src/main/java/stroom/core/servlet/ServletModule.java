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

package stroom.core.servlet;

import stroom.receive.common.DebugServlet;
import stroom.receive.common.ReceiveDataServlet;
import stroom.util.guice.FilterBinder;
import stroom.util.guice.FilterInfo;
import stroom.util.guice.ServletBinder;
import stroom.util.servlet.HttpServletRequestHolder;
import stroom.util.servlet.SessionIdProvider;
import stroom.util.shared.ResourcePaths;

import com.google.inject.AbstractModule;
import jakarta.servlet.http.HttpServletRequest;

public class ServletModule extends AbstractModule {

    private static final String MATCH_ALL_PATHS = "/*";

    @Override
    protected void configure() {
        bind(HttpServletRequest.class).toProvider(HttpServletRequestHolder.class);
        bind(SessionIdProvider.class).to(SessionIdProviderImpl.class);

        // The regex for our script entities that can be cached
        final String cacheablePathsRegex = "^" + ResourcePaths.ROOT_PATH + "/script/\\?$";
        FilterBinder.create(binder())
                .bind(new FilterInfo(HttpServletRequestFilter.class.getSimpleName(), MATCH_ALL_PATHS),
                        HttpServletRequestFilter.class)
                .bind(new FilterInfo(RejectPostFilter.class.getSimpleName(), MATCH_ALL_PATHS)
                                .addparameter("rejectUri", "/"),
                        RejectPostFilter.class)
                .bind(new FilterInfo(CacheControlFilter.class.getSimpleName(), MATCH_ALL_PATHS)
                                .addparameter(CacheControlFilter.INIT_PARAM_KEY_SECONDS, "600")
                                .addparameter(
                                        CacheControlFilter.INIT_PARAM_KEY_CACHEABLE_PATH_REGEX,
                                        cacheablePathsRegex),
                        CacheControlFilter.class);

        ServletBinder.create(binder())
                // authenticated servlets
                .bind(RedirectServlet.class)
                .bind(DashboardServlet.class)
                .bind(StroomServlet.class)
                .bind(SignInServlet.class)
                // unauthenticated servlets (i.e. run as proc user)
                .bind(ReceiveDataServlet.class)
                .bind(DebugServlet.class)
                .bind(StatusServlet.class)
                .bind(SwaggerUiServlet.class);
    }

}
