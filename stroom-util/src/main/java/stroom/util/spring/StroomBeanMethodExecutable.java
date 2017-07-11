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

package stroom.util.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.shared.Task;

import java.util.concurrent.atomic.AtomicBoolean;

public class StroomBeanMethodExecutable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomBeanMethodExecutable.class);

    private final StroomBeanMethod stroomBeanMethod;
    private final StroomBeanStore stroomBeanStore;
    private final String message;
    private final AtomicBoolean running;

    public StroomBeanMethodExecutable(final StroomBeanMethod stroomBeanMethod, final StroomBeanStore stroomBeanStore,
                                      final String message) {
        this(stroomBeanMethod, stroomBeanStore, message, new AtomicBoolean());
    }

    public StroomBeanMethodExecutable(final StroomBeanMethod stroomBeanMethod, final StroomBeanStore stroomBeanStore,
                                      final String message, final AtomicBoolean running) {
        this.stroomBeanMethod = stroomBeanMethod;
        this.stroomBeanStore = stroomBeanStore;
        this.message = message;
        this.running = running;
    }

    public void exec(final Task<?> task) {
        try {
            LOGGER.debug(message + " " + stroomBeanMethod.getBeanName() + "." + stroomBeanMethod.getBeanMethod().getName());
            final Class<?>[] paramTypes = stroomBeanMethod.getBeanMethod().getParameterTypes();
            if (paramTypes.length > 0) {
                if (paramTypes.length > 1) {
                    throw new IllegalArgumentException(
                            "Method cannot have more than 1 argument and that argument must be a task");
                } else {
                    final Class<?> firstParam = paramTypes[0];
                    if (!Task.class.isAssignableFrom(firstParam)) {
                        throw new IllegalArgumentException("Method can only have a task argument");
                    } else {
                        stroomBeanStore.invoke(stroomBeanMethod, task);
                    }
                }
            } else {
                stroomBeanStore.invoke(stroomBeanMethod);
            }
        } catch (final Throwable t) {
            LOGGER.error("Error calling {}", stroomBeanMethod, t);
        } finally {
            running.set(false);
        }
    }

    @Override
    public String toString() {
        return stroomBeanMethod.toString();
    }
}
