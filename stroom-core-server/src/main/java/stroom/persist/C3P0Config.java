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

package stroom.persist;

import stroom.properties.api.PropertyService;

import java.util.Objects;

public class C3P0Config {
    private static final int MAX_STATEMENTS = 0;
    private static final int MAX_STATEMENTS_PER_CONNECTION = 0;
    private static final int INITIAL_POOL_SIZE = 1;
    private static final int MIN_POOL_SIZE = 1;
    private static final int MAX_POOL_SIZE = 10;
    private static final int IDLE_CONNECTION_TEST_PERIOD = 0;  //idle connections never tested
    private static final int MAX_IDLE_TIME = 0;  //seconds, 0 means connections never expire
    private static final int ACQUIRE_INCREMENT = 1;
    private static final int ACQUIRE_RETRY_ATTEMPTS = 30;
    private static final int ACQUIRE_RETRY_DELAY = 1000; //milliseconds
    private static final int CHECKOUT_TIMEOUT = 0;    //milliseconds
    private static final int MAX_ADMINISTRATIVE_TASK_TIME = 0;    //seconds
    private static final int MAX_IDLE_TIME_EXCESS_CONNECTIONS = 0;    //seconds
    private static final int MAX_CONNECTION_AGE = 0;    //seconds
    private static final int UNRETURNED_CONNECTION_TIMEOUT = 0;    //seconds
    private static final int STATEMENT_CACHE_NUM_DEFERRED_CLOSE_THREADS = 0;
    private static final int NUM_HELPER_THREADS = 1;

    private static final String PROP_MAX_STATEMENTS = "maxStatements";
    private static final String PROP_MAX_STATEMENTS_PER_CONNECTION = "maxStatementsPerConnection";
    private static final String PROP_INITIAL_POOL_SIZE = "initialPoolSize";
    private static final String PROP_MIN_POOL_SIZE = "minPoolSize";
    private static final String PROP_MAX_POOL_SIZE = "maxPoolSize";
    private static final String PROP_IDLE_CONNECTION_TEST_PERIOD = "idleConnectionTestPeriod";
    private static final String PROP_MAX_IDLE_TIME = "maxIdleTime";
    private static final String PROP_ACQUIRE_INCREMENT = "acquireIncrement";
    private static final String PROP_ACQUIRE_RETRY_ATTEMPTS = "acquireRetryAttempts";
    private static final String PROP_ACQUIRE_RETRY_DELAY = "acquireRetryDelay";
    private static final String PROP_CHECKOUT_TIMEOUT = "checkoutTimeout";
    private static final String PROP_MAX_ADMINISTRATIVE_TASK_TIME = "maxAdministrativeTaskTime";
    private static final String PROP_MAX_IDLE_TIME_EXCESS_CONNECTIONS = "maxIdleTimeExcessConnections";
    private static final String PROP_MAX_CONNECTION_AGE = "maxConnectionAge";
    private static final String PROP_UNRETURNED_CONNECTION_TIMEOUT = "unreturnedConnectionTimeout";
    private static final String PROP_STATEMENT_CACHE_NUM_DEFERRED_CLOSE_THREADS = "statementCacheNumDeferredCloseThreads";
    private static final String PROP_NUM_HELPER_THREADS = "numHelperThreads";

    private final int maxStatements;
    private final int maxStatementsPerConnection;
    private final int initialPoolSize;
    private final int minPoolSize;
    private final int maxPoolSize;
    private final int idleConnectionTestPeriod;
    private final int maxIdleTime;
    private final int acquireIncrement;
    private final int acquireRetryAttempts;
    private final int acquireRetryDelay;
    private final int checkoutTimeout;
    private final int maxAdministrativeTaskTime;
    private final int maxIdleTimeExcessConnections;
    private final int maxConnectionAge;
    private final int unreturnedConnectionTimeout;
    private final int statementCacheNumDeferredCloseThreads;
    private final int numHelperThreads;

    public C3P0Config(final String prefix, final PropertyService propertyService) {
        this.maxStatements = propertyService.getIntProperty(prefix + PROP_MAX_STATEMENTS, MAX_STATEMENTS);
        this.maxStatementsPerConnection = propertyService.getIntProperty(prefix + PROP_MAX_STATEMENTS_PER_CONNECTION, MAX_STATEMENTS_PER_CONNECTION);
        this.initialPoolSize = propertyService.getIntProperty(prefix + PROP_INITIAL_POOL_SIZE, INITIAL_POOL_SIZE);
        this.minPoolSize = propertyService.getIntProperty(prefix + PROP_MIN_POOL_SIZE, MIN_POOL_SIZE);
        this.maxPoolSize = propertyService.getIntProperty(prefix + PROP_MAX_POOL_SIZE, MAX_POOL_SIZE);
        this.idleConnectionTestPeriod = propertyService.getIntProperty(prefix + PROP_IDLE_CONNECTION_TEST_PERIOD, IDLE_CONNECTION_TEST_PERIOD);
        this.maxIdleTime = propertyService.getIntProperty(prefix + PROP_MAX_IDLE_TIME, MAX_IDLE_TIME);
        this.acquireIncrement = propertyService.getIntProperty(prefix + PROP_ACQUIRE_INCREMENT, ACQUIRE_INCREMENT);
        this.acquireRetryAttempts = propertyService.getIntProperty(prefix + PROP_ACQUIRE_RETRY_ATTEMPTS, ACQUIRE_RETRY_ATTEMPTS);
        this.acquireRetryDelay = propertyService.getIntProperty(prefix + PROP_ACQUIRE_RETRY_DELAY, ACQUIRE_RETRY_DELAY);
        this.checkoutTimeout = propertyService.getIntProperty(prefix + PROP_CHECKOUT_TIMEOUT, CHECKOUT_TIMEOUT);
        this.maxAdministrativeTaskTime = propertyService.getIntProperty(prefix + PROP_MAX_ADMINISTRATIVE_TASK_TIME, MAX_ADMINISTRATIVE_TASK_TIME);
        this.maxIdleTimeExcessConnections = propertyService.getIntProperty(prefix + PROP_MAX_IDLE_TIME_EXCESS_CONNECTIONS, MAX_IDLE_TIME_EXCESS_CONNECTIONS);
        this.maxConnectionAge = propertyService.getIntProperty(prefix + PROP_MAX_CONNECTION_AGE, MAX_CONNECTION_AGE);
        this.unreturnedConnectionTimeout = propertyService.getIntProperty(prefix + PROP_UNRETURNED_CONNECTION_TIMEOUT, UNRETURNED_CONNECTION_TIMEOUT);
        this.statementCacheNumDeferredCloseThreads = propertyService.getIntProperty(prefix + PROP_STATEMENT_CACHE_NUM_DEFERRED_CLOSE_THREADS, STATEMENT_CACHE_NUM_DEFERRED_CLOSE_THREADS);
        this.numHelperThreads = propertyService.getIntProperty(prefix + PROP_NUM_HELPER_THREADS, NUM_HELPER_THREADS);
    }

    public int getMaxStatements() {
        return maxStatements;
    }

    public int getMaxStatementsPerConnection() {
        return maxStatementsPerConnection;
    }

    public int getInitialPoolSize() {
        return initialPoolSize;
    }

    public int getMinPoolSize() {
        return minPoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public int getIdleConnectionTestPeriod() {
        return idleConnectionTestPeriod;
    }

    public int getMaxIdleTime() {
        return maxIdleTime;
    }

    public int getAcquireIncrement() {
        return acquireIncrement;
    }

    public int getAcquireRetryAttempts() {
        return acquireRetryAttempts;
    }

    public int getAcquireRetryDelay() {
        return acquireRetryDelay;
    }

    public int getCheckoutTimeout() {
        return checkoutTimeout;
    }

    public int getMaxAdministrativeTaskTime() {
        return maxAdministrativeTaskTime;
    }

    public int getMaxIdleTimeExcessConnections() {
        return maxIdleTimeExcessConnections;
    }

    public int getMaxConnectionAge() {
        return maxConnectionAge;
    }

    public int getUnreturnedConnectionTimeout() {
        return unreturnedConnectionTimeout;
    }

    public int getStatementCacheNumDeferredCloseThreads() {
        return statementCacheNumDeferredCloseThreads;
    }

    public int getNumHelperThreads() {
        return numHelperThreads;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final C3P0Config that = (C3P0Config) o;
        return maxStatements == that.maxStatements &&
                maxStatementsPerConnection == that.maxStatementsPerConnection &&
                initialPoolSize == that.initialPoolSize &&
                minPoolSize == that.minPoolSize &&
                maxPoolSize == that.maxPoolSize &&
                idleConnectionTestPeriod == that.idleConnectionTestPeriod &&
                maxIdleTime == that.maxIdleTime &&
                acquireIncrement == that.acquireIncrement &&
                acquireRetryAttempts == that.acquireRetryAttempts &&
                acquireRetryDelay == that.acquireRetryDelay &&
                checkoutTimeout == that.checkoutTimeout &&
                maxAdministrativeTaskTime == that.maxAdministrativeTaskTime &&
                maxIdleTimeExcessConnections == that.maxIdleTimeExcessConnections &&
                maxConnectionAge == that.maxConnectionAge &&
                unreturnedConnectionTimeout == that.unreturnedConnectionTimeout &&
                statementCacheNumDeferredCloseThreads == that.statementCacheNumDeferredCloseThreads &&
                numHelperThreads == that.numHelperThreads;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxStatements, maxStatementsPerConnection, initialPoolSize, minPoolSize, maxPoolSize, idleConnectionTestPeriod, maxIdleTime, acquireIncrement, acquireRetryAttempts, acquireRetryDelay, checkoutTimeout, maxAdministrativeTaskTime, maxIdleTimeExcessConnections, maxConnectionAge, unreturnedConnectionTimeout, statementCacheNumDeferredCloseThreads, numHelperThreads);
    }
}
