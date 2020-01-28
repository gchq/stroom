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

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import javax.inject.Inject;
import java.nio.file.Path;

@ExtendWith(GuiceExtension.class)
@IncludeModule(MockServiceModule.class)
public abstract class AbstractProcessIntegrationTest extends StroomIntegrationTest {
    @Inject
    private IntegrationTestSetupUtil integrationTestSetupUtil;

    @BeforeEach
    void before(final TestInfo testInfo, @TempDir final Path tempDir) {
        super.before(testInfo, tempDir);
        integrationTestSetupUtil.importSchemas(true);
    }
}