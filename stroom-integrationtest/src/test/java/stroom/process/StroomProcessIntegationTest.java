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

package stroom.process;

import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import stroom.StroomIntegrationTest;
import stroom.util.spring.StroomSpringProfiles;
import stroom.util.test.IntegrationTest;
import stroom.util.test.StroomSpringJUnit4ClassRunner;

@RunWith(StroomSpringJUnit4ClassRunner.class)
@Category(IntegrationTest.class)
@ActiveProfiles(StroomSpringProfiles.TEST)
@ContextConfiguration(locations = { "classpath:META-INF/spring/stroomCoreServerContext.xml",
        "classpath:META-INF/spring/stroomCoreServerLocalTestingContext.xml",
        "classpath:META-INF/spring/stroomCoreServerMockClusterContext.xml",
        "classpath:META-INF/spring/stroomProcessContext.xml",
        "classpath:META-INF/spring/stroomProcessComponentTestContext.xml" })
public abstract class StroomProcessIntegationTest extends StroomIntegrationTest {
}
