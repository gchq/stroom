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

package stroom.job.shared;

import stroom.util.shared.BaseCriteria;
import stroom.entity.shared.EntityIdSet;
import stroom.node.shared.Node;
import stroom.util.shared.CriteriaSet;
import stroom.util.shared.IdSet;
import stroom.util.shared.StringCriteria;

/**
 * Criteria object used to fetch a job that matches the parameters specified.
 */
public class FindJobNodeCriteria extends BaseCriteria {
    private static final long serialVersionUID = 71512228011037748L;

    private StringCriteria jobName = new StringCriteria();
    private StringCriteria nodeName = new StringCriteria();

    public StringCriteria getJobName() {
        return jobName;
    }

    public StringCriteria getNodeName() {
        return nodeName;
    }
}
