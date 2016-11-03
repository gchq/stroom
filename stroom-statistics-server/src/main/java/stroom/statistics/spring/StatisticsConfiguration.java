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

package stroom.statistics.spring;

import java.util.Arrays;

import stroom.util.logging.StroomLogger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import stroom.statistic.server.MetaDataStatistic;
import stroom.statistics.server.common.MetaDataStatisticImpl;
import stroom.statistics.server.common.MetaDataStatisticTemplate;

@Configuration
public class StatisticsConfiguration {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StatisticsConfiguration.class);

    public StatisticsConfiguration() {
        LOGGER.info("StatisticsConfiguration loading...");
    }

    @Bean
    public MetaDataStatistic metaDataStatistic() {
        MetaDataStatisticImpl metaDataStatistic = new MetaDataStatisticImpl();
        metaDataStatistic.setTemplates(Arrays.asList(
                new MetaDataStatisticTemplate("Meta Data-Streams Received", "receivedTime", Arrays.asList("Feed")),
                new MetaDataStatisticTemplate("Meta Data-Stream Size", "receivedTime", "StreamSize",
                        Arrays.asList("Feed")),
                new MetaDataStatisticTemplate("Meta Data-Bytes Received", "receivedTime", "StreamSize",
                        Arrays.asList("Feed"))));
        return metaDataStatistic;
    }
}
