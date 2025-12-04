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

package stroom.pipeline.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractHolder<T extends Holder> implements HasChangeHandlers<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractHolder.class);

    private List<ChangeHandler<T>> handlers;

    @Override
    public void addChangeHandler(final ChangeHandler<T> handler) {
        if (handlers == null) {
            handlers = new ArrayList<>();
        }
        handlers.add(handler);
    }

    protected void fire(final ChangeEvent<T> event) {
        if (handlers != null) {
            for (final ChangeHandler<T> handler : handlers) {
                try {
                    handler.onChange(event);
                } catch (final RuntimeException e) {
                    LOGGER.error("Unable to handle onChange!", e);
                }
            }
        }
    }
}
