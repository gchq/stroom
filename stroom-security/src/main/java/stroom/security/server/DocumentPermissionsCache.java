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

package stroom.security.server;

import stroom.cache.AbstractCacheBean;
import stroom.entity.shared.DocRef;
import stroom.security.shared.DocumentPermissions;
import net.sf.ehcache.CacheManager;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Component
public class DocumentPermissionsCache extends AbstractCacheBean<DocRef, DocumentPermissions> {
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final DocumentPermissionService documentPermissionService;

    @Inject
    public DocumentPermissionsCache(final CacheManager cacheManager,
            final DocumentPermissionService documentPermissionService) {
        super(cacheManager, "Document Permissions Cache", MAX_CACHE_ENTRIES);
        this.documentPermissionService = documentPermissionService;
        setMaxIdleTime(30, TimeUnit.MINUTES);
        setMaxLiveTime(30, TimeUnit.MINUTES);
    }

    @Override
    protected DocumentPermissions create(final DocRef document) {
        return documentPermissionService.getPermissionsForDocument(document);
    }
}
