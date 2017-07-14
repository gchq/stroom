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

/**
 * Common Test Utility that among other things tears down things before the
 * tests run.
 */
public interface CommonTestControl {
    void setup();

    void teardown();

    void createRequiredXMLSchemas();

    /**
     * Shutdown database.
     */
    void shutdown();
    //
    // public void clearContext();

    /**
     * Return a entity count.
     *
     * @param clazz
     *            to count
     * @return the count
     */
    int countEntity(Class<?> clazz);

    void deleteEntity(Class<?> clazz);
}
