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

package stroom.app.db.migration;

/**
 * A superclass for all Junits that each test a subclass of {@link AbstractCrossModuleJavaDbMigration}.
 */
public interface CrossModuleMigrationTest {

//    /**
//     * @return The class that will set up any test data prior to the migration that is being tested.
//     * Said class will be instantiated by guice so constructor injection can be used to
//     * pass any data sources it needs. Its constructor must inject {@link TestState} and pass to the
//     * superclass.
//     */
//    Class<? extends AbstractCrossModuleMigrationTestData> getTestDataClass();
}
