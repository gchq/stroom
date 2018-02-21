/*
 * Copyright 2017 Crown Copyright
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

package stroom.pipeline;

import stroom.entity.MockDocumentEntityService;
import stroom.importexport.ImportExportHelper;
import stroom.pipeline.shared.FindTextConverterCriteria;
import stroom.pipeline.shared.TextConverter;

import javax.inject.Inject;

/**
 * <p>
 * Very simple mock that keeps everything in memory.
 * </p>
 * <p>
 * <p>
 * You can call clear at any point to clear everything down.
 * </p>
 */
public class MockTextConverterService extends MockDocumentEntityService<TextConverter, FindTextConverterCriteria>
        implements TextConverterService {
    @Inject
    public MockTextConverterService(final ImportExportHelper importExportHelper) {
        super(importExportHelper);
    }

    @Override
    public Class<TextConverter> getEntityClass() {
        return TextConverter.class;
    }
}
