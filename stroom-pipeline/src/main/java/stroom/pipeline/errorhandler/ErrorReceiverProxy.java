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

package stroom.pipeline.errorhandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.guice.PipelineScoped;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;

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
    public void log(final Severity severity, final Location location, final String elementId, final String message, final Throwable e) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(message, e);
        }

        errorReceiver.log(severity, location, elementId, message, e);
    }

    public ErrorReceiver getErrorReceiver() {
        return errorReceiver;
    }

    public void setErrorReceiver(final ErrorReceiver errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    @Override
    public String toString() {
        return this.errorReceiver.toString();
    }
}
