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

package stroom.pathways.shared.pathway;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

/**
 * The kinds of change a trace can make to a learnt model. Stored with each mutation so a replay knows
 * what it is showing without having to work it out from the values.
 */
public enum MutationType implements HasDisplayValue, HasPrimitiveValue {
    /** First sight of this root operation. */
    PATHWAY_ADDED("Pathway Added", 0),
    /** A step the model had not seen beneath this parent. */
    NODE_ADDED("Node Added", 1),
    /** First value recorded for this constraint. */
    CONSTRAINT_ADDED("Constraint Added", 2),
    /** A constraint widened to admit something it did not before. */
    CONSTRAINT_CHANGED("Constraint Changed", 3),
    /** A required attribute was absent, so the constraint stopped being required. */
    CONSTRAINT_OPTIONAL("Constraint Optional", 4),
    /** The bottom of a range moved down to admit something smaller than anything seen before. */
    CONSTRAINT_MIN_EXPANDED("Min Expanded", 5),
    /** The top of a range moved up to admit something larger than anything seen before. */
    CONSTRAINT_MAX_EXPANDED("Max Expanded", 6),
    /** Another value joined the set of those already seen. */
    CONSTRAINT_SET_EXPANDED("Set Expanded", 7),
    /** Too many values to list, so the constraint stopped saying anything. One way. */
    CONSTRAINT_GENERALISED("Generalised", 8),
    /** Too many values to list, so they became the range they span, gaps included. */
    CONSTRAINT_RANGED("Ranged", 9),
    /** A value of a type the constraint had not held before, so it stopped checking the type. */
    CONSTRAINT_TYPE_CONFLICT("Type Conflict", 10),
    /** Named in the configuration as one not to learn, so recorded as admitting anything. */
    CONSTRAINT_IGNORED("Ignored", 11),
    /**
     * A node the model knows about that this trace did not carry, so its occurrences widened to admit
     * none. A real change — it is how the model learns a node is optional — but not one made by a
     * trace that reached the node, which is why it is told apart from an ordinary widening.
     */
    NODE_ABSENT("Absent", 12),
    /**
     * One end of a range stopped being asserted, because the configuration names it as one not to
     * learn. Told apart from a widening because nothing about the traced work changed — a trace
     * carried a duration as it always does, and what moved was what the model is willing to learn
     * from it.
     */
    CONSTRAINT_BOUND_OPENED("Bound Opened", 13),
    /**
     * A constraint was narrowed to what has been seen within the document's narrowing window. Not
     * caused by a trace: every trace only ever widens, and this is the narrowing putting back what
     * one outlier took away, so that a widening means something again.
     */
    CONSTRAINT_NARROWED("Constraint Narrowed", 14),
    /**
     * A path was dropped because nothing has taken it within the document's narrowing window. Not
     * caused by a trace: a pathway gathers one-off shapes faster than anything else, and a path
     * nothing takes anymore is a way through the work that is no longer a way through the work.
     */
    PATH_DROPPED("Path Dropped", 15),
    /**
     * A node stopped being part of the work, because no trace within the window carried it. The node
     * is kept, not removed — paths name nodes by position and a replay puts them back by uuid.
     */
    NODE_RETIRED("Node Retired", 16),
    /**
     * A node that had stopped being part of the work is part of it again. The same node as before, so
     * what it was and how it was used are still its own.
     */
    NODE_REVIVED("Node Revived", 17);

    public static final PrimitiveValueConverter<MutationType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(MutationType.class, MutationType.values());

    private final String displayValue;
    private final byte primitiveValue;

    MutationType(final String displayValue, final int primitiveValue) {
        this.displayValue = displayValue;
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
