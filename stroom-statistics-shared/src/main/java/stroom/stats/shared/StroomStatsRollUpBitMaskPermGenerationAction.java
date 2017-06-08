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

import stroom.entity.shared.Action;
import stroom.entity.shared.ResultList;

public class StroomStatsRollUpBitMaskPermGenerationAction extends Action<ResultList<CustomRollUpMask>> {

    private static final long serialVersionUID = -6717177709897089356L;

    private int fieldCount;

    public StroomStatsRollUpBitMaskPermGenerationAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public StroomStatsRollUpBitMaskPermGenerationAction(final int fieldCount) {
        this.fieldCount = fieldCount;
    }

    public int getFieldCount() {
        return fieldCount;
    }

    @Override
    public String getTaskName() {
        return "Roll up bit mask perm generation";
    }
}
