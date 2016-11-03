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

package stroom.pipeline.server.errorhandler;

import stroom.util.shared.Location;
import stroom.util.shared.Severity;

public class ErrorReceiverIdDecorator implements ErrorReceiver {
    private final String id;
    private final ErrorReceiver errorReceiver;

    public ErrorReceiverIdDecorator(final String id, final ErrorReceiver errorReceiver) {
        this.id = id;
        this.errorReceiver = errorReceiver;
    }

    @Override
    public void log(final Severity severity, final Location location, final String elementId, final String message,
            final Throwable e) {
        final String msg = MessageUtil.getMessage(message, e);
        errorReceiver.log(severity, location, id, elementId + " - " + msg, e);
    }

    @Override
    public String toString() {
        return this.errorReceiver.toString();
    }
}
