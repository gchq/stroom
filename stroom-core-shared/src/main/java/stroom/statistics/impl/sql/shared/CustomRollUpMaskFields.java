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

package stroom.statistics.impl.sql.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomRollUpMaskFields implements Comparable<CustomRollUpMaskFields> {
    @JsonProperty
    private final int id;
    @JsonProperty
    private final short maskValue;
    @JsonProperty
    private final Set<Integer> rolledUpFieldPositions;

    @JsonCreator
    public CustomRollUpMaskFields(@JsonProperty("id") final int id,
                                  @JsonProperty("maskValue") final short maskValue,
                                  @JsonProperty("rolledUpFieldPositions") final Set<Integer> rolledUpFieldPositions) {
        this.id = id;
        this.maskValue = maskValue;
        this.rolledUpFieldPositions = rolledUpFieldPositions;
    }

    public int getId() {
        return id;
    }

    public short getMaskValue() {
        return maskValue;
    }

    public Set<Integer> getRolledUpFieldPositions() {
        return rolledUpFieldPositions;
    }

    public boolean isFieldRolledUp(final int position) {
        if (rolledUpFieldPositions != null) {
            return rolledUpFieldPositions.contains(position);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "CustomRollUpMaskFields [maskValue=" + maskValue + ", rolledUpFieldPositions=" + rolledUpFieldPositions
                + "]";
    }

    @Override
    public int compareTo(final CustomRollUpMaskFields that) {
        return Short.compare(this.maskValue, that.maskValue);
    }
}
