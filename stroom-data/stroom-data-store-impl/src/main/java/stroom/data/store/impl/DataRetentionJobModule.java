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

package stroom.data.store.impl;

import com.google.inject.AbstractModule;

public class DataRetentionJobModule extends AbstractModule {

//    @Override
//    protected void configure() {
//
//        ScheduledJobsBinder.create(binder())
//                .bindJobTo(DataRetention.class, builder -> builder
//                        .name("Feed Based Data Retention")
//                        .description("Delete data that exceeds the retention period specified by feed")
//                        .schedule(CRON, "0 0 0 * * ?"));
//    }
//
//    private static class DataRetention extends RunnableWrapper {
//
//        @Inject
//        DataRetention(final FeedDataRetentionExecutor feedDataRetentionExecutor) {
//            super(feedDataRetentionExecutor::exec);
//        }
//    }
}
