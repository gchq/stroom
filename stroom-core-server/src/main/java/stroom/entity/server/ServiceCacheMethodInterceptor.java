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

package stroom.entity.server;

import com.googlecode.ehcache.annotations.key.ListCacheKeyGenerator;
import com.googlecode.ehcache.annotations.key.ReadOnlyList;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.Entity;
import stroom.entity.shared.EntityService;
import stroom.entity.shared.EntityServiceException;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

import javax.annotation.Resource;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Set;

@Component("serviceCacheInterceptor")
public class ServiceCacheMethodInterceptor implements MethodInterceptor, InitializingBean, Clearable {
    public static final String PROXY_CLASS_NAME = "_$$_";
    private static final String LOAD = "load";
    private static final String LOAD_BY_ID = "loadById";
    private static final String LOAD_BY_UUID = "loadByUuid";
    private static final String FIND = "find";
    private final ListCacheKeyGenerator keyGenerator = new ListCacheKeyGenerator(true, true);
    @Resource
    ServiceCacheMethodInterceptorTransactionHelper serviceCacheMethodInterceptorTransactionHelper;
    @Resource
    private CacheManager cacheManager;
    private Cache cache;

    @Override
    public void afterPropertiesSet() throws Exception {
        cache = cacheManager.getCache("serviceCache");
    }

    @Override
    public void clear() {
        cache.removeAll();
    }

    private Object getTarget(final MethodInvocation invocation) throws Throwable {
        final Object obj = invocation.getThis();

        // Deal with JDK dynamic proxies.
        if (obj instanceof Advised) {
            final Advised advised = (Advised) obj;
            final Object target = advised.getTargetSource().getTarget();
            return target;
        }

        return obj;
    }

    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        final Method method = invocation.getMethod();
        final Object target = getTarget(invocation);
        final String methodName = method.getName();

        if (target instanceof EntityService && (methodName.contains(LOAD) || methodName.contains(FIND))) {
            if (methodName.equals(LOAD) || methodName.equals(LOAD_BY_ID) || methodName.equals(LOAD_BY_UUID)) {
                return load(invocation);
            } else {
                return defaultLookup(invocation);
            }

        } else {
            return invocation.proceed();
        }
    }

    /**
     * This method redirects all load calls to loadById to improve caching
     * efficiency across multiple types of load call.
     */
    @SuppressWarnings("unchecked")
    private Object load(final MethodInvocation invocation) throws Throwable {
        final Method method = invocation.getMethod();
        final Object target = getTarget(invocation);

        final EntityService<?> entityService = (EntityService<?>) target;

        final String type = entityService.getEntityType();
        DocRef docRef = null;
        Set<String> fetchSet = null;

        if (method.getParameterTypes().length >= 1) {
            if (long.class.isAssignableFrom(method.getParameterTypes()[0])) {
                docRef = new DocRef();
                docRef.setType(type);
                docRef.setId((Long) invocation.getArguments()[0]);
            } else if (String.class.isAssignableFrom(method.getParameterTypes()[0])) {
                docRef = new DocRef();
                docRef.setType(type);
                docRef.setUuid((String) invocation.getArguments()[0]);
            } else if (Entity.class.isAssignableFrom(method.getParameterTypes()[0])) {
                final Entity entity = (Entity) invocation.getArguments()[0];
                // If no entity has been supplied to load then return null.
                if (entity == null) {
                    return null;
                }

                docRef = DocRefUtil.create(entity);
            } else {
                throw new EntityServiceException("Unexpected parameter type: " + method.getParameterTypes()[0].getName());
            }
        }

        if (method.getParameterTypes().length >= 2) {
            if (Set.class.isAssignableFrom(method.getParameterTypes()[1])) {
                fetchSet = (Set<String>) invocation.getArguments()[1];
            } else {
                throw new EntityServiceException("Unexpected parameter type: " + method.getParameterTypes()[1].getName());
            }
        }

        if (method.getParameterTypes().length > 2) {
            throw new EntityServiceException("Unexpected parameter type: " + method.getParameterTypes()[2].getName());
        }

        final EntityIdKey key = new EntityIdKey(docRef, fetchSet);

        // Try and get a cached entity from the cache.
        final Element element = cache.get(key);
        Object entity = null;
        if (element == null) {
            // We didn't find a cached entity so load one and put it in the
            // cache.
            entity = serviceCacheMethodInterceptorTransactionHelper.transaction_getEntity(entityService, key);
            if (entity != null && entity.getClass().getName().contains(PROXY_CLASS_NAME)) {
                // Don't store cache items in the cache.
            } else {
                cache.put(new Element(key, entity));
            }
        } else {
            entity = element.getObjectValue();
        }
        return entity;
    }

    private Object defaultLookup(final MethodInvocation invocation) throws Throwable {
        final ReadOnlyList<?> key = keyGenerator.generateKey(invocation);

        // Try and get a cached method result from the cache.
        final Element element = cache.get(key);
        Object result = null;
        if (element == null) {
            // We didn't find a cached result so get one and put it in the
            // cache.
            result = serviceCacheMethodInterceptorTransactionHelper.transaction_proceed(invocation);
            cache.put(new Element(key, result));
        } else {
            result = element.getObjectValue();
        }
        return result;
    }

    public static class EntityIdKey implements Serializable {
        private static final long serialVersionUID = 3598470074084886609L;

        public final DocRef docRef;
        public final Set<String> fetchSet;
        public final int hashCode;

        public EntityIdKey(final DocRef docRef, final Set<String> fetchSet) {
            this.docRef = docRef;
            this.fetchSet = fetchSet;

            final HashCodeBuilder builder = new HashCodeBuilder();
            builder.append(docRef);
            builder.append(fetchSet);
            hashCode = builder.toHashCode();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof EntityIdKey)) {
                return false;
            }

            final EntityIdKey key = (EntityIdKey) o;
            final EqualsBuilder builder = new EqualsBuilder();
            builder.append(docRef, key.docRef);
            builder.append(fetchSet, key.fetchSet);
            return builder.isEquals();
        }

        @Override
        public String toString() {
            return docRef + " " + fetchSet;
        }
    }
}
