/*
 * Copyright 2016 Crown Copyright
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

package stroom.entity.server.util;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.stereotype.Component;

/**
 * <p>
 * Interceptor to de proxy return values from the server side to remove any
 * hibernate proxies.
 * </p>
 */
@Component
public class JpaDeProxyMethodInterceptor implements MethodInterceptor {
    @Override
    public Object invoke(final MethodInvocation method) throws Throwable {
        // Create the processor
        final BaseEntityDeProxyProcessor incomingProcessor = new BaseEntityDeProxyProcessor(true);
        final BaseEntityDeProxyProcessor outgoingProcessor = new BaseEntityDeProxyProcessor(false);

        try {
            // Convert the parameters
            for (int i = 0; i < method.getArguments().length; i++) {
                method.getArguments()[i] = incomingProcessor.process(method.getArguments()[i]);
            }

            // Call the method
            Object rtn = method.proceed();

            // Convert the returned type
            rtn = outgoingProcessor.process(rtn);

            return rtn;

        } catch (final Throwable e) {
            throw EntityServiceExceptionUtil.create(e);
        }
    }
}
