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
 */

package stroom.pipeline.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import stroom.entity.server.GenericEntityService;
import stroom.entity.shared.DocRef;
import stroom.pipeline.shared.FetchDocRefsAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedSet;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchDocRefsAction.class)
@Scope(value = StroomScope.TASK)
public class FetchDocRefsHandler
        extends AbstractTaskHandler<FetchDocRefsAction, SharedSet<DocRef>> {
    private final Logger LOGGER = LoggerFactory.getLogger(FetchDocRefsHandler.class);
    private final GenericEntityService genericEntityService;

    @Inject
    FetchDocRefsHandler(final GenericEntityService genericEntityService) {
        this.genericEntityService = genericEntityService;
    }

    @Override
    public SharedSet<DocRef> exec(final FetchDocRefsAction action) {
        final SharedSet<DocRef> result = new SharedSet<>();
        if (action.getDocRefs() != null) {
            for (final DocRef docRef : action.getDocRefs()) {
                try {
                    final DocRef loaded = DocRef.create(genericEntityService.loadByUuid(docRef.getType(), docRef.getUuid()));
                    result.add(loaded);
                } catch (final Exception e) {
                    LOGGER.debug(e.getMessage(), e);
                }
            }
        }

        return result;
    }
}
