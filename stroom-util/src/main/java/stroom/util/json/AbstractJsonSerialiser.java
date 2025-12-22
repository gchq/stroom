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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractJsonSerialiser<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJsonSerialiser.class);

    public String serialise(final T object) {
        try {
            return JsonUtil.writeValueAsString(object);

        } catch (final RuntimeException e) {
            LOGGER.debug("Problem serialising {} {}", new Object[]{getSerialisableClass(), object}, e);
            LOGGER.warn("Problem serialising {} {} - {} (enable debug for full trace)",
                    getSerialisableClass(), object, String.valueOf(e));
        }
        return null;
    }

    public T deserialise(final String json) {
        try {
            return JsonUtil.readValue(json, getSerialisableClass());
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to deserialise", e);
            LOGGER.warn(e.getMessage());
        }
        return null;
    }

    protected abstract Class<? extends T> getSerialisableClass();
}
