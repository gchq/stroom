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

package stroom.app.guice;

import stroom.data.store.impl.DataRetentionJobModule;
import stroom.data.store.impl.fs.FsDataStoreJobsModule;
import stroom.data.store.impl.fs.FsVolumeJobsModule;
import stroom.gitrepo.impl.GitRepoJobsModule;

import com.google.inject.AbstractModule;

public class JobsModule extends AbstractModule {

    @Override
    protected void configure() {

        // Job modules with no other obvious home
        install(new DataRetentionJobModule());
        install(new FsVolumeJobsModule());
        install(new stroom.node.impl.NodeJobsModule());
        install(new FsDataStoreJobsModule());
        install(new GitRepoJobsModule());
    }
}
