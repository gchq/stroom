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

package stroom.util.pipeline.scope;

import jakarta.inject.Inject;

import java.util.function.Supplier;

public class PipelineScopeRunnable {

    private final PipelineScope scope;

    @Inject
    PipelineScopeRunnable(final PipelineScope scope) {
        this.scope = scope;
    }

    public void scopeRunnable(final Runnable runnable) {
        scope.enter();
        try {
//            // explicitly seed some seed objects...
//            scope.seed(Key.get(SomeObject.class), someObject);

            // create and access scoped objects
            runnable.run();

        } finally {
            scope.exit();
        }
    }

    public <T> T scopeResult(final Supplier<T> supplier) {
        final T result;

        scope.enter();
        try {
            // create and access scoped objects
            result = supplier.get();

        } finally {
            scope.exit();
        }

        return result;
    }
}
