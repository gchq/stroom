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

package stroom.entity.server.util;

import com.mchange.v2.c3p0.QueryConnectionTester;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * During JUnit tests Hibernate auto generates the database schema in HSQLDB due
 * to the property 'hibernate.hbm2ddl.auto' via 'stroom.jpaHbm2DdlAuto' being set
 * to 'update'. This is desired behaviour for unit tests but HSQLDB throws an
 * exception during schema generation as Hibernate attempts to drop some non
 * existent database constraints. Hibernate expects that some databases will
 * throw an exception so it tries to handle the exception quietly, however this
 * exception causes the C3P0 connection pool to validate the DB connection which
 * it does on all statement errors. The default C3P0 connection tester tries to
 * execute a simple SQL statement to test the connection, e.g. 'select 1'. This
 * statement is not supported by HSQLDB so also fails and invalidates the C3P0
 * connection.
 *
 * To fix this issue for unit testing there are several possible solutions:
 *
 * 1. Create the HSQLDB schema from our own DDL script and set
 * 'hibernate.hbm2ddl.auto' to validate.
 *
 * 2. Change the connection tester 'preferredTestQuery' to be DB specific.
 * 'select 1' works OK for MySql in production but HSQLDB would need something
 * like 'SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS'.
 *
 * 3. Use our own connection tester in JUnit tests, i.e. this class, that just
 * defers connection testing to the JDBC connection.isValid() method.
 *
 * 4. We could avoid the use of C3P0 connection pools in unit tests.
 *
 * For now we will choose option 3 as it is the simplest to implement.
 */
public class StroomConnectionTesterOkOnException implements QueryConnectionTester {
    private static final long serialVersionUID = 2572460567240428803L;

    @Override
    public int activeCheckConnection(final Connection c) {
        try {
            if (c.isValid(10)) {
                return CONNECTION_IS_OKAY;
            }
        } catch (final SQLException e) {
        }

        return CONNECTION_IS_INVALID;
    }

    @Override
    public int statusOnException(final Connection c, final Throwable t) {
        try {
            if (c.isValid(10)) {
                return CONNECTION_IS_OKAY;
            }
        } catch (final SQLException e) {
        }

        return CONNECTION_IS_INVALID;
    }

    @Override
    public int activeCheckConnection(final Connection c, final String preferredTestQuery) {
        try {
            if (c.isValid(10)) {
                return CONNECTION_IS_OKAY;
            }
        } catch (final SQLException e) {
        }

        return CONNECTION_IS_INVALID;
    }

    @Override
    public boolean equals(final Object o) {
        return (o instanceof StroomConnectionTesterOkOnException);
    }

    @Override
    public int hashCode() {
        return 1;
    }
}
