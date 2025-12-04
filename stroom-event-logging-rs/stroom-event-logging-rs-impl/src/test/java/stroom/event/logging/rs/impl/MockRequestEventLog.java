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

package stroom.event.logging.rs.impl;

import jakarta.annotation.Nullable;

public class MockRequestEventLog implements RequestEventLog {

    @Override
    public void log(final RequestInfo info, @Nullable final Object responseEntity) {

    }

    @Override
    public void log(final RequestInfo info, @Nullable final Object responseEntity, final Throwable error) {

    }
}
