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

import stroom.util.shared.diff.LineDiff;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// The changes to documents' Documentation tabs that the user applied from the AI chat, kept so each can be undone:
/// whether one can be applied to a tab's text, the text to put back to undo it, and those whose Undo apply has been
/// withdrawn as the tab has been edited since.
///
/// This holds no GWT dependencies of its own, so it can be unit tested.
final class AppliedDocumentationChanges {

    /// Why a change can't be applied, as the tab's text isn't what it was proposed against.
    static final String CHANGED = "The documentation has changed since this was proposed. Ask again for a change to "
                                  + "the current documentation.";
    /// Why a change can't be applied, as the user may not edit the document.
    static final String NOT_EDITABLE = "You don't have permission to edit this document, so the change can't be "
                                       + "applied.";
    /// Why an applied change can't be undone, as the tab has been edited since.
    static final String UNDO_WITHDRAWN = "The documentation has been edited since the change was applied, so Undo "
                                         + "apply would lose those edits. Use Ctrl+Z in its editor to undo it.";

    private final Map<String, Applied> applied = new HashMap<>();
    private final Set<String> withdrawn = new HashSet<>();

    /// @param readOnly True if the Documentation tab's editor is read only.
    /// @param current  The text the tab holds now.
    /// @param base     The text the change was proposed against.
    /// @return Why the change can't be applied, or null if it can. Line endings are ignored.
    static String applyProblem(final boolean readOnly, final String current, final String base) {
        if (readOnly) {
            return NOT_EDITABLE;
        }
        if (!LineDiff.diff(Objects.requireNonNullElse(base, ""), Objects.requireNonNullElse(current, ""))
                .isIdentical()) {
            return CHANGED;
        }
        return null;
    }

    /// Records a change the user has applied.
    ///
    /// @param key      What the change is known by, e.g. its message and action ids.
    /// @param previous The text the tab held before it.
    /// @param text     The text applied.
    void applied(final String key, final String previous, final String text) {
        applied.put(key, new Applied(previous, text));
        withdrawn.remove(key);
    }

    /// @param key      What the change is known by.
    /// @param current  The text the tab holds now.
    /// @param base     The text the change was proposed against.
    /// @param proposed The text proposed.
    /// @return The text to put back to undo the change, or null if the tab has been edited since it was applied
    /// (or what was applied isn't known), so undoing it would lose those edits. If the change wasn't recorded here
    /// (e.g. the page has been loaded again since), the tab must hold exactly the proposed text, which is put back
    /// to the text it was proposed against.
    String undoText(final String key, final String current, final String base, final String proposed) {
        final Applied change = applied.get(key);
        if (change != null) {
            return Objects.equals(change.text(), current)
                    ? change.previous()
                    : null;
        }
        return proposed != null && proposed.equals(current)
                ? Objects.requireNonNullElse(base, "")
                : null;
    }

    /// Forgets a change once it has been undone.
    ///
    /// @param key What the change is known by.
    void undone(final String key) {
        applied.remove(key);
        withdrawn.remove(key);
    }

    /// Withdraws a change's Undo apply, as its tab has been edited since.
    ///
    /// @param key What the change is known by.
    void withdraw(final String key) {
        withdrawn.add(key);
    }

    /// @param key What the change is known by.
    /// @return True if its Undo apply has been withdrawn.
    boolean isWithdrawn(final String key) {
        return withdrawn.contains(key);
    }


    // --------------------------------------------------------------------------------


    /// A change applied to a Documentation tab.
    ///
    /// @param previous The text the tab held before it.
    /// @param text     The text applied.
    private record Applied(String previous, String text) {

    }
}
