/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.impl;

import stroom.util.shared.Severity;

import java.util.function.Supplier;

public interface MessageReceiver {

    void log(Severity severity, Supplier<String> message);

    /**
     * Names the trace and span every message came from. A report covers many traces and each trace
     * many spans, so without it there is no way to tell which one a line is about.
     */
    static MessageReceiver forSpan(final MessageReceiver messageReceiver,
                                   final String traceId,
                                   final String spanId) {
        final String prefix = "[" + traceId + ":" + spanId + "] ";
        return (severity, message) -> messageReceiver.log(severity, () -> prefix + message.get());
    }
}
