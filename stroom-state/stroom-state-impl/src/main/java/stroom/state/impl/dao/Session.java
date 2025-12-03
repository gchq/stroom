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

package stroom.state.impl.dao;

import java.time.Instant;

public record Session(
        String key,
        Instant start,
        Instant end,
        boolean terminal) {

    public static class Builder {
        private String key;
        private Instant start;
        private Instant end;
        private boolean terminal;

        public Builder() {
        }

        public Builder(final Session session) {
            this.key = session.key;
            this.start = session.start;
            this.end = session.end;
            this.terminal = session.terminal;
        }

        public Builder key(final String key) {
            this.key = key;
            return this;
        }

        public Builder start(final Instant start) {
            this.start = start;
            return this;
        }

        public Builder end(final Instant end) {
            this.end = end;
            return this;
        }

        public Builder terminal(final boolean terminal) {
            this.terminal = terminal;
            return this;
        }

        public Session build() {
            return new Session(key, start, end, terminal);
        }
    }

}
