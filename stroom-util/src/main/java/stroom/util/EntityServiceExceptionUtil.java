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

package stroom.util;

import stroom.util.io.StreamUtil;
import stroom.util.shared.EntityServiceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.UnknownHostException;

public class EntityServiceExceptionUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityServiceExceptionUtil.class);

    /**
     * Handle an exception and throw a nice EntityServiceException
     */
    public static EntityServiceException create(final Throwable ex) throws EntityServiceException {
        if (ex instanceof EntityServiceException) {
            LOGGER.trace("create() - {}", ex.getMessage());

            return (EntityServiceException) ex;
        } else {
            LOGGER.debug("create() - wrapping exception", ex);

            return unwrap(ex);
        }
    }

    /**
     * Unwrap an exception and log it if required.
     */
    public static String unwrapMessage(final Throwable rootEx, final Throwable e) {
        if (e instanceof java.sql.SQLException) {
            return "Unable to save record state: " + e.getMessage();
        }
        if (e instanceof EntityServiceException) {
            return e.getMessage();
        }
        if (e.getCause() != null) {
            return unwrapMessage(rootEx, e.getCause());
        }
        return getDefaultMessage(e, rootEx);
    }

    public static String getDefaultMessage(final Throwable e, final Throwable rootEx) {
        String msg = null;
        if (e != null && e.getMessage() != null) {
            msg = e.getMessage();
        } else {
            if (rootEx != null && rootEx.getMessage() != null) {
                msg = rootEx.getMessage();
            }
        }

        if (msg == null) {
            msg = "Unknown";

            // Log the exception otherwise we may never know what type of
            // exception this was.
            LOGGER.error(e.getMessage(), e);
        } else if (msg.startsWith("Incorrect key file for table")) {
            return "Unable to run query as data set too large";
        }

        return msg;
    }

    private static EntityServiceException unwrap(final Throwable th) {
        return unwrap(th, th, 10);
    }

    private static EntityServiceException unwrap(final Throwable rootEx, final Throwable thEx, final int depth) {
        if (thEx instanceof EntityServiceException) {
            return (EntityServiceException) thEx;
        }
        if (thEx instanceof UnknownHostException) {
            return new EntityServiceException("Unknown host: " + thEx.getMessage(), thEx.getClass().getName(), true);
        }
        if (depth > 0) {
            if (thEx instanceof InvocationTargetException) {
                return unwrap(rootEx, ((InvocationTargetException) thEx).getTargetException(), depth - 1);
            }
            if (thEx.getCause() != null) {
                return unwrap(rootEx, thEx.getCause(), depth - 1);
            }
        }
        boolean networkRelated = thEx.getClass().getName().contains(".net");
        final String message = thEx.getMessage();
        if (message != null) {
            if (thEx instanceof IOException && message.contains("http")) {
                networkRelated = true;
            }
        }

        LOGGER.warn("unwrap() - wrapping exception {} {} (increase log level to debug to see stack trace)",
                rootEx.getMessage(), thEx.getMessage());
        LOGGER.debug("unwrap() - wrapping exception ", rootEx);

        final EntityServiceException entityServiceException = new EntityServiceException(unwrapMessage(rootEx, rootEx),
                thEx.getClass().getName(), networkRelated);
        entityServiceException.setCallStack(StreamUtil.exceptionCallStack(rootEx));
        return entityServiceException;

    }

    private static String appendDetail(final String detail) {
        if (detail == null) {
            return "";
        }
        return detail + "\n";
    }
}
