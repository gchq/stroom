/*
 * Copyright 2026 Crown Copyright
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

package stroom.externaldatasource.impl;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class TestQueryCancellation {

    private final Statement statement = Mockito.mock(Statement.class);
    private final QueryCancellation cancellation = new QueryCancellation();

    @Test
    void cancelStopsTheRunningStatement() throws SQLException {
        cancellation.register(statement);

        cancellation.cancel();

        Mockito.verify(statement).cancel();
        assertThat(cancellation.isCancelled()).isTrue();
    }

    @Test
    void statementRegisteredAfterCancelIsStoppedAtOnce() throws SQLException {
        cancellation.cancel();

        cancellation.register(statement);

        Mockito.verify(statement).cancel();
    }

    @Test
    void finishedStatementIsNotCancelled() throws SQLException {
        cancellation.register(statement);
        cancellation.unregister();

        cancellation.cancel();

        Mockito.verify(statement, Mockito.never()).cancel();
        assertThat(cancellation.isCancelled()).isTrue();
    }

    @Test
    void failureToCancelIsNotThrown() throws SQLException {
        Mockito.doThrow(new SQLException("not supported")).when(statement).cancel();
        cancellation.register(statement);

        cancellation.cancel();

        assertThat(cancellation.isCancelled()).isTrue();
    }

    @Test
    void cancelRunsAnyStopAction() {
        // Such as stopping an Amazon Athena query execution.
        final int[] stops = new int[1];
        cancellation.register(() -> stops[0]++);

        cancellation.cancel();

        assertThat(stops[0]).isEqualTo(1);
    }

    @Test
    void failingStopActionIsNotThrown() {
        cancellation.register(() -> {
            throw new IllegalStateException("query already finished");
        });

        cancellation.cancel();

        assertThat(cancellation.isCancelled()).isTrue();
    }

    @Test
    void notCancelledToBeginWith() {
        assertThat(cancellation.isCancelled()).isFalse();
    }
}
