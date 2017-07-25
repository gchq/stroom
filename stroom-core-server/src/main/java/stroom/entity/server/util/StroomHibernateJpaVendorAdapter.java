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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.ConnectionHandle;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.ArrayDeque;

/**
 * TODO: Not sure we need this anymore
 */
public class StroomHibernateJpaVendorAdapter extends HibernateJpaVendorAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomHibernateJpaVendorAdapter.class);

    static ThreadLocal<ArrayDeque<StackTraceElement[]>> threadTransactionStack = new ThreadLocal<ArrayDeque<StackTraceElement[]>>();
    private HibernateJpaDialect jpaDialect = new StroomHibernateJpaDialect();

    @Override
    public HibernateJpaDialect getJpaDialect() {
        return jpaDialect;
    }

    public static class StroomHibernateJpaDialect extends HibernateJpaDialect {
        private static final long serialVersionUID = 1L;

        public ConnectionHandle getJdbcConnection(javax.persistence.EntityManager entityManager, boolean readOnly)
                throws javax.persistence.PersistenceException, java.sql.SQLException {
            ConnectionHandle connectionHandle = super.getJdbcConnection(entityManager, readOnly);
            connectionHandle.getConnection().setReadOnly(readOnly);
            return connectionHandle;
        }

        @Override
        public Object beginTransaction(EntityManager entityManager, TransactionDefinition definition)
                throws PersistenceException, SQLException, TransactionException {
            ArrayDeque<StackTraceElement[]> stack = threadTransactionStack.get();
            if (stack == null) {
                stack = new ArrayDeque<>();
                threadTransactionStack.set(stack);
            }
            stack.push(Thread.currentThread().getStackTrace());

            if (stack.size() > 2) {
                StringBuilder trace = new StringBuilder();
                int t = 0;
                for (StackTraceElement[] frame : stack) {
                    t++;
                    for (int i = 0; i < frame.length; i++) {
                        trace.append(t);
                        trace.append(" ");
                        trace.append(frame[i].toString());
                        trace.append("\n");
                    }
                }

                LOGGER.warn("beginTransaction() - \n{}", trace);

            }

            return super.beginTransaction(entityManager, definition);
        }

        @Override
        public void cleanupTransaction(Object transactionData) {
            threadTransactionStack.get().pop();
            super.cleanupTransaction(transactionData);
        }
    }
}
