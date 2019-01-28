/*
 * Copyright 2017 Crown Copyright
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

package stroom.pipeline.shared.stepping;

import stroom.task.shared.Action;
import stroom.explorer.shared.SharedDocRef;

public class GetPipelineForMetaAction extends Action<SharedDocRef> {
    private static final long serialVersionUID = -1773544031158236156L;

    private Long metaId;
    private Long childMetaId;

    public GetPipelineForMetaAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public GetPipelineForMetaAction(final Long metaId, final Long childMetaId) {
        this.metaId = metaId;
        this.childMetaId = childMetaId;
    }

    public Long getMetaId() {
        return metaId;
    }

    public Long getChildMetaId() {
        return childMetaId;
    }

    @Override
    public String getTaskName() {
        return "GetPipelineForStreamAction";
    }
}
