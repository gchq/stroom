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

package stroom.data.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.data.client.DataPreviewTabPlugin;
import stroom.data.client.SourceTabPlugin;
import stroom.data.client.presenter.DataDisplaySupport;
import stroom.editor.client.presenter.DelegatingAceCompleter;

public class StreamStoreModule extends PluginModule {

    @Override
    protected void configure() {
        bind(DataDisplaySupport.class).asEagerSingleton();

        bindPlugin(SourceTabPlugin.class);
        bindPlugin(DataPreviewTabPlugin.class);

        bind(DelegatingAceCompleter.class).asEagerSingleton();

    }
}
