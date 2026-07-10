/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.data.store.impl.fs;


import stroom.aws.s3.shared.S3ClientConfig;
import stroom.cache.api.TemplateCache;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.ValidationResult;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.util.Objects;

public abstract class AbstractS3StreamStore implements StreamStore {

    private final TemplateCache templateCache;

    protected AbstractS3StreamStore(final TemplateCache templateCache) {
        this.templateCache = templateCache;
    }

    @Override
    public ValidationResult validateVolume(final FsVolume volume) {
        Objects.requireNonNull(volume);
        ValidationResult validationResult = ValidationResult.ok();
        validationResult = validationResult.errorIfNull(
                "S3 Client Configuration must be provided",
                volume.getS3ClientConfigData());

        return validationResult;
    }

    protected S3ClientConfig readS3ClientConfig(final FsVolume fileVolume) {
        final String s3ClientConfigData = fileVolume.getS3ClientConfigData();
        if (NullSafe.isNonBlankString(s3ClientConfigData)) {
            return JsonUtil
                    .readValue(s3ClientConfigData, S3ClientConfig.class);
        } else {
            return null;
        }
    }

    protected TemplateCache getTemplateCache() {
        return templateCache;
    }
}
