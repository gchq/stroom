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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

public abstract class AbstractJsonSerialiser<T> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractJsonSerialiser.class);

    public String serialise(final T object) {
        try {
            return JsonUtil.writeValueAsString(object);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Problem serialising {} {} - {}",
                    getSerialisableClass(), object, LogUtil.exceptionMessage(e)), e);
            LOGGER.warn(() -> LogUtil.message("Problem serialising {} {} - {} (enable debug for full trace)",
                    getSerialisableClass(), object, LogUtil.exceptionMessage(e)));
        }
        return null;
    }

    public T deserialise(final String json) {
        try {
            return JsonUtil.readValue(json, getSerialisableClass());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to deserialise to {} - {}",
                    getSerialisableClass(), LogUtil.exceptionMessage(e)), e);
            LOGGER.warn(() -> LogUtil.message("Unable to deserialise to {} - {} (enable debug for full trace)",
                    getSerialisableClass(), LogUtil.exceptionMessage(e)));
        }
        return null;
    }

    protected abstract Class<? extends T> getSerialisableClass();
}
