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

import stroom.pipeline.destination.Destination;
import stroom.pipeline.destination.RollingDestination;
import stroom.pipeline.destination.RollingDestinationFactory;
import stroom.pipeline.destination.RollingDestinations;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.factory.PipelineFactoryException;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.util.shared.ModelStringUtil;
import stroom.util.task.TaskMonitor;

import javax.annotation.Resource;
import java.io.IOException;

public abstract class AbstractRollingAppender extends AbstractDestinationProvider implements RollingDestinationFactory {
    private static final int MB = 1024 * 1024;
    private static final int DEFAULT_ROLL_SIZE = 100 * MB;

    private static final int SECOND = 1000;
    private static final int MINUTE = 60 * SECOND;
    private static final int HOUR = 60 * MINUTE;

    private long frequency = HOUR;
    private long rollSize = DEFAULT_ROLL_SIZE;

    private boolean validatedSettings;

    @Resource
    private RollingDestinations destinations;
    @Resource
    private TaskMonitor taskMonitor;

    AbstractRollingAppender() {
        this.validatedSettings = false;
    }

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

    private void validateSettings() {
        if (!validatedSettings) {
            validatedSettings = true;

            if (frequency <= 0) {
                throw new ProcessException("Rolling frequency must be greater than 0");
            }

            if (rollSize <= 0) {
                throw new ProcessException("Roll size must be greater than 0");
            }

            this.validateSpecificSettings();
        }
    }

    long getFrequency() {
        return frequency;
    }

    long getRollSize() {
        return rollSize;
    }

    /**
     * Child classes can add checks for their specific fields, the child class can assume
     * this is only being called once.
     */
    abstract void validateSpecificSettings();

    /**
     * Child classes will have their own schemes for generating a key.
     *
     * @return The key to refer to the appender.
     * @throws IOException If anything goes wrong during key construction, it can call out to external property services
     */
    abstract Object getKey() throws IOException;

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

    void setRollSize(final String rollSize) {
        if (rollSize != null && rollSize.trim().length() > 0) {
            try {
                final Long value = ModelStringUtil.parseIECByteSizeString(rollSize);
                if (value == null) {
                    throw new PipelineFactoryException("Incorrect value for roll size: " + rollSize);
                }

                this.rollSize = value;
            } catch (final NumberFormatException e) {
                throw new PipelineFactoryException("Incorrect value for roll size: " + rollSize);
            }
        }
    }
}
