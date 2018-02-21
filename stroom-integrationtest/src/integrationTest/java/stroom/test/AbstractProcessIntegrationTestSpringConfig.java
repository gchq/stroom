/*
 * Copyright 2018 Crown Copyright
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

package stroom.test;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import stroom.cluster.ClusterSpringConfig;
import stroom.pipeline.spring.PipelineConfiguration;
import stroom.spring.MetaDataStatisticConfiguration;
import stroom.spring.PersistenceConfiguration;
import stroom.spring.ProcessTestServerComponentScanConfiguration;
import stroom.spring.ScopeConfiguration;
import stroom.spring.ScopeTestConfiguration;
import stroom.spring.ServerComponentScanTestConfiguration;
import stroom.spring.ServerConfiguration;
import stroom.statistics.spring.StatisticsConfiguration;
import stroom.task.cluster.ClusterTaskSpringConfig;

@Configuration
@Import({
        ClusterSpringConfig.class,
        ClusterTaskSpringConfig.class,
        ScopeConfiguration.class,
        PersistenceConfiguration.class,
        ProcessTestServerComponentScanConfiguration.class,
        ServerConfiguration.class,
//        SecurityConfiguration.class,
//        ExplorerConfiguration.class,
//        DictionaryConfiguration.class,
        ScopeTestConfiguration.class,
//        EventLoggingConfiguration.class,
//        IndexConfiguration.class,
//        SearchConfiguration.class,
//        ScriptConfiguration.class,
//        VisualisationConfiguration.class,
//        DashboardConfiguration.class,
        StatisticsConfiguration.class
})
class AbstractProcessIntegrationTestSpringConfig {
}