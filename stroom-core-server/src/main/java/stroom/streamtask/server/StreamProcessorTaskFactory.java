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

package stroom.streamtask.server;

import stroom.jobsystem.server.DistributedTaskFactory;
import stroom.jobsystem.server.DistributedTaskFactoryBean;
import stroom.node.shared.Node;
import stroom.streamtask.shared.StreamTask;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@DistributedTaskFactoryBean(jobName = StreamProcessorTask.JOB_NAME, description = "Job to process streams matching stream processor filters with their associated pipelines")
public class StreamProcessorTaskFactory implements DistributedTaskFactory<StreamProcessorTask, VoidResult> {
    private final StreamTaskCreator streamTaskCreator;

    @Inject
    StreamProcessorTaskFactory(final StreamTaskCreator streamTaskCreator) {
        this.streamTaskCreator = streamTaskCreator;
    }

    @Override
    public List<StreamProcessorTask> fetch(final Node node, final int count) {
        final List<StreamTask> streamTasks = streamTaskCreator.assignStreamTasks(node, count);
        return wrap(streamTasks);
    }

    @Override
    public void abandon(final Node node, final List<StreamProcessorTask> tasks) {
        final List<StreamTask> streamTasks = unwrap(tasks);
        streamTaskCreator.abandonStreamTasks(node, streamTasks);
    }

    /**
     * Wrap stream tasks with stream processor tasks.
     */
    private List<StreamProcessorTask> wrap(final List<StreamTask> in) {
        List<StreamProcessorTask> out = Collections.emptyList();
        if (in != null && in.size() > 0) {
            out = new ArrayList<>(in.size());
            for (final StreamTask task : in) {
                out.add(new StreamProcessorTask(task));
            }
        }
        return out;
    }

    /**
     * Unwrap stream processor tasks and get a list of stream tasks.
     */
    private List<StreamTask> unwrap(final List<StreamProcessorTask> in) {
        List<StreamTask> out = Collections.emptyList();
        if (in != null && in.size() > 0) {
            out = new ArrayList<>(in.size());
            for (final StreamProcessorTask task : in) {
                out.add(task.getStreamTask());
            }
        }
        return out;
    }
}
