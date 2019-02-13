/*
 * Copyright 2016 Crown Copyright
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

package stroom.stats.shared;

import stroom.docref.SharedObject;

import java.util.Set;

public class CustomRollUpMaskFields implements SharedObject, Comparable<CustomRollUpMaskFields> {

    private static final long serialVersionUID = 8434581070070953139L;

    private int id;
    private short maskValue;
    private Set<Integer> rolledUpFieldPositions;

    public CustomRollUpMaskFields() {
        // Default constructor necessary for GWT serialisation.
    }

    public CustomRollUpMaskFields(final int id, final short maskValue, final Set<Integer> rolledUpFieldPositions) {
        this.id = id;
        this.maskValue = maskValue;
        this.rolledUpFieldPositions = rolledUpFieldPositions;
    }

    public int getId() {
        return id;
    }

    public void setId(final int id) {
        this.id = id;
    }

    public short getMaskValue() {
        return maskValue;
    }

    public void setMaskValue(final short maskValue) {
        this.maskValue = maskValue;
    }

    public Set<Integer> getRolledUpFieldPositions() {
        return rolledUpFieldPositions;
    }

    public void setRolledUpFieldPositions(final Set<Integer> rolledUpFieldPositions) {
        this.rolledUpFieldPositions = rolledUpFieldPositions;
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
