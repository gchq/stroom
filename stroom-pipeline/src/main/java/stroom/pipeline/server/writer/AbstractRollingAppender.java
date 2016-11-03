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

package stroom.pipeline.server.writer;

import java.io.IOException;

import javax.annotation.Resource;

import stroom.pipeline.destination.Destination;
import stroom.pipeline.destination.RollingDestination;
import stroom.pipeline.destination.RollingDestinationFactory;
import stroom.pipeline.destination.RollingDestinations;
import stroom.util.task.TaskMonitor;

public abstract class AbstractRollingAppender extends AbstractDestinationProvider implements RollingDestinationFactory {
    @Resource
    private RollingDestinations destinations;
    @Resource
    private TaskMonitor taskMonitor;

    @Override
    public Destination borrowDestination() throws IOException {
        validateSettings();

        // Get a key to use for the destination.
        final Object key = getKey();

        // Send off this record to be written to a destination.
        return destinations.borrow(taskMonitor, key, this);
    }

    @Override
    public void returnDestination(final Destination destination) throws IOException {
        destinations.returnDestination((RollingDestination) destination);
    }

    abstract void validateSettings();

    abstract Object getKey() throws IOException;
}
