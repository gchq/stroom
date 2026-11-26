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

package stroom.ai.impl.tools.draft;

import java.util.List;
import java.util.function.Function;

/// Whether conditions hold on a record, as every count of records by conditions tells it: they hold, they don't, or
/// it can't be told here (an xpath, a dictionary, a record-scope test, a field an extraction gives). A record whose
/// verdict is unknown is counted apart, as one that may be recognised, never as one that is or isn't (found in
/// review, 2026-11-27: the catalogue's check counted such records as never recognised, the translation as always,
/// so the catch-all's form and the draft's notes disagreed).
public enum Verdict {

    /// They hold.
    HOLDS,
    /// They don't.
    DOESNT,
    /// It can't be told here.
    UNKNOWN;

    /// @param holds An evaluator's verdict: true, false, or null where it can't be told.
    /// @return It as a verdict.
    public static Verdict of(final Boolean holds) {
        return holds == null
                ? UNKNOWN
                : holds
                        ? HOLDS
                        : DOESNT;
    }

    /// The verdict of conditions that must all hold: one that doesn't decides it; else one that can't be told.
    ///
    /// @param conditions The conditions.
    /// @param verdict    Each one's verdict.
    /// @param <T>        The conditions' type.
    /// @return Theirs; [#HOLDS] for none.
    public static <T> Verdict all(final List<T> conditions, final Function<T, Verdict> verdict) {
        Verdict out = HOLDS;
        for (final T condition : conditions) {
            out = out.and(verdict.apply(condition));
            if (out == DOESNT) {
                return out;
            }
        }
        return out;
    }

    /// @param other Another verdict.
    /// @return Both holding: one that doesn't decides it, else one that can't be told.
    public Verdict and(final Verdict other) {
        if (this == DOESNT || other == DOESNT) {
            return DOESNT;
        }
        return this == UNKNOWN || other == UNKNOWN
                ? UNKNOWN
                : HOLDS;
    }

    /// @param other Another verdict.
    /// @return Either holding: one that holds decides it, else one that can't be told.
    public Verdict or(final Verdict other) {
        if (this == HOLDS || other == HOLDS) {
            return HOLDS;
        }
        return this == UNKNOWN || other == UNKNOWN
                ? UNKNOWN
                : DOESNT;
    }

    /// @return It as the evaluator says it: true, false, or null where it can't be told.
    public Boolean toBoolean() {
        return this == UNKNOWN
                ? null
                : this == HOLDS;
    }
}
