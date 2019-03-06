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

package stroom.job.impl;

import event.logging.BaseObject;
import event.logging.Object;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.event.logging.api.ObjectInfoProvider;
import stroom.job.shared.JobNode;

class JobNodeObjectInfoProvider implements ObjectInfoProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobNodeObjectInfoProvider.class);

    @Override
    public BaseObject createBaseObject(final java.lang.Object obj) {
        final JobNode jobNode = (JobNode) obj;

        final Object object = new Object();
        object.setType("JobNode");
        object.setId(String.valueOf(jobNode.getId()));

        if (jobNode.getJob() != null) {
            object.setName(jobNode.getJob().getName());
        }

        try {
            object.getData()
                    .add(EventLoggingUtil.createData("Enabled", String.valueOf(jobNode.isEnabled())));
            object.getData()
                    .add(EventLoggingUtil.createData("Node Name", jobNode.getNodeName()));
            object.getData()
                    .add(EventLoggingUtil.createData("Schedule", jobNode.getSchedule()));
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to add unknown but useful data!", e);
        }

        return object;
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        return object.getClass().getSimpleName();
    }
}
