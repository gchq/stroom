/*
 * Copyright 2018 Crown Copyright
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

package stroom.explorer.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

public final class ExplorerActionHandlerBinder {
    private final Multibinder<ExplorerActionHandler> multibinder;

    private ExplorerActionHandlerBinder(final Binder binder) {
        multibinder = Multibinder.newSetBinder(binder, ExplorerActionHandler.class);
    }

    public static ExplorerActionHandlerBinder create(final Binder binder) {
        return new ExplorerActionHandlerBinder(binder);
    }

    public <T extends ExplorerActionHandler> ExplorerActionHandlerBinder bind(final Class<T> handlerClass) {
        multibinder.addBinding().to(handlerClass);
        return this;
    }
}