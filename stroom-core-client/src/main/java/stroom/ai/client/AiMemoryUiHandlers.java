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

package stroom.ai.client;

import com.gwtplatform.mvp.client.UiHandlers;

/// What the user can do in the Memory dialog.
public interface AiMemoryUiHandlers extends UiHandlers {

    /// Opens a piece of memory to view and edit.
    ///
    /// @param index Its position in the list.
    void onMemorySelected(int index);

    /// Called as the user edits the memory, so it can be marked as unsaved.
    void onContentChanged();

    /// Starts a new piece of memory.
    void onNewMemory();

    /// Saves the memory being edited.
    void onSaveMemory();

    /// Deletes the memory being edited.
    void onDeleteMemory();

    /// Deletes all of the user's own memory.
    void onClearUserMemory();

    /// Undoes a change the agent made to memory.
    ///
    /// @param messageId The MEMORY_UPDATE message recording the change.
    void onUndoChange(int messageId);
}
