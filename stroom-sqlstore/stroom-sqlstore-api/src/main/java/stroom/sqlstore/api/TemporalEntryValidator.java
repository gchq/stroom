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

package stroom.sqlstore.api;

import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

/// The one set of rules for what a temporal store will accept as an entry.
///
/// Every write path - the store's `create` and `applyChanges`, the DAO beneath them, and the
/// pipeline filter that ingests reference data - checks against these, so an entry one path
/// accepts is never rejected by another.
///
/// Each method returns why its argument is invalid, or `null` if it is valid, so that a caller can
/// choose whether to throw, skip the entry, or report it against a batch position.
public final class TemporalEntryValidator {

    private TemporalEntryValidator() {
        // Static utility.
    }

    /// Checks a key: it must not be blank, and must fit the `map_key` column.
    ///
    /// @param key The key to check.
    /// @return Why the key is invalid, or `null` if it is valid.
    public static String keyProblem(final String key) {
        if (key == null || key.isBlank()) {
            return "the key must not be blank.";
        }
        if (key.length() > UpdatableTemporalStore.MAX_KEY_LENGTH) {
            return "the key is " + key.length() + " characters long, longer than the maximum of "
                   + UpdatableTemporalStore.MAX_KEY_LENGTH + ": '" + key + "'.";
        }
        return null;
    }

    /// Checks an entry to be written: it must name a map, have a valid key and have an effective time.
    ///
    /// @param entry The entry to check.
    /// @return Why the entry is invalid, or `null` if it is valid.
    public static String entryProblem(final TemporalEntry entry) {
        if (entry == null) {
            return "there is no entry.";
        }
        if (entry.getMap() == null || entry.getMap().isBlank()) {
            return "the map name must not be blank.";
        }
        return keyAndTimeProblem(entry.getKey(), entry.getEffectiveTimeMs());
    }

    /// Checks the id of an entry to be read or deleted: it must have a valid key and an effective time.
    ///
    /// The map name is not checked, because an id is always resolved against a store that has
    /// already been identified.
    ///
    /// @param id The id to check.
    /// @return Why the id is invalid, or `null` if it is valid.
    public static String idProblem(final TemporalEntryId id) {
        if (id == null) {
            return "there is no entry id.";
        }
        return keyAndTimeProblem(id.getKey(), id.getEffectiveTimeMs());
    }

    private static String keyAndTimeProblem(final String key, final Long effectiveTimeMs) {
        final String keyProblem = keyProblem(key);
        if (keyProblem != null) {
            return keyProblem;
        }
        if (effectiveTimeMs == null) {
            return "the effective time must be set.";
        }
        return null;
    }
}
