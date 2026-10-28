/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.gin.processing;

import stroom.core.client.ContentManager;

import com.google.gwt.inject.client.AbstractGinModule;
import com.google.inject.Singleton;

/// Single bindings from Stroom's modules that the processing screens need without the rest of
/// their module: `AppModule`'s content manager (a singleton, which the document plugins share,
/// as in Stroom).
public class ProcessingExtrasScreenModule extends AbstractGinModule {

    /// Binds the controllers and components.
    @Override
    protected void configure() {
        bind(ContentManager.class).in(Singleton.class);
    }
}
