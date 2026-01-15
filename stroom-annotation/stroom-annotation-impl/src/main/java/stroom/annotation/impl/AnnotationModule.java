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

package stroom.annotation.impl;

import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationCreator;
import stroom.annotation.shared.AnnotationEntry;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.job.api.ScheduledJobsBinder;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.query.common.v2.AnnotationMapperFactory;
import stroom.searchable.api.Searchable;
import stroom.util.RunnableWrapper;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.HasUserDependencies;
import stroom.util.shared.scheduler.CronExpressions;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class AnnotationModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(AnnotationCreator.class).to(AnnotationService.class);
        bind(AnnotationMapperFactory.class).to(AnnotationMapperFactoryImpl.class);

        RestResourcesBinder.create(binder())
                .bind(AnnotationResourceImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(Annotation.class, AnnotationEventInfoProvider.class)
                .bind(AnnotationEntry.class, AnnotationEventInfoProvider.class);

        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(AnnotationService.class);
//        GuiceUtil.buildMultiBinder(binder(), Searchable.class)
//                .addBinding(AnnotationService.class);
        GuiceUtil.buildMapBinder(binder(), Searchable.class)
                .addBinding(AnnotationService.class);
        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(AnnotationState.class);

        GuiceUtil.buildMapBinder(binder(), String.class, HasUserDependencies.class)
                .addBinding(AnnotationService.class.getName(), AnnotationService.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(DataRetention.class, builder -> builder
                        .name(AnnotationService.ANNOTATION_RETENTION_JOB_NAME)
                        .description("Delete annotations that exceed the retention period " +
                                     "specified by data retention policy")
                        .cronSchedule(CronExpressions.EVERY_DAY_AT_MIDNIGHT.getExpression()));
    }

    private static class DataRetention extends RunnableWrapper {

        @Inject
        DataRetention(final AnnotationService annotationService) {
            super(annotationService::performDataRetention);
        }
    }
}
