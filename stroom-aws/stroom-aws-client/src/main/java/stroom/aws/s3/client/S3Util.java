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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class S3Util {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3Util.class);

    public static final String AWS_USER_DEFINED_META_PREFIX = "x-amz-meta-";

    private static final Pattern S3_META_KEY_INVALID_CHARS_PATTERN = Pattern.compile("[^a-z0-9_-]");
    private static final Pattern S3_BUCKET_NAME_INVALID_CHARS_PATTERN = Pattern.compile("[^0-9a-z.-]");
    private static final Pattern S3_KEY_NAME_INVALID_CHARS_PATTERN = Pattern.compile("[^0-9a-zA-Z!-_.*'()/]");
    private static final Pattern LEADING_HYPHENS = Pattern.compile("^-+");
    private static final Pattern TRAILING_HYPHENS = Pattern.compile("-+$");
    private static final Pattern LEADING_SLASH = Pattern.compile("^/+");
    private static final Pattern TRAILING_SLASH = Pattern.compile("/+$");
    private static final Pattern MULTI_SLASH = Pattern.compile("/+");

    static {
        if (!Objects.equals(AWS_USER_DEFINED_META_PREFIX, AWS_USER_DEFINED_META_PREFIX.toLowerCase())) {
            // This is because of use of CIKey.startsWithLowerCase
            throw new IllegalStateException("Expecting AWS_USER_DEFINED_META_PREFIX to be lower case");
        }
    }

    private S3Util() {
    }

    /**
     * Remove/replace any unwanted characters from the S3 bucket name.
     */
    public static String cleanS3MetaDataKey(final String metaKey) {
        String result = Objects.requireNonNull(metaKey);
        result = result.toLowerCase(Locale.ROOT);
        result = S3_META_KEY_INVALID_CHARS_PATTERN.matcher(result).replaceAll("-");
        result = LEADING_HYPHENS.matcher(result).replaceAll("");
        result = TRAILING_HYPHENS.matcher(result).replaceAll("");
        LOGGER.debug("cleanS3MetaDataKey() - metaKey: '{}', s3Name: '{}'", metaKey, result);
        return result;
    }

    /**
     * Remove/replace any unwanted characters from the S3 bucket name.
     */
    public static String cleanBucketName(final String bucketName) {
        String result = Objects.requireNonNull(bucketName);
        result = result.toLowerCase(Locale.ROOT);
        result = S3_BUCKET_NAME_INVALID_CHARS_PATTERN.matcher(result).replaceAll("-");
        result = LEADING_HYPHENS.matcher(result).replaceAll("");
        result = TRAILING_HYPHENS.matcher(result).replaceAll("");
        LOGGER.debug("cleanBucketName() - bucketName: '{}', result: '{}'", bucketName, result);
        return result;
    }

    /**
     * Remove/replace any unwanted characters from the S3 key name.
     */
    public static String cleanKeyName(final String keyName) {
        String result = Objects.requireNonNull(keyName);
        result = S3_KEY_NAME_INVALID_CHARS_PATTERN.matcher(result).replaceAll("-");
        result = MULTI_SLASH.matcher(result).replaceAll("/");
        result = LEADING_SLASH.matcher(result).replaceAll("");
        result = TRAILING_SLASH.matcher(result).replaceAll("");
        LOGGER.debug("cleanKeyName() - keyName: '{}', result: '{}'", keyName, result);
        return result;
    }

    public static void validateMetadataKey(final String metadataKey) throws IllegalArgumentException {
        if (NullSafe.isNonBlankString(metadataKey)) {
            if (S3_META_KEY_INVALID_CHARS_PATTERN.matcher(metadataKey).find()) {
                throw new IllegalArgumentException(LogUtil.message("Invalid metadata key: '{}'. Pattern: '{}'",
                        metadataKey, S3_META_KEY_INVALID_CHARS_PATTERN));
            }
        }
    }

    // TODO Not sure this is needed, but do just in case the SDK doesn't remove it.
    public static CIKey removeAwsPrefix(final CIKey key) {
        return NullSafe.get(
                key,
                ciKey -> {
                    if (ciKey.startsWithLowerCase(AWS_USER_DEFINED_META_PREFIX)) {
                        ciKey = ciKey.substring(AWS_USER_DEFINED_META_PREFIX.length());
                    }
                    return ciKey;
                });
    }

    // TODO Not sure this is needed, but do just in case the SDK doesn't remove it.
    public static String removeAwsPrefix(final String key) {
        return NullSafe.get(
                key,
                k -> {
                    if (k.startsWith(AWS_USER_DEFINED_META_PREFIX)) {
                        k = k.substring(AWS_USER_DEFINED_META_PREFIX.length());
                    }
                    return k;
                });
    }
}
