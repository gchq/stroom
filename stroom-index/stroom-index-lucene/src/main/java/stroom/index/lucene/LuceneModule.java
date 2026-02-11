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

package stroom.index.lucene;

import stroom.index.impl.LuceneProvider;
import stroom.search.extraction.MemoryIndex;
import stroom.util.guice.GuiceUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class LuceneModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(MemoryIndex.class).to(LuceneMemoryIndex.class);

        // Bind this provider.
        GuiceUtil.buildMultiBinder(binder(), LuceneProvider.class).addBinding(Lucene980Provider.class);
        GuiceUtil.buildMultiBinder(binder(), LuceneProvider.class).addBinding(Lucene1031Provider.class);
    }

    @Provides
    public FieldFactory getFieldFactory(final DenseVectorFieldCreatorFactory denseVectorFieldCreatorFactory) {
        return new FieldFactory(denseVectorFieldCreatorFactory);
    }
}
