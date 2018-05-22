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

package stroom.streamtask.shared;

import stroom.dispatch.shared.TreeAction;
import stroom.entity.shared.Action;
import stroom.entity.shared.HasIsConstrained;
import stroom.entity.shared.ResultList;
import stroom.docref.DocRef;
import stroom.docref.SharedObject;

import java.util.HashSet;
import java.util.Set;

public class FetchProcessorAction extends Action<ResultList<SharedObject>>
        implements TreeAction<SharedObject>, HasIsConstrained {
    private static final long serialVersionUID = -1773544031158236156L;

    private DocRef pipelineRef;
    private Set<SharedObject> expandedRows;

    public FetchProcessorAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchProcessorAction(final DocRef pipelineRef) {
        this.pipelineRef = pipelineRef;
    }

    @Override
    public boolean isConstrained() {
        return pipelineRef != null;
    }

    public DocRef getPipeline() {
        return pipelineRef;
    }

    public void setPipeline(final DocRef pipelineRef) {
        this.pipelineRef = pipelineRef;
    }

    @Override
    public String getTaskName() {
        return "FetchProcessorAction - fetchProcessor()";
    }

    @Override
    public void setRowExpanded(final SharedObject row, final boolean open) {
        if (open) {
            if (expandedRows == null) {
                expandedRows = new HashSet<>();
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
