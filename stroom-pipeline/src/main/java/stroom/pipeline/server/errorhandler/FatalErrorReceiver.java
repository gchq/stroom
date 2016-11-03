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

/**
 * Throws an exception that will cause XML processing to terminate immediately
 * for all types of error.
 */
public final class FatalErrorReceiver implements ErrorReceiver {
    @Override
    public void log(final Severity severity, final Location location, final String elementId, final String message,
            final Throwable e) {
        throw ProcessException.wrap(message, e);
    }
}
