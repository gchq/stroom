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

package stroom.search.elastic.client.presenter;

import com.gwtplatform.mvp.client.UiHandlers;

/// Handles user input on the Settings tab of the Elasticsearch index Fields tab.
public interface ElasticFieldSettingsUiHandlers extends UiHandlers {

    /// Called when the Enabled checkbox changes.
    void onEnabledChange();

    /// Called when any `dense_vector` setting changes.
    void onDenseVectorChange();

    /// Called when the user asks to rename the virtual field.
    void onRenameVirtualField();
}
