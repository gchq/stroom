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
import stroom.pipeline.spring.PipelineConfiguration;
import stroom.spring.MetaDataStatisticConfiguration;
import stroom.spring.PersistenceConfiguration;
import stroom.spring.ScopeConfiguration;
import stroom.spring.ScopeTestConfiguration;
import stroom.spring.ServerComponentScanConfiguration;
import stroom.spring.ServerComponentScanTestConfiguration;
import stroom.spring.ServerConfiguration;
import stroom.statistics.spring.StatisticsConfiguration;

@Configuration
@Import({
//        DashboardConfiguration.class,
//        EventLoggingConfiguration.class,
//        IndexConfiguration.class,
        MetaDataStatisticConfiguration.class,
        PersistenceConfiguration.class,
//        DictionaryConfiguration.class,
        PipelineConfiguration.class,
//        RuleSetConfiguration.class,
        ScopeConfiguration.class,
        ScopeTestConfiguration.class,
//        ScriptConfiguration.class,
//        SearchConfiguration.class,
//        SecurityConfiguration.class,
//        ExplorerConfiguration.class,
        ServerComponentScanTestConfiguration.class,
        ServerConfiguration.class,
        StatisticsConfiguration.class
//        ,
//        VisualisationConfiguration.class
})
class AbstractCoreIntegrationTestSpringConfig {
}