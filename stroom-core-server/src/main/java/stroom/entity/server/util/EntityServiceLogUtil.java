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
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import org.apache.log4j.Level;

import java.util.Collection;

public final class EntityServiceLogUtil {
    static String FQCN = EntityServiceLogUtil.class.getName();

    public static final void logQuery(final Logger logger, final String prefix,
                                      final LogExecutionTime logExecutionTime, final Collection<?> rtnList, final SQLBuilder sql) {
        final long duration = logExecutionTime.getDuration();

        if (duration > 1000 || logger.isDebugEnabled()) {
            final StringBuilder log = new StringBuilder();
            log.append(prefix);
            log.append(" - took ");
            log.append(logExecutionTime);
            if (rtnList != null) {
                log.append(" for ");
                log.append(ModelStringUtil.formatCsv(rtnList.size()));
                log.append(" matches ");
            }
            if (sql != null) {
                log.append(" - ");
                log.append(sql.toTraceString());
            }
            if (duration > 1000) {
                logger.warn("{}, {}", FQCN, log.toString());
            } else {
                logger.debug("{}, {}", FQCN, log.toString());
            }
        }
    }

    public static final void logUpdate(final Logger logger, final String prefix,
                                       final LogExecutionTime logExecutionTime, final Long updateCount, final SQLBuilder sql) {
        final long duration = logExecutionTime.getDuration();

        if (duration > 1000 || logger.isDebugEnabled()) {
            final StringBuilder log = new StringBuilder();
            log.append(prefix);
            log.append(" - took ");
            log.append(logExecutionTime);
            if (updateCount != null) {
                log.append(" for ");
                log.append(ModelStringUtil.formatCsv(updateCount));
                log.append(" matches ");
            }
            if (sql != null) {
                log.append(" - ");
                log.append(sql.toTraceString());
            }
            if (duration > 1000) {
                logger.warn("{}, {}", FQCN, log.toString());
            } else {
                logger.debug("{}, {}", FQCN, log.toString());
            }
        }
    }
}
