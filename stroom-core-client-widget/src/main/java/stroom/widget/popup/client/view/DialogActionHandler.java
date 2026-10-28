/*
 * Copyright 2024 Crown Copyright
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

package stroom.widget.popup.client.view;

/// Told of an action asked of a dialog, by a click on one of its buttons or a key (Escape,
/// Ctrl+Enter).
@FunctionalInterface
public interface DialogActionHandler {

    /// @param action The action asked for, e.g. `DialogAction.OK`.
    void onDialogAction(DialogAction action);
}
