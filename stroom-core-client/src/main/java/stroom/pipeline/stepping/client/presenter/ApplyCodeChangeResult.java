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

package stroom.pipeline.stepping.client.presenter;

/// The outcome of applying a proposed code change to an element's editor.
public enum ApplyCodeChangeResult {
    /// The change was applied, unsaved.
    APPLIED,
    /// The proposal is the same as the current code, so there was nothing to apply.
    NO_CHANGE,
    /// The editor's code has changed since the proposal was made, so it was not applied.
    CODE_CHANGED,
    /// The element has no code that can be edited (none, not loaded or read only).
    NOT_EDITABLE,
    /// The code editor has not been shown yet, so nothing could be applied.
    NOT_READY
}
