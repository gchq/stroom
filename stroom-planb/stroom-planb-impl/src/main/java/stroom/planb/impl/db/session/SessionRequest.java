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

package stroom.planb.impl.db.session;

import stroom.planb.impl.serde.keyprefix.KeyPrefix;

import java.time.Instant;

public record SessionRequest(KeyPrefix prefix, Instant time) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private KeyPrefix prefix;
        private Instant time;

        public Builder() {
        }

        public Builder(final SessionRequest request) {
            this.prefix = request.prefix;
            this.time = request.time;
        }

        public Builder prefix(final KeyPrefix prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder time(final Instant time) {
            this.time = time;
            return this;
        }

        public SessionRequest build() {
            return new SessionRequest(prefix, time);
        }
    }
}
