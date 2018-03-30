/*
 * Copyright 2016 Crown Copyright
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

package stroom.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.shared.Task;

public abstract class AbstractTaskHandler<T extends Task<R>, R> implements TaskHandler<T, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTaskHandler.class);

    @Override
    public void exec(final T task, final TaskCallback<R> callback) {
        try {
            final R result = exec(task);
            if (callback != null) {
                try {
                    callback.onSuccess(result);
                } catch (final RuntimeException e) {
                    // Ignore any errors that come from handling success.
                    LOGGER.trace(e.getMessage(), e);
                }
            }
        } catch (final RuntimeException e) {
            if (callback != null) {
                LOGGER.debug(e.getMessage(), e);
                callback.onFailure(e);
            } else {
                LOGGER.error(e.getMessage(), e);
                LOGGER.error("exec() - No Call back for error", e);
            }
        }
    }

    public abstract R exec(final T task);
}
