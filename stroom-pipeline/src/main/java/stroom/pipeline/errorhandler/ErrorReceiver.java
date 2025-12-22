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

package stroom.pipeline.errorhandler;

import stroom.util.logging.LogUtil;
import stroom.util.shared.ElementId;
import stroom.util.shared.ErrorType;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;

/**
 * An interface to deal with all error reporting and handling during processing.
 */
public interface ErrorReceiver {

    void log(
            Severity severity,
            Location location,
            ElementId elementId,
            String message,
            ErrorType errorType,
            Throwable e);

    default void log(
            final Severity severity,
            final Location location,
            final ElementId elementId,
            final String message,
            final Throwable e) {
        log(severity, location, elementId, message, null, e);
    }

    // Different name to avoid confusion with varargs
    default void logTemplate(
            final Severity severity,
            final Location location,
            final ElementId elementId,
            final String messageTemplate,
            final Throwable e,
            final Object... messageArgs) {
        if (messageArgs == null || messageArgs.length == 0) {
            log(severity, location, elementId, messageTemplate, e);
        } else {
            log(severity, location, elementId, LogUtil.message(messageTemplate, messageArgs), e);
        }
    }
}
