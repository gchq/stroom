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

package stroom.security.impl;

import stroom.util.guice.GuiceUtil;
import stroom.util.guice.ServletBinder;

import com.google.inject.AbstractModule;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;

public class SessionSecurityModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(SessionListService.class).to(SessionListListener.class);

        GuiceUtil.buildMultiBinder(binder(), HttpSessionListener.class)
                .addBinding(SessionListListener.class);

        GuiceUtil.buildMultiBinder(binder(), HttpSessionIdListener.class)
                .addBinding(SessionListListener.class);

        ServletBinder.create(binder())
                .bind(SessionListServlet.class);
    }
}
