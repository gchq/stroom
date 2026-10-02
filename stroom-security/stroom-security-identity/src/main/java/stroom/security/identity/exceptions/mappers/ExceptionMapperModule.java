/*
 * Copyright 2026 Crown Copyright
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

package stroom.security.identity.exceptions.mappers;

import stroom.util.guice.GuiceUtil;

import com.google.inject.AbstractModule;
import jakarta.ws.rs.ext.ExceptionMapper;

/**
 * Registers this module's exception mappers.
 * <p>
 * A mapper takes effect only by being in the {@link ExceptionMapper} multibinder that
 * {@code DelegatingExceptionMapper} iterates; the {@code @Provider} annotation on its own does nothing here,
 * because nothing scans for it. Without this the exceptions below miss their mapper and fall through to the
 * catch-all, which answers 500 to what are ordinary client errors.
 * </p>
 * <p>
 * The binding lives beside the mappers rather than in the application's Jersey module so that adding a
 * mapper and registering it are the same piece of work, in one place.
 * </p>
 */
public class ExceptionMapperModule extends AbstractModule {

    @Override
    protected void configure() {
        GuiceUtil.buildMultiBinder(binder(), ExceptionMapper.class)
                .addBinding(BadRequestExceptionMapper.class)
                .addBinding(ConflictExceptionMapper.class)
                .addBinding(NoCertificateExceptionMapper.class)
                .addBinding(UnsupportedFilterExceptionMapper.class);

        // NoSuchUserExceptionMapper is deliberately absent. NoSuchUserException extends
        // WebApplicationException, so Jersey already answers it with the status it carries, and binding the
        // mapper as well would give two answers for one exception.
    }
}
