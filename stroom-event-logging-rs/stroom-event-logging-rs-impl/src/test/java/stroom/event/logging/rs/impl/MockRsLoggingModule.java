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

package stroom.event.logging.rs.impl;

import stroom.event.logging.impl.LoggingConfig;

import com.google.inject.AbstractModule;
import jakarta.servlet.http.HttpServletRequest;

public class MockRsLoggingModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(LoggingConfig.class).toInstance(new LoggingConfig());
        bind(HttpServletRequest.class).to(MockHttpServletRequest.class);
//        bind(ResourceInfo.class).to(MockResourceInfo.class);
    }
}

