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

package stroom.dispatch.client;

import com.google.gwt.inject.client.AbstractGinModule;
import com.google.inject.Singleton;

public class ClientDispatchModule extends AbstractGinModule {
    @Override
    protected void configure() {
        bind(ClientDispatchAsync.class).to(ClientDispatchAsyncImpl.class).in(Singleton.class);
        bind(RestService.class).to(RestServiceImpl.class).in(Singleton.class);
    }
}
