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

public class FrequencyExpressions {

    public static final FrequencyExpression EVERY_MINUTE =
            new FrequencyExpression("Every Minute", "1m");

    public static final FrequencyExpression EVERY_5_MINUTES =
            new FrequencyExpression("Every 5 Minutes", "5m");

    public static final FrequencyExpression EVERY_10_MINUTES =
            new FrequencyExpression("Every 10 Minutes", "10m");
    public static final FrequencyExpression EVERY_30_MINUTES =
            new FrequencyExpression("Every 30 Minutes", "30m");

    public static final FrequencyExpression EVERY_HOUR =
            new FrequencyExpression("Every Hour", "1h");
    public static final FrequencyExpression EVERY_2_HOURS =
            new FrequencyExpression("Every 2 Hours", "2h");
    public static final FrequencyExpression EVERY_6_HOURS =
            new FrequencyExpression("Every 6 Hours", "6h");
    public static final FrequencyExpression EVERY_12_HOURS =
            new FrequencyExpression("Every 12 Hours", "12h");


    public static final FrequencyExpression[] MINUTE = new FrequencyExpression[]{
            EVERY_MINUTE,
            EVERY_5_MINUTES,
            EVERY_10_MINUTES,
            EVERY_30_MINUTES
    };

    public static final FrequencyExpression[] HOUR = new FrequencyExpression[]{
            EVERY_HOUR,
            EVERY_2_HOURS,
            EVERY_6_HOURS,
            EVERY_12_HOURS
    };

    public static final FrequencyExpression[] ALL = new FrequencyExpression[]{
            EVERY_MINUTE,
            EVERY_5_MINUTES,
            EVERY_10_MINUTES,
            EVERY_30_MINUTES,
            EVERY_HOUR,
            EVERY_2_HOURS,
            EVERY_6_HOURS,
            EVERY_12_HOURS
    };
}
