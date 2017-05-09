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

public class StroomStatsStoreFieldChangeAction extends Action<StroomStatsStoreEntityData> {

    private static final long serialVersionUID = 6058872325119692765L;

    private StroomStatsStoreEntityData oldEntityData;
    private StroomStatsStoreEntityData newEntityData;

    public StroomStatsStoreFieldChangeAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public StroomStatsStoreFieldChangeAction(final StroomStatsStoreEntityData oldEntityData,
                                             final StroomStatsStoreEntityData newEntityData) {
        this.oldEntityData = oldEntityData;
        this.newEntityData = newEntityData;
    }

    public StroomStatsStoreEntityData getOldEntityData() {
        return oldEntityData;
    }

    public void setOldEntityData(final StroomStatsStoreEntityData oldEntityData) {
        this.oldEntityData = oldEntityData;
    }

    public StroomStatsStoreEntityData getNewEntityData() {
        return newEntityData;
    }

    public void setNewEntityData(final StroomStatsStoreEntityData newEntityData) {
        this.newEntityData = newEntityData;
    }

    @Override
    public String getTaskName() {
        return "Statistics Data Source Field Change";
    }
}
