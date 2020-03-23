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

package stroom.data.store.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.data.shared.DataResource;
import stroom.data.shared.UploadDataRequest;
import stroom.meta.shared.FindMetaCriteria;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.HasHealthCheck;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;

class DataResourceImpl implements DataResource, HasHealthCheck {
    private final ResourceStore resourceStore;
    private final DataUploadTaskHandler dataUploadTaskHandler;
    private final DataDownloadTaskHandler dataDownloadTaskHandler;
    private final StreamEventLog streamEventLog;
    private final SecurityContext securityContext;

    @Inject
    DataResourceImpl(
            final ResourceStore resourceStore,
            final DataUploadTaskHandler dataUploadTaskHandler,
            final DataDownloadTaskHandler dataDownloadTaskHandler,
            final StreamEventLog streamEventLog,
            final SecurityContext securityContext) {
        this.resourceStore = resourceStore;
        this.dataUploadTaskHandler = dataUploadTaskHandler;
        this.dataDownloadTaskHandler = dataDownloadTaskHandler;
        this.streamEventLog = streamEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public ResourceGeneration download(final FindMetaCriteria criteria) {
        return securityContext.secureResult(PermissionNames.EXPORT_DATA_PERMISSION, () -> {
            ResourceKey resourceKey;
            try {
                // Import file.
                resourceKey = resourceStore.createTempFile("StroomData.zip");
                final Path file = resourceStore.getTempFile(resourceKey);
                String fileName = file.getFileName().toString();
                int index = fileName.lastIndexOf(".");
                if (index != -1) {
                    fileName = fileName.substring(0, index);
                }

                final DataDownloadSettings settings = new DataDownloadSettings();
                dataDownloadTaskHandler.downloadData(criteria, file.getParent(), fileName, settings);

                streamEventLog.exportStream(criteria, null);

            } catch (final RuntimeException e) {
                streamEventLog.exportStream(criteria, e);
                throw EntityServiceExceptionUtil.create(e);
            }
            return new ResourceGeneration(resourceKey, new ArrayList<>());
        });
    }

    @Override
    public ResourceKey upload(final UploadDataRequest request) {
        return securityContext.secureResult(PermissionNames.IMPORT_DATA_PERMISSION, () -> {
            try {
                // Import file.
                final Path file = resourceStore.getTempFile(request.getKey());

                dataUploadTaskHandler.uploadData(
                        request.getFileName(),
                        file,
                        request.getFeedName(),
                        request.getStreamTypeName(),
                        request.getEffectiveMs(),
                        request.getMetaData());

            } catch (final RuntimeException e) {
                throw e;//EntityServiceExceptionUtil.create(e);
            } finally {
                // Delete the import if it was successful
                resourceStore.deleteTempFile(request.getKey());
            }

            return request.getKey();
        });
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}