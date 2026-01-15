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

package stroom.util.db;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The aim of this class is to hold a system wide flag that indicates if all the Flyway DB migrations
 * (i.e. for all modules) have been run. It gets set to true during app startup when the bootstrap_lock
 * table is checked. This means we don't need to run all the Flyway checks on every boot if the build
 * version hasn't changed.
 * <p>
 * Is a static variable as it will be used before guice has done all the bindings so can't be
 * injected.
 */
public class DbMigrationState {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DbMigrationState.class);

    private static final AtomicBoolean HAVE_BOOTSTRAP_MIGRATIONS_BEEN_DONE = new AtomicBoolean(false);

    private DbMigrationState() {
    }

    /**
     * @return True if all the migrations have been performed
     */
    public static boolean haveBootstrapMigrationsBeenDone() {
        return HAVE_BOOTSTRAP_MIGRATIONS_BEEN_DONE.get();
    }

    /**
     * Call this once when all the migrations have been done or when you detect that they have previously
     * been run.
     */
    public static void markBootstrapMigrationsComplete() {
        LOGGER.debug("Marking DB migration as complete");

        final boolean didSet = HAVE_BOOTSTRAP_MIGRATIONS_BEEN_DONE.compareAndSet(false, true);

        if (!didSet) {
            throw new RuntimeException("Error marking DB migration as complete, already set to true.");
        }
    }
}
