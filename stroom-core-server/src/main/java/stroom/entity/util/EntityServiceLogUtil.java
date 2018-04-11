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

package stroom.entity.util;

import org.slf4j.Logger;
import org.slf4j.spi.LocationAwareLogger;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;

import java.util.Collection;

public final class EntityServiceLogUtil {
    private static String FQCN = EntityServiceLogUtil.class.getName();

    public static void logQuery(final Logger logger, final String prefix,
                                final LogExecutionTime logExecutionTime, final Collection<?> rtnList, final AbstractSqlBuilder sql) {
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
                log(logger, LocationAwareLogger.WARN_INT, log.toString(), null);
            } else {
                log(logger, LocationAwareLogger.DEBUG_INT, log.toString(), null);
            }
        }
    }

    public static final void logUpdate(final Logger logger, final String prefix,
                                       final LogExecutionTime logExecutionTime, final Long updateCount, final AbstractSqlBuilder sql) {
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
                log(logger, LocationAwareLogger.WARN_INT, log.toString(), null);
            } else {
                log(logger, LocationAwareLogger.DEBUG_INT, log.toString(), null);
            }
        }
    }

    private static void log(final Logger logger, final int severity, final String message, final Throwable t) {
        if (logger instanceof LocationAwareLogger) {
            final LocationAwareLogger locationAwareLogger = (LocationAwareLogger) logger;
            locationAwareLogger.log(null, FQCN, severity, message, null, t);

        } else {
            switch (severity) {
                case LocationAwareLogger.TRACE_INT:
                    logger.trace(message, t);
                    break;
                case LocationAwareLogger.DEBUG_INT:
                    logger.debug(message, t);
                    break;
                case LocationAwareLogger.INFO_INT:
                    logger.info(message, t);
                    break;
                case LocationAwareLogger.WARN_INT:
                    logger.warn(message, t);
                    break;
                case LocationAwareLogger.ERROR_INT:
                    logger.error(message, t);
                    break;
                default:
                    logger.error(message, t);
                    break;
            }
        }
    }
}
