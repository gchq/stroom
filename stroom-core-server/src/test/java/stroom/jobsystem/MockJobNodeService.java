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
 *
 */

package stroom.jobsystem;

import stroom.entity.MockEntityService;
import stroom.jobsystem.shared.FindJobNodeCriteria;
import stroom.jobsystem.shared.JobNode;

import javax.inject.Singleton;

@Singleton
public class MockJobNodeService extends MockEntityService<JobNode, FindJobNodeCriteria> implements JobNodeService {
    @Override
    public void startup() {
    }

    @Override
    public Class<JobNode> getEntityClass() {
        return JobNode.class;
    }
}
