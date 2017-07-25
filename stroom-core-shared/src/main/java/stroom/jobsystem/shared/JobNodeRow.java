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

package stroom.jobsystem.shared;

import stroom.entity.shared.EntityRow;
import stroom.util.shared.Expander;
import stroom.util.shared.TreeRow;

public class JobNodeRow extends EntityRow<JobNode> implements TreeRow {
    private static final long serialVersionUID = 3306590492924959915L;

    private static final Expander EXPANDER = new Expander(2, false, true);

    private JobNodeInfo jobNodeInfo;

    public JobNodeRow() {
        // Default constructor necessary for GWT serialisation.
    }

    public JobNodeRow(final JobNode jobNode, final JobNodeInfo jobNodeInfo) {
        super(jobNode);
        this.jobNodeInfo = jobNodeInfo;
    }

    public JobNodeInfo getJobNodeInfo() {
        return jobNodeInfo;
    }

    @Override
    public Expander getExpander() {
        return EXPANDER;
    }
}
