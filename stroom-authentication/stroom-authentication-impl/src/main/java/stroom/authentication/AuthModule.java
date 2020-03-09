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

package stroom.authentication;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.jooq.Configuration;
import stroom.authentication.exceptions.ConflictException;
import stroom.authentication.exceptions.mappers.*;
import stroom.authentication.resources.authentication.v1.AuthenticationResource;
import stroom.authentication.resources.token.v1.TokenResource;
import stroom.authentication.resources.user.v1.UserDao;
import stroom.authentication.resources.user.v1.UserResource;
import stroom.authentication.service.eventlogging.StroomEventLoggingService;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.RestResource;

public final class AuthModule extends AbstractModule {
    //    private Config config;
    private Configuration jooqConfig;
//
//    public Module(Config config, Configuration jooqConfig) {
//        this.config = config;
//        this.jooqConfig = jooqConfig;
//    }

    @Override
    protected void configure() {
        bind(UserResource.class);
//        bind(AuthenticationResource.class);
//        bind(TokenResource.class);
//        bind(UserServiceClient.class);
        bind(TokenVerifier.class);
        bind(EmailSender.class);
        bind(CertificateManager.class);
        bind(TokenBuilderFactory.class);
        bind(StroomEventLoggingService.class);
        bind(UserDao.class);

        bind(ConflictExceptionMapper.class);
        bind(BadRequestExceptionMapper.class);
        bind(TokenCreationExceptionMapper.class);
        bind(UnsupportedFilterExceptionMapper.class);
        bind(NoSuchUserExceptionMapper.class);
        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(UserResource.class)
                .addBinding(AuthenticationResource.class)
                .addBinding(TokenResource.class);
    }

    //
//    @Provides
//    public Config getConfig() {
//        return config;
//    }
//
//    @Provides
//    public TokenConfig getTokenConfig() {
//        return config.getTokenConfig();
//    }
//
    @Provides
    public Configuration getJooqConfig() {
        return jooqConfig;
    }
//
//    @Provides
//    public PasswordIntegrityCheckTask getPasswordIntegrityCheckTask(Config config, UserDao userDao){
//        return new PasswordIntegrityCheckTask(config.getPasswordIntegrityChecksConfig(), userDao);
//    }
}
