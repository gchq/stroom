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

package stroom.data.store.impl;

import stroom.meta.shared.FindMetaRowAction;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.MetaService;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.ResultList;

import javax.inject.Inject;

class FindMetaRowHandler extends AbstractTaskHandler<FindMetaRowAction, ResultList<MetaRow>> {
    private final MetaService metaService;
    private final StreamAttributeMapRetentionRuleDecorator ruleDecorator;

    @Inject
    FindMetaRowHandler(final MetaService metaService,
                       final StreamAttributeMapRetentionRuleDecorator ruleDecorator) {
        this.metaService = metaService;
        this.ruleDecorator = ruleDecorator;
    }

    @Override
    public ResultList<MetaRow> exec(final FindMetaRowAction action) {
        final ResultList<MetaRow> list = metaService.findRows(action.getCriteria());
        list.forEach(metaRow -> ruleDecorator.addMatchingRetentionRuleInfo(metaRow.getMeta(), metaRow.getAttributes()));
        return list;
    }
}
