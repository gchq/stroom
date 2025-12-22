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

package stroom.bytebuffer;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBufferPoolImpl6;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasSystemInfoBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class ByteBufferModule extends AbstractModule {

    @Override
    protected void configure() {
        // If you switch impl here make sure also to do it in the SystemInfo binder below
        bind(ByteBufferPool.class).to(ByteBufferPoolImpl6.class);
        bind(ByteBufferFactory.class).to(ByteBufferFactoryImpl.class);

        HasSystemInfoBinder.create(binder())
                .bind(ByteBufferPoolImpl6.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(ByteBufferPoolImpl6.class);
    }
}
