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

package stroom.task.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.Secured;
import stroom.util.shared.Task;

@Secured
public abstract class AbstractTaskHandler<T extends Task<R>, R> implements TaskHandler<T, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTaskHandler.class);

    @Override
    public void exec(final T task, final TaskCallback<R> callback) {
        try {
            final R result = exec(task);
            if (callback != null) {
                try {
                    callback.onSuccess(result);
                } catch (final Throwable t) {
                    // Ignore any errors that come from handling success.
                    LOGGER.trace(t.getMessage(), t);
                }
            }
        } catch (final Throwable t) {
            if (callback != null) {
                LOGGER.debug(t.getMessage(), t);
                callback.onFailure(t);
            } else {
                LOGGER.error(t.getMessage(), t);
                LOGGER.error("exec() - No Call back for error", t);
            }
        }
    }

    public abstract R exec(final T task);
}
