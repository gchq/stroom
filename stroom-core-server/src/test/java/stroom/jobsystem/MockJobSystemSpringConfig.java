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

package stroom.jobsystem;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import stroom.jobsystem.server.MockClusterLockService;
import stroom.jobsystem.server.MockJobManager;
import stroom.jobsystem.server.MockJobNodeService;
import stroom.jobsystem.server.MockJobService;
import stroom.jobsystem.server.MockScheduleService;
import stroom.util.spring.StroomSpringProfiles;

@Configuration
public class MockJobSystemSpringConfig {
    @Bean
    @Profile(StroomSpringProfiles.TEST)
    public MockClusterLockService mockClusterLockService() {
        return new MockClusterLockService();
    }

    @Bean
    @Profile(StroomSpringProfiles.TEST)
    public MockJobManager mockJobManager() {
        return new MockJobManager();
    }

    @Bean
    @Profile(StroomSpringProfiles.TEST)
    public MockJobNodeService mockJobNodeService() {
        return new MockJobNodeService();
    }

    @Bean
    @Profile(StroomSpringProfiles.TEST)
    public MockJobService mockJobService() {
        return new MockJobService();
    }

    @Bean
    @Profile(StroomSpringProfiles.TEST)
    public MockScheduleService mockScheduleService() {
        return new MockScheduleService();
    }
}