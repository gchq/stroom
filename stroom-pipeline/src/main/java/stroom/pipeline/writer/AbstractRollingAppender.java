/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.util.scheduler.CronTrigger;
import stroom.util.scheduler.FrequencyTrigger;
import stroom.util.scheduler.Trigger;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.scheduler.FrequencyExpressions;

import java.io.IOException;

public abstract class AbstractRollingAppender extends AbstractDestinationProvider implements RollingDestinationFactory {

    private static final int MB = 1024 * 1024;
    private static final int DEFAULT_ROLL_SIZE = 100 * MB;

    private static final long SECOND = 1000;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;

    private FrequencyTrigger frequencyTrigger;
    private CronTrigger cronTrigger;
    private long rollSize = DEFAULT_ROLL_SIZE;

    private boolean validatedSettings;

    private final RollingDestinations destinations;

    protected AbstractRollingAppender(final RollingDestinations destinations) {
        this.destinations = destinations;
        this.validatedSettings = false;
    }

    @Override
    public Destination borrowDestination() throws IOException {
        validateSettings();

        // See if the task has been terminated.
        checkTermination();

        // Get a key to use for the destination.
        final Object key = getKey();

        // Send off this record to be written to a destination.
        return destinations.borrow(key, this);
    }

    @Override
    public void returnDestination(final Destination destination) {
        destinations.returnDestination((RollingDestination) destination);
    }

    private void validateSettings() {
        if (!validatedSettings) {
            validatedSettings = true;

            if (frequencyTrigger == null && cronTrigger == null) {
                // Default the frequency to an hour if there is no schedule.
                frequencyTrigger = new FrequencyTrigger(FrequencyExpressions.EVERY_HOUR.getExpression());
            }

            if (rollSize <= 0) {
                throw ProcessException.create("Roll size must be greater than 0");
            }

            this.validateSpecificSettings();
        }
    }

    protected Trigger getFrequencyTrigger() {
        return frequencyTrigger;
    }

    protected Trigger getCronTrigger() {
        return cronTrigger;
    }

    protected long getRollSize() {
        return rollSize;
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

    protected void setFrequency(final String frequency) {
        if (frequency == null || frequency.isBlank()) {
            this.frequencyTrigger = null;
        } else {
            try {
                this.frequencyTrigger = new FrequencyTrigger(frequency);
            } catch (final NumberFormatException e) {
                throw new PipelineFactoryException("Incorrect value for frequency: " + frequency);
            }
        }
    }

    protected void setSchedule(final String expression) {
        if (expression == null || expression.isBlank()) {
            this.cronTrigger = null;
        } else {
            try {
                this.cronTrigger = new CronTrigger(expression);
            } catch (final NumberFormatException e) {
                throw new PipelineFactoryException("Incorrect value for schedule: " + expression);
            }
        }
    }

    protected void setRollSize(final String rollSize) {
        if (rollSize != null && !rollSize.isBlank()) {
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
