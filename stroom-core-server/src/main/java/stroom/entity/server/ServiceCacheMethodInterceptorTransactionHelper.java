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

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.shared.EntityService;
import stroom.entity.shared.HasLoadById;
import stroom.entity.shared.HasLoadByUuid;
import stroom.query.api.DocRef;

@Component
@Transactional(readOnly = true)
public class ServiceCacheMethodInterceptorTransactionHelper {
    public Object transaction_getEntity(final EntityService<?> entityService,
                                        final ServiceCacheMethodInterceptor.EntityIdKey entityKey) {
        DocRef docRef = entityKey.docRef;

        if (docRef.getId() != null && entityService instanceof HasLoadById) {
            if (entityKey.fetchSet == null) {
                return ((HasLoadById) entityService).loadById(docRef.getId());
            } else {
                return ((HasLoadById) entityService).loadById(docRef.getId(), entityKey.fetchSet);
            }
        }

        if (entityKey.docRef.getUuid() != null && entityService instanceof HasLoadByUuid) {
            if (entityKey.fetchSet == null) {
                return ((HasLoadByUuid) entityService).loadByUuid(entityKey.docRef.getUuid());
            } else {
                return ((HasLoadByUuid) entityService).loadByUuid(entityKey.docRef.getUuid(), entityKey.fetchSet);
            }
        }

        throw new RuntimeException("Entity service does not support HasLoadById or HasLoadByUuid");
    }

    public Object transaction_proceed(final MethodInvocation invocation) throws Throwable {
        return invocation.proceed();
    }
}