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

package stroom.security.impl.db;

import stroom.security.impl.AppPermissionDao;
import stroom.security.impl.AppPermissionIdDao;
import stroom.security.impl.DocTypeIdDao;
import stroom.security.impl.DocumentPermissionDao;
import stroom.security.impl.UserDao;
import stroom.security.impl.apikey.ApiKeyDao;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class SecurityDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(ApiKeyDao.class).to(ApiKeyDaoImpl.class);
        bind(UserDao.class).to(UserDaoImpl.class);
        bind(DocumentPermissionDao.class).to(DocumentPermissionDaoImpl.class);
        bind(AppPermissionDao.class).to(AppPermissionDaoImpl.class);
        bind(AppPermissionIdDao.class).to(AppPermissionIdDaoImpl.class);
        bind(DocTypeIdDao.class).to(DocTypeIdDaoImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(AppPermissionIdDaoImpl.class)
                .addBinding(DocTypeIdDaoImpl.class);
    }
}
