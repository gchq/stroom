/*
 * Copyright 2023 Crown Copyright
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

package stroom.ai.impl.dao;

import stroom.ai.impl.AiChatRunDao;
import stroom.ai.impl.AiDao;
import stroom.ai.impl.AiMemoryDao;
import stroom.ai.impl.AiSampleUploadDao;
import stroom.ai.impl.AiToolApprovalDao;
import stroom.ai.impl.AiToolPolicyDao;
import stroom.ai.impl.AiWorkspaceDao;

import com.google.inject.AbstractModule;

public class AiDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();
        bind(AiDao.class).to(AiDaoImpl.class);
        bind(AiChatRunDao.class).to(AiChatRunDaoImpl.class);
        bind(AiWorkspaceDao.class).to(AiWorkspaceDaoImpl.class);
        bind(AiMemoryDao.class).to(AiMemoryDaoImpl.class);
        bind(AiToolApprovalDao.class).to(AiToolApprovalDaoImpl.class);
        bind(AiToolPolicyDao.class).to(AiToolPolicyDaoImpl.class);
        bind(AiSampleUploadDao.class).to(AiSampleUploadDaoImpl.class);
    }
}
