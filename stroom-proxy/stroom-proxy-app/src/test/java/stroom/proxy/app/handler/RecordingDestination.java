/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.handler;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A destination that accepts every group and records the path it was given.
 */
public class RecordingDestination implements Destination {

    private final List<Path> delivered;

    public RecordingDestination(final List<Path> delivered) {
        this.delivered = Objects.requireNonNull(delivered);
    }

    @Override
    public String getName() {
        return "recording";
    }

    @Override
    public String getDescription() {
        return "a list";
    }

    @Override
    public void deliver(final Path group) {
        delivered.add(group);
    }
}
