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

import stroom.util.pipeline.scope.PipelineScoped;
import stroom.util.shared.ElementId;
import stroom.util.shared.ErrorType;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@PipelineScoped
public class ErrorReceiverProxy implements ErrorReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorReceiverProxy.class);

    private ErrorReceiver errorReceiver;

    public ErrorReceiverProxy() {
    }

    public ErrorReceiverProxy(final ErrorReceiver errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    @Override
    public void log(final Severity severity,
                    final Location location,
                    final ElementId elementId,
                    final String message,
                    final ErrorType errorType,
                    final Throwable e) {

        // TRACE for full stack traces, DEBUG for message only
        if (LOGGER.isDebugEnabled()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(message, e);
            } else {
                LOGGER.debug(message +
                             (e != null && !Objects.equals(e.getMessage(), message)
                                     ? " - " + e.getMessage()
                                     : "") +
                             " (Enable TRACE for full stack traces)");
            }
        }
        errorReceiver.log(severity, location, elementId, message, errorType, e);
    }

    public ErrorReceiver getErrorReceiver() {
        return errorReceiver;
    }

    public void setErrorReceiver(final ErrorReceiver errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    @Override
    public String toString() {
        if (errorReceiver != null) {
            return this.errorReceiver.toString();
        }
        return super.toString();
    }
}
