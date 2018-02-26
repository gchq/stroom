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

package stroom.policy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.dictionary.DictionaryStore;
import stroom.entity.StroomEntityManager;
import stroom.jobsystem.ClusterLockService;
import stroom.properties.StroomPropertyService;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.sql.DataSource;

@Configuration
public class PolicySpringConfig {
    @Bean
    @Scope(value = StroomScope.TASK)
    public DataRetentionExecutor dataRetentionExecutor(final TaskMonitor taskMonitor,
                                                       final ClusterLockService clusterLockService,
                                                       final DataRetentionService dataRetentionService,
                                                       final StroomPropertyService propertyService,
                                                       final DictionaryStore dictionaryStore,
                                                       final DataSource dataSource) {
        return new DataRetentionExecutor(taskMonitor, clusterLockService, dataRetentionService, propertyService, dictionaryStore, dataSource);
    }

    @Bean
    public DataRetentionService dataRetentionService(final PolicyService policyService) {
        return new DataRetentionService(policyService);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public FetchDataRetentionPolicyHandler fetchDataRetentionPolicyHandler(final DataRetentionService dataRetentionService) {
        return new FetchDataRetentionPolicyHandler(dataRetentionService);
    }

    @Bean("policyService")
    public PolicyService policyService(final StroomEntityManager entityManager) {
        return new PolicyServiceImpl(entityManager);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public SaveDataRetentionPolicyHandler saveDataRetentionPolicyHandler(final DataRetentionService dataRetentionService) {
        return new SaveDataRetentionPolicyHandler(dataRetentionService);
    }
}