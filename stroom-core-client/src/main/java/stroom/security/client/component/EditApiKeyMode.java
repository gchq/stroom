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

package stroom.security.client.component;

/// What the API key dialog ([EditApiKeyController]) is doing with the key.
public enum EditApiKeyMode {
    /// When the user is initially entering data to create the key.
    PRE_CREATE,
    /// Immediately after creation of the key, when the API key string is still in memory and
    /// can be shown.
    POST_CREATE,
    /// Editing of the record at a later date when the API key string is no longer available to
    /// show to the user.
    EDIT
}
