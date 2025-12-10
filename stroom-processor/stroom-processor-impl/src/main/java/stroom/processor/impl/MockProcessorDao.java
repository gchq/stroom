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

package stroom.processor.impl;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFields;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.ExpressionUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;

import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class MockProcessorDao implements ProcessorDao, Clearable {

    private final MockIntCrud<Processor> dao = new MockIntCrud<>();

    @Override
    public Processor create(final Processor processor) {
        final ExpressionOperator findProcessorExpression = ExpressionOperator.builder()
                .addDocRefTerm(ProcessorFields.PIPELINE,
                        Condition.IS_DOC_REF,
                        new DocRef("Pipeline", processor.getPipelineUuid()))
                .build();
        final ExpressionCriteria findProcessorCriteria = new ExpressionCriteria(findProcessorExpression);
//        findProcessorCriteria.obtainPipelineUuidCriteria().setString(processor.getPipelineUuid());
        final ResultPage<Processor> list = find(findProcessorCriteria);
        final Processor existingProcessor = list.getFirst();
        if (existingProcessor != null) {
            return existingProcessor;
        }

        return dao.create(processor);
    }

    @Override
    public Optional<Processor> fetch(final int id) {
        return dao.fetch(id);
    }

    @Override
    public Optional<Processor> fetchByUuid(final String uuid) {
        return Optional.empty();
    }

    @Override
    public Optional<Processor> fetchByPipelineUuid(final String pipelineUuid) {
        return dao.getMap().values()
                .stream()
                .filter(processor -> processor.getPipelineUuid().equals(pipelineUuid))
                .findAny();
    }

    @Override
    public Processor update(final Processor processor) {
        return dao.update(processor);
    }

    @Override
    public boolean delete(final int id) {
        return dao.delete(id);
    }

    @Override
    public int logicalDeleteByProcessorId(final int processorId) {
        return 0;
    }

    @Override
    public int physicalDeleteOldProcessors(final Instant deleteThreshold) {
        return 0;
    }

    @Override
    public ResultPage<Processor> find(final ExpressionCriteria criteria) {
        final List<Processor> list = dao
                .getMap()
                .values()
                .stream()
                .filter(pf -> {
                    final List<String> pipelineUuids = ExpressionUtil.values(criteria.getExpression(),
                            ProcessorFields.PIPELINE.getFldName());
                    return pipelineUuids == null || pipelineUuids.contains(pf.getPipelineUuid());
                })
                .collect(Collectors.toList());

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public void clear() {
        dao.clear();
    }
}
