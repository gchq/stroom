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

import stroom.pipeline.refdata.store.FastInfosetUtil;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.StringValue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public record TemporalState(
        String key,
        Instant effectiveTime,
        byte typeId,
        ByteBuffer value) {

    public String getValueAsString() {
        return switch (typeId) {
            case StringValue.TYPE_ID -> new String(value.duplicate().array(), StandardCharsets.UTF_8);
            case FastInfosetValue.TYPE_ID -> FastInfosetUtil.byteBufferToString(value.duplicate());
            default -> null;
        };
    }

    public static class Builder {

        private String key;
        private Instant effectiveTime;
        private byte typeId;
        private ByteBuffer value;

        public Builder() {
        }

        public Builder(final TemporalState state) {
            this.key = state.key;
            this.effectiveTime = state.effectiveTime;
            this.typeId = state.typeId;
            this.value = state.value;
        }

        public Builder key(final String key) {
            this.key = key;
            return this;
        }

        public Builder effectiveTime(final Instant effectiveTime) {
            this.effectiveTime = effectiveTime;
            return this;
        }

        public Builder typeId(final byte typeId) {
            this.typeId = typeId;
            return this;
        }

        public Builder value(final ByteBuffer value) {
            this.value = value;
            return this;
        }

        public TemporalState build() {
            return new TemporalState(key, effectiveTime, typeId, value);
        }
    }
}
