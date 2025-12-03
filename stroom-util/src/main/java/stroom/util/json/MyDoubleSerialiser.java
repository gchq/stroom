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

package stroom.util.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class MyDoubleSerialiser extends JsonSerializer<Double> {

    @Override
    public void serialize(final Double value, final JsonGenerator gen, final SerializerProvider serializers)
            throws IOException {
        if (value != null) {
            if (value.compareTo((double) value.longValue()) == 0) {
                gen.writeNumber(value.longValue());
            } else {
                gen.writeNumber(value);
            }
        }
    }
}
