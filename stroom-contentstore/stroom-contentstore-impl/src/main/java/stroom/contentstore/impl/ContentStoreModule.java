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

package stroom.contentstore.impl;

import stroom.contentstore.shared.ContentStoreResource;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.guice.ServletBinder;

import com.google.inject.AbstractModule;

public class ContentStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        // Bind the Resource implementation to the REST service
        bind(ContentStoreResource.class).to(ContentStoreResourceImpl.class);
        RestResourcesBinder.create(binder()).bind(ContentStoreResource.class);

        // Servlet for icon passthrough
        ServletBinder.create(binder()).bind(IconPassthroughServlet.class);
    }
}
