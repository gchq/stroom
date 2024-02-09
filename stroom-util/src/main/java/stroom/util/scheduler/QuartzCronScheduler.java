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

package stroom.util.scheduler;

import stroom.util.date.DateUtil;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.TriggerBuilder;

import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;

public class QuartzCronScheduler implements Scheduler {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

//    private final SimpleCron simpleCron;

    private final CronTrigger cronTrigger;
    private Long lastExecute;
    private Long nextExecute;

    public QuartzCronScheduler(final String expression) {
////        this.simpleCron = SimpleCron.compile(expression);
//
//        CronTrigger trigger = TriggerBuilder.newTrigger()
////                .withIdentity("trigger3", "group1")
//                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/2 8-17 * * ?"))
////                .forJob("myJob", "group1")
//                .build();

        final String converted = QuartzCronUtil.convertLegacy(expression);
        cronTrigger = TriggerBuilder.newTrigger()
//                .withIdentity("trigger3", "group1")

                .withSchedule(CronScheduleBuilder.cronSchedule(converted).inTimeZone(UTC))
                .startAt(Date.from(Instant.ofEpochMilli(0)))
//                .forJob("myJob", "group1")
                .build();

    }

    /**
     * @return date to aid testing.
     */
    protected Long getCurrentTime() {
        return System.currentTimeMillis();
    }

    /**
     * Should we execute.
     *
     * @return
     */
    public boolean execute(final long timeNow) {
        if (nextExecute == null) {
            nextExecute = getNextExecute(timeNow);
        } else if (timeNow > nextExecute) {
            nextExecute = getNextExecute(timeNow);
            lastExecute = timeNow;
            return true;
        }
        return false;
    }

    public Long getNextExecute(final long timeNow) {
        final Date now = Date.from(Instant.ofEpochMilli(timeNow));
        final Date next = cronTrigger.getFireTimeAfter(now);
        if (next != null) {
            return next.getTime();
        }
        return null;
    }

    /**
     * Should we execute.
     *
     * @return
     */
    @Override
    public boolean execute() {
        return execute(getCurrentTime());
    }

    @Override
    public Long getScheduleReferenceTime() {
        if (lastExecute != null) {
            return lastExecute;
        }

        return System.currentTimeMillis();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SimpleCron ");
        if (lastExecute != null) {
            sb.append("lastExecute=\"");
            sb.append(DateUtil.createNormalDateTimeString(lastExecute));
            sb.append("\" ");
        }
        if (nextExecute != null) {
            sb.append("nextExecute=\"");
            sb.append(DateUtil.createNormalDateTimeString(nextExecute));
            sb.append("\" ");
        }
        return sb.toString();
    }
}
