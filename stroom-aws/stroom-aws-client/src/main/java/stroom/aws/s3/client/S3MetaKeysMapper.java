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

package stroom.aws.s3.client;


import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.shared.MetaFields;
import stroom.query.api.datasource.QueryField;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class S3MetaKeysMapper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3MetaKeysMapper.class);

    /**
     * Key => cleaned key
     */
    private final Map<CIKey, CIKey> forwardMap;
    /**
     * Cleaned key => key
     */
    private final Map<CIKey, CIKey> reverseMap;

    @Inject
    public S3MetaKeysMapper() {
        final List<String> fieldNames = MetaFields.getAllFields()
                .stream()
                .map(QueryField::getFldName)
                .filter(NullSafe::isNonBlankString)
                .toList();

        final Set<String> allKeys = Stream.concat(
                        fieldNames.stream(),
                        StandardHeaderArguments.HTTP_POST_BASE_META_ALLOW_SET.stream()
                                .map(CIKey::get))
                .collect(Collectors.toSet());

        forwardMap = new HashMap<>(allKeys.size());
        reverseMap = new HashMap<>(allKeys.size());

        allKeys.forEach(key -> {
            final CIKey fieldCIKey = CIKey.internStaticKey(key);
            final String cleaned = S3Util.cleanS3MetaDataKey(key);
            final CIKey cleanedCIKey = Objects.equals(key, cleaned)
                    ? fieldCIKey
                    : CIKey.internStaticKey(cleaned);

            LOGGER.debug("fieldCIKey: '{}' => cleanedCIKey: '{}'", fieldCIKey, cleanedCIKey);
            final CIKey existingS3Key = reverseMap.get(cleanedCIKey);
            if (existingS3Key != null) {
                throw new RuntimeException(LogUtil.message("Duplicate cleaned key '{}' for keys '{}' and '{}'",
                        cleaned, key, existingS3Key.get()));
            }
            forwardMap.put(fieldCIKey, cleanedCIKey);
            reverseMap.put(cleanedCIKey, fieldCIKey);
        });
    }

    /**
     * Convert fieldName into a form that is safe for use as an S3 metadata key.
     */
    public Optional<String> getS3Key(final String fieldName) {
        if (NullSafe.isBlankString(fieldName)) {
            return Optional.empty();
        } else {
            return getS3Key(CIKey.of(fieldName))
                    .map(CIKey::get);
        }
    }

    /**
     * Convert fieldName into a form that is safe for use as an S3 metadata key.
     */
    public Optional<CIKey> getS3Key(final CIKey fieldName) {
        return Optional.ofNullable(forwardMap.get(fieldName));
    }

    /**
     * Convert an S3 metadata key back to its original form.
     */
    public Optional<String> getOriginalKey(final String s3MetaKey) {
        if (NullSafe.isBlankString(s3MetaKey)) {
            return Optional.empty();
        } else {
            return getOriginalKey(CIKey.of(s3MetaKey))
                    .map(CIKey::get);
        }
    }

    /**
     * Convert an S3 metadata key back to its original form.
     */
    public Optional<CIKey> getOriginalKey(final CIKey s3MetaKey) {
        return Optional.ofNullable(reverseMap.get(s3MetaKey));
    }
}
