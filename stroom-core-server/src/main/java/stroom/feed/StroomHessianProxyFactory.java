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

package stroom.feed;

import com.caucho.hessian.client.HessianConnectionFactory;
import com.caucho.hessian.client.HessianProxyFactory;
import com.caucho.hessian.client.StroomHessianURLConnectionFactory;

import java.lang.reflect.InvocationTargetException;

public class StroomHessianProxyFactory extends HessianProxyFactory {
    private boolean ignoreSSLHostnameVerifier = true;

    @Override
    protected HessianConnectionFactory createHessianConnectionFactory() {
        final String className = System.getProperty(HessianConnectionFactory.class.getName());

        HessianConnectionFactory factory;
        try {
            if (className != null) {
                final ClassLoader loader = Thread.currentThread().getContextClassLoader();

                final Class<?> cl = Class.forName(className, false, loader);

                factory = (HessianConnectionFactory) cl.getConstructor().newInstance();

                return factory;
            }
        } catch (final NoSuchMethodException | InvocationTargetException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        return new StroomHessianURLConnectionFactory(ignoreSSLHostnameVerifier);
    }

    public void setIgnoreSSLHostnameVerifier(final boolean ignoreSSLHostnameVerifier) {
        this.ignoreSSLHostnameVerifier = ignoreSSLHostnameVerifier;
    }
}
