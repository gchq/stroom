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

package stroom.util.scheduler;

import org.quartz.CronScheduleBuilder;
import org.quartz.TriggerBuilder;

import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;

public class CronTrigger implements Trigger {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private final org.quartz.CronTrigger cronTrigger;

    public CronTrigger(final String expression) {
        final String converted = QuartzCronUtil.convertLegacy(expression);
        cronTrigger = TriggerBuilder.newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule(converted).inTimeZone(UTC))
                .startAt(Date.from(Instant.ofEpochMilli(0)))
                .build();
    }

    @Override
    public Instant getNextExecutionTimeAfter(final Instant afterTime) {
        final Date now = Date.from(afterTime);
        final Date next = cronTrigger.getFireTimeAfter(now);
        if (next == null) {
            return null;
        }
        return next.toInstant();
    }
}
