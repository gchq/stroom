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

package stroom.floormap.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.floormap.client.FloorMapEventStorePlugin;
import stroom.floormap.client.FloorMapPlugin;

/// GIN module for the Floor Map feature.
/// Binds the floor map's plugins and its two documents' editors.
public class FloorMapModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(FloorMapPlugin.class);
        // The events store is a document of its own, creatable from the explorer, so it needs its
        // own plugin and editor rather than appearing inside the floor map's own editor.
        bindPlugin(FloorMapEventStorePlugin.class);
    }
}
