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

package stroom.test;

import org.junit.Before;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import stroom.cluster.server.ClusterSpringConfig;
import stroom.dictionary.spring.DictionaryConfiguration;
import stroom.logging.spring.EventLoggingConfiguration;
import stroom.dashboard.spring.DashboardConfiguration;
import stroom.explorer.server.ExplorerConfiguration;
import stroom.index.spring.IndexConfiguration;
import stroom.script.spring.ScriptConfiguration;
import stroom.search.spring.SearchConfiguration;
import stroom.security.spring.SecurityConfiguration;
import stroom.spring.PersistenceConfiguration;
import stroom.spring.ProcessTestServerComponentScanConfiguration;
import stroom.spring.ScopeConfiguration;
import stroom.spring.ScopeTestConfiguration;
import stroom.spring.ServerConfiguration;
import stroom.statistics.spring.StatisticsConfiguration;
import stroom.task.cluster.ClusterTaskSpringConfig;
import stroom.util.cache.CacheManagerSpringConfig;
import stroom.util.spring.StroomSpringProfiles;
import stroom.visualisation.spring.VisualisationConfiguration;

@ActiveProfiles(value = {
        StroomSpringProfiles.TEST,
        StroomSpringProfiles.IT,
        SecurityConfiguration.MOCK_SECURITY})
@ContextConfiguration(classes = {
        ClusterSpringConfig.class,
        ClusterTaskSpringConfig.class,
        ScopeConfiguration.class,
        PersistenceConfiguration.class,
        ProcessTestServerComponentScanConfiguration.class,
        ServerConfiguration.class,
        SecurityConfiguration.class,
        ExplorerConfiguration.class,
        DictionaryConfiguration.class,
        ScopeTestConfiguration.class,
        EventLoggingConfiguration.class,
        IndexConfiguration.class,
        SearchConfiguration.class,
        ScriptConfiguration.class,
        VisualisationConfiguration.class,
        DashboardConfiguration.class,
        StatisticsConfiguration.class})
public abstract class AbstractProcessIntegrationTest extends StroomIntegrationTest {

    @Before
    public void beforeTest() {
        super.importSchemas(true);
    }
}
