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

package stroom.data.store.impl.fs;

import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.inject.Inject;

/**
 * API used by the tasks to interface to the stream store under the bonnet.
 */
class FsOrphanMetaFinder {

    private static final int BATCH_SIZE = 1000;

    private final FsFileFinder fileFinder;
    private final MetaService metaService;

    @Inject
    public FsOrphanMetaFinder(final FsFileFinder fileFinder,
                              final MetaService metaService) {
        this.fileFinder = fileFinder;
        this.metaService = metaService;
    }

    public void scan(final Consumer<Meta> orphanConsumer,
                     final FsOrphanMetaFinderProgress progress) {
        final long maxId = metaService.getMaxId();
        long minId = 0;

        while (minId != -1 && !Thread.currentThread().isInterrupted()) {
            minId = scanBatch(minId, maxId, orphanConsumer, progress);
        }
    }

    private long scanBatch(final long minId,
                           final long maxId,
                           final Consumer<Meta> orphanConsumer,
                           final FsOrphanMetaFinderProgress progress) {
        progress.setMinId(minId);
        progress.setMaxId(maxId);
        progress.log();

        final PageRequest pageRequest = new PageRequest(0, BATCH_SIZE);
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(MetaFields.ID, Condition.GREATER_THAN, minId)
                .addTerm(MetaFields.ID, Condition.LESS_THAN_OR_EQUAL_TO, maxId)
                .build();
        final FindMetaCriteria criteria = new FindMetaCriteria(pageRequest, null, expression, false);
        final ResultPage<Meta> resultPage = metaService.find(criteria);
        final List<Meta> values = resultPage.getValues();

        long result = -1;
        if (values.size() > 0) {
            final Iterator<Meta> metaIterator = values.iterator();
            while (metaIterator.hasNext() && !Thread.currentThread().isInterrupted()) {
                final Meta meta = metaIterator.next();
                progress.setId(meta.getId());

                result = Math.max(result, meta.getId());
                final Optional<Path> optional = fileFinder.findRootStreamFile(meta);
                if (optional.isEmpty()) {
                    progress.foundOrphan();
                    orphanConsumer.accept(meta);
                }

                progress.log();
            }
        }
        return result;
    }
}
