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

package stroom.processor.client.presenter;

import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.ProcessorProfile;
import stroom.processor.shared.ProcessorProfileResource;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.shared.GWT;

import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ProcessorProfileClient {

    private static final ProcessorProfileResource PROCESSOR_PROFILE_RESOURCE =
            GWT.create(ProcessorProfileResource.class);

    private final RestFactory restFactory;

    @Inject
    ProcessorProfileClient(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void list(final ExpressionCriteria criteria,
                     final Consumer<ResultPage<ProcessorProfile>> dataConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PROCESSOR_PROFILE_RESOURCE)
                .method(res -> res.find(criteria))
                .onSuccess(dataConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void create(final ProcessorProfile processorProfile,
                                final Consumer<ProcessorProfile> consumer,
                                final RestErrorHandler errorHandler,
                                final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PROCESSOR_PROFILE_RESOURCE)
                .method(res -> res.create(processorProfile))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void fetchById(final int id,
                      final Consumer<ProcessorProfile> consumer,
                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PROCESSOR_PROFILE_RESOURCE)
                .method(res -> res.fetchById(id))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void fetchByName(final String name,
                      final Consumer<ProcessorProfile> consumer,
                            final RestErrorHandler errorHandler,
                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PROCESSOR_PROFILE_RESOURCE)
                .method(res -> res.fetchByName(name))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void update(final int id,
                       final ProcessorProfile processorProfile,
                       final Consumer<ProcessorProfile> consumer,
                       final RestErrorHandler errorHandler,
                       final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PROCESSOR_PROFILE_RESOURCE)
                .method(res -> res.update(id, processorProfile))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void delete(final int id,
                       final Consumer<Boolean> consumer,
                       final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PROCESSOR_PROFILE_RESOURCE)
                .method(res -> res.delete(id))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
