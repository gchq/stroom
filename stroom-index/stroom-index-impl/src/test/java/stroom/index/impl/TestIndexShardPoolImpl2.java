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

package stroom.index.impl;

import stroom.index.shared.IndexShardKey;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.query.language.functions.ValString;
import stroom.search.extraction.FieldValue;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.concurrent.SimpleExecutor;
import stroom.util.io.FileUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class TestIndexShardPoolImpl2 extends StroomUnitTest {

    @BeforeEach
    void before() {
        FileUtil.deleteContents(getCurrentTestDir().resolve("index"));
    }

    @Test
    void testThreadingLikeTheRealThing() throws InterruptedException {
        final LuceneIndexField indexField = LuceneIndexField.createField("test");
        final List<LuceneIndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(indexField);

        try {
            final Indexer indexer = (key, document) -> {
            };

            final LuceneIndexDoc index = LuceneIndexDoc.builder()
                    .uuid("1")
                    .fields(indexFields)
                    .maxDocsPerShard(1000)
                    .build();

            final IndexShardKey indexShardKey = IndexShardKey.createKey(index);
            final SimpleExecutor simpleExecutor = new SimpleExecutor(10);

            for (int i = 0; i < 1000; i++) {
                simpleExecutor.execute(() -> {
                    for (int i1 = 0; i1 < 100; i1++) {
                        // Do some work.
                        final FieldValue field = new FieldValue(indexField, ValString.create("test"));
                        final IndexDocument document = new IndexDocument();
                        document.add(field);
                        indexer.addDocument(indexShardKey, document);
                    }
                });
            }

            simpleExecutor.stop(false);
        } catch (final RuntimeException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
