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

package stroom.util.shared.scheduler;

public class CronExpressions {

    public static final CronExpression EVERY_MINUTE =
            new CronExpression("Every Minute", "0 * * * * ?");

    public static final CronExpression EVERY_5TH_MINUTE =
            new CronExpression("Every 5th Minute", "0 /5 * * * ?");

    public static final CronExpression EVERY_10TH_MINUTE =
            new CronExpression("Every 10th Minute", "0 /10 * * * ?");
    public static final CronExpression EVERY_30TH_MINUTE =
            new CronExpression("Every 30th Minute", "0 /30 * * * ?");
    public static final CronExpression EVERY_10_MINUTES =
            new CronExpression("Every 10 Minutes", "0 0,10,20,30,40,50 * * * ?");
    public static final CronExpression EVERY_10_MINUTES_ALTERNATE =
            new CronExpression("Every 10 Minutes Alternate", "0 5,15,25,35,45,55 * * * ?");

    public static final CronExpression EVERY_HOUR =
            new CronExpression("Every Hour", "0 0 * * * ?");
    public static final CronExpression EVERY_2_HOURS =
            new CronExpression("Every 2 Hours", "0 0 /2 * * ?");
    public static final CronExpression EVERY_6_HOURS =
            new CronExpression("Every 6 Hours", "0 0 /6 * * ?");
    public static final CronExpression EVERY_12_HOURS =
            new CronExpression("Every 12 Hours", "0 0 /12 * * ?");


    public static final CronExpression EVERY_DAY_AT_MIDNIGHT =
            new CronExpression("Every Day At Midnight", "0 0 0 * * ?");
    public static final CronExpression EVERY_DAY_AT_2AM =
            new CronExpression("Every Day At 2AM", "0 0 2 * * ?");
    public static final CronExpression EVERY_DAY_AT_3AM =
            new CronExpression("Every Day At 3AM", "0 0 3 * * ?");
    public static final CronExpression EVERY_DAY_AT_MIDDAY =
            new CronExpression("Every Day At Midday", "0 0 12 * * ?");


    public static final CronExpression EVERY_MONTH =
            new CronExpression("Every Month", "0 0 0 1 * ?");
    public static final CronExpression EVERY_OTHER_MONTH =
            new CronExpression("Every Other Month", "0 0 0 1 /2 ?");
    public static final CronExpression EVERY_6_MONTHS =
            new CronExpression("Every 6 Months", "0 0 0 1 6 ?");
    public static final CronExpression EVERY_YEAR =
            new CronExpression("Every Year", "0 0 0 1 1 ?");


    public static final CronExpression[] MINUTE = new CronExpression[]{
            EVERY_MINUTE,
            EVERY_5TH_MINUTE,
            EVERY_10TH_MINUTE,
            EVERY_30TH_MINUTE
    };

    public static final CronExpression[] HOUR = new CronExpression[]{
            EVERY_HOUR,
            EVERY_2_HOURS,
            EVERY_6_HOURS,
            EVERY_12_HOURS
    };

    public static final CronExpression[] DAY = new CronExpression[]{
            EVERY_DAY_AT_MIDNIGHT,
            EVERY_DAY_AT_2AM,
            EVERY_DAY_AT_3AM,
            EVERY_DAY_AT_MIDDAY
    };

    public static final CronExpression[] MONTH = new CronExpression[]{
            EVERY_MONTH,
            EVERY_OTHER_MONTH,
            EVERY_6_MONTHS,
            EVERY_YEAR
    };

    public static final CronExpression[] ALL = new CronExpression[]{
            EVERY_MINUTE,
            EVERY_5TH_MINUTE,
            EVERY_10TH_MINUTE,
            EVERY_30TH_MINUTE,
            EVERY_10_MINUTES,
            EVERY_10_MINUTES_ALTERNATE,
            EVERY_HOUR,
            EVERY_2_HOURS,
            EVERY_6_HOURS,
            EVERY_12_HOURS,
            EVERY_DAY_AT_MIDNIGHT,
            EVERY_DAY_AT_2AM,
            EVERY_DAY_AT_3AM,
            EVERY_DAY_AT_MIDDAY,
            EVERY_MONTH,
            EVERY_OTHER_MONTH,
            EVERY_6_MONTHS,
            EVERY_YEAR
    };
}
