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

package stroom.statistics.shared.common;

import stroom.entity.shared.Action;
import stroom.entity.shared.ResultList;

import java.util.List;

public class RollUpBitMaskConversionAction extends Action<ResultList<CustomRollUpMaskFields>> {
    private static final long serialVersionUID = 9094030625926679727L;

    private List<Short> maskValues;

    public RollUpBitMaskConversionAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public RollUpBitMaskConversionAction(final List<Short> maskValues) {
        this.maskValues = maskValues;
    }

    public List<Short> getMaskValues() {
        return maskValues;
    }

    public void setMaskValues(final List<Short> maskValues) {
        this.maskValues = maskValues;
    }

    @Override
    public String getTaskName() {
        return "Roll up bit mask conversion";
    }
}
