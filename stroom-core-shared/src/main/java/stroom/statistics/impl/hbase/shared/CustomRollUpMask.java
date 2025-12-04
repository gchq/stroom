/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.statistics.impl.hbase.shared;

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonPropertyOrder({"rolledUpTagPosition"})
@JsonInclude(Include.NON_NULL)
public class CustomRollUpMask implements HasDisplayValue {

    /**
     * Holds a list of the positions of tags that are rolled up, zero based. The
     * position number is based on the alphanumeric sorted list of tag/field
     * names in the {@link StroomStatsStoreDoc}. Would use a SortedSet but that
     * is not supported by GWT. Must ensure the contents of this are sorted so
     * that when contains is called on lists of these objects it works
     * correctly.
     */
    @JsonProperty
    private final List<Integer> rolledUpTagPosition;

    public CustomRollUpMask() {
        rolledUpTagPosition = new ArrayList<>();
    }

    @JsonCreator
    public CustomRollUpMask(@JsonProperty("rolledUpTagPosition") final List<Integer> rolledUpTagPosition) {
        if (rolledUpTagPosition != null) {
            this.rolledUpTagPosition = new ArrayList<>(rolledUpTagPosition);
            Collections.sort(this.rolledUpTagPosition);
        } else {
            this.rolledUpTagPosition = new ArrayList<>();
        }
    }

    public List<Integer> getRolledUpTagPosition() {
        return rolledUpTagPosition;
    }

    public boolean isTagRolledUp(final int position) {
        return rolledUpTagPosition.contains(position);
    }

    public void setRollUpState(final Integer position, final boolean isRolledUp) {
        if (isRolledUp) {
            if (!rolledUpTagPosition.contains(position)) {
                rolledUpTagPosition.add(position);
                Collections.sort(this.rolledUpTagPosition);
            }
        } else {
            // no need to re-sort on remove as already in order
            rolledUpTagPosition.remove(position);
        }

    }

    @Override
    @JsonIgnore
    public String getDisplayValue() {
        return null;
    }

    public CustomRollUpMask deepCopy() {
        return new CustomRollUpMask(new ArrayList<>(rolledUpTagPosition));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((rolledUpTagPosition == null)
                ? 0
                : rolledUpTagPosition.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CustomRollUpMask other = (CustomRollUpMask) obj;
        if (rolledUpTagPosition == null) {
            return other.rolledUpTagPosition == null;
        } else {
            return rolledUpTagPosition.equals(other.rolledUpTagPosition);
        }
    }

    @Override
    public String toString() {
        return "CustomRollUpMask [rolledUpTagPositions=" + rolledUpTagPosition + "]";
    }
}
