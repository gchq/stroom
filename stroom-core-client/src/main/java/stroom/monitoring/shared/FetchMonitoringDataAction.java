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

package stroom.monitoring.shared;

import stroom.dispatch.shared.Action;
import stroom.dispatch.shared.TreeAction;
import stroom.entity.shared.ResultList;
import stroom.util.shared.SharedObject;

import java.util.HashSet;
import java.util.Set;

public class FetchMonitoringDataAction extends Action<ResultList<SharedObject>>implements TreeAction<SharedObject> {
    private static final long serialVersionUID = -6808045615241590297L;

    private Set<SharedObject> expandedRows;

    public enum ModelType {
        MEMORY, CPU, EPS
    }

    private ModelType modelType;

    public FetchMonitoringDataAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public void setModelType(final ModelType modelType) {
        this.modelType = modelType;
    }

    public ModelType getModelType() {
        return modelType;
    }

    @Override
    public String getTaskName() {
        return "Fetch monitoring data";
    }

    @Override
    public void setRowExpanded(final SharedObject row, final boolean open) {
        if (open) {
            if (expandedRows == null) {
                expandedRows = new HashSet<SharedObject>();
            }
            expandedRows.add(row);
        } else {
            if (expandedRows != null) {
                expandedRows.remove(row);
            }
        }
    }

    @Override
    public boolean isRowExpanded(final SharedObject row) {
        if (expandedRows == null) {
            return false;
        }
        return expandedRows.contains(row);
    }

    @Override
    public Set<SharedObject> getExpandedRows() {
        return expandedRows;
    }
}
