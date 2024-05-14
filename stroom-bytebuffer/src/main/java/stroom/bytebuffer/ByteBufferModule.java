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
 *
 */

package stroom.bytebuffer;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasSystemInfoBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class ByteBufferModule extends AbstractModule {

    static final Class<? extends ByteBufferPool> DEFAULT_BYTE_BUFFER_POOL = ByteBufferPoolImpl10.class;

    @Override
    protected void configure() {
        // If you switch impl here make sure also to do it in the SystemInfo binder below
        bind(ByteBufferPool.class).to(DEFAULT_BYTE_BUFFER_POOL);
        bind(ByteBufferFactory.class).to(ByteBufferFactoryImpl.class);

        HasSystemInfoBinder.create(binder())
                .bind(DEFAULT_BYTE_BUFFER_POOL);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(DEFAULT_BYTE_BUFFER_POOL);
    }
}
