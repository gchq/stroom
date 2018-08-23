/*
 * Copyright 2018 Crown Copyright
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

package stroom.logging;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.servlet.HttpServletRequestHolder;
import stroom.servlet.HttpServletRequestHolderImpl;

public class LoggingModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DocumentEventLog.class).to(DocumentEventLogImpl.class);
        bind(HttpServletRequestHolder.class).to(HttpServletRequestHolderImpl.class);

        final Multibinder<EventInfoProvider> eventInfoProviderBinder = Multibinder.newSetBinder(binder(), EventInfoProvider.class);
        eventInfoProviderBinder.addBinding().to(BasicEventInfoProvider.class);
    }


//    @Bean
//    public StroomEventLoggingService stroomEventLoggingService(final SecurityContext security,
//                                                               final Provider<HttpServletRequestHolder> httpServletRequestHolderProvider) {
//        HttpServletRequestHolder httpServletRequestHolder = null;
//        try {
//            httpServletRequestHolder = httpServletRequestHolderProvider.get();
//        } catch (final RuntimeException e) {
//            // Ignore
//        }
//
//        return new StroomEventLoggingService(security, httpServletRequestHolder);
//    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}