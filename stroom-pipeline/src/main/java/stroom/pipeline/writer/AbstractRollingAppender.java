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

package stroom.pipeline.writer;

import stroom.pipeline.destination.Destination;
import stroom.pipeline.destination.RollingDestination;
import stroom.pipeline.destination.RollingDestinationFactory;
import stroom.pipeline.destination.RollingDestinations;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.PipelineFactoryException;
import stroom.pipeline.factory.PipelineProperty;
import stroom.util.shared.ModelStringUtil;
import stroom.task.TaskContext;

import java.io.IOException;

public abstract class AbstractRollingAppender extends AbstractDestinationProvider implements RollingDestinationFactory {
    private static final int MB = 1024 * 1024;
    private static final int DEFAULT_MAX_SIZE = 100 * MB;

    private static final int SECOND = 1000;
    private static final int MINUTE = 60 * SECOND;
    private static final int HOUR = 60 * MINUTE;

    private long frequency = HOUR;
    private long maxSize = DEFAULT_MAX_SIZE;

    private boolean validatedSettings;

    private final RollingDestinations destinations;
    private final TaskContext taskContext;

    public AbstractRollingAppender(final RollingDestinations destinations,
                                   final TaskContext taskContext) {
        this.destinations = destinations;
        this.taskContext = taskContext;
        this.validatedSettings = false;
    }

    @Override
    public Destination borrowDestination() throws IOException {
        validateSettings();

        // Get a key to use for the destination.
        final Object key = getKey();

        // Send off this record to be written to a destination.
        return destinations.borrow(taskContext, key, this);
    }

    @Override
    public void returnDestination(final Destination destination) throws IOException {
        destinations.returnDestination((RollingDestination) destination);
    }

    private void validateSettings() {
        if (!validatedSettings) {
            validatedSettings = true;

            if (frequency <= 0) {
                throw new ProcessException("Rolling frequency must be greater than 0");
            }

            if (maxSize <= 0) {
                throw new ProcessException("Max size must be greater than 0");
            }

            this.validateSpecificSettings();
        }
    }

    protected long getFrequency() {
        return frequency;
    }

    protected long getMaxSize() {
        return maxSize;
    }

    /**
     * Child classes can add checks for their specific fields, the child class can assume
     * this is only being called once.
     */
    protected abstract void validateSpecificSettings();

    /**
     * Child classes will have their own schemes for generating a key.
     *
     * @return The key to refer to the appender.
     * @throws IOException If anything goes wrong during key construction, it can call out to external property services
     */
    protected abstract Object getKey() throws IOException;

    @PipelineProperty(description = "Choose how frequently files are rolled.", defaultValue = "1h")
    public void setFrequency(final String frequency) {
        if (frequency != null && frequency.trim().length() > 0) {
            try {
                final Long value = ModelStringUtil.parseDurationString(frequency);
                if (value == null) {
                    throw new PipelineFactoryException("Incorrect value for frequency: " + frequency);
                }

                this.frequency = value;
            } catch (final NumberFormatException e) {
                throw new PipelineFactoryException("Incorrect value for frequency: " + frequency);
            }
        }
    }

    @PipelineProperty(description = "Choose the maximum size that a file can be before it is rolled, e.g. 10M, 1G.", defaultValue = "100M")
    public void setMaxSize(final String maxSize) {
        if (maxSize != null && maxSize.trim().length() > 0) {
            try {
                final Long value = ModelStringUtil.parseIECByteSizeString(maxSize);
                if (value == null) {
                    throw new PipelineFactoryException("Incorrect value for max size: " + maxSize);
                }

                this.maxSize = value;
            } catch (final NumberFormatException e) {
                throw new PipelineFactoryException("Incorrect value for max size: " + maxSize);
            }
        }
    }
}
