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

import stroom.util.shared.ElementId;
import stroom.util.shared.ErrorType;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;

/**
 * Implements a SAX error handler for logging all errors to log4j.
 */
public class IgnorantErrorReceiver implements ErrorReceiver {
    @Override
    public void log(final Severity severity, final Location location, final ElementId elementId, final String message,
                    final ErrorType errorType, final Throwable e) {
        // Do nothing.
    }
}
