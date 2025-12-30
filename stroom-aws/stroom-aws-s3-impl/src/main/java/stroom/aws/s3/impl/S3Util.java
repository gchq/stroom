/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.aws.s3.impl;


import java.util.Locale;
import java.util.regex.Pattern;

public class S3Util {

    private static final Pattern S3_META_KEY_INVALID_CHARS_PATTERN = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern S3_BUCKET_NAME_INVALID_CHARS_PATTERN = Pattern.compile("[^0-9a-z.-]");
    private static final Pattern S3_KEY_NAME_INVALID_CHARS_PATTERN = Pattern.compile("[^0-9a-zA-Z!-_.*'()/]");
    private static final Pattern LEADING_HYPHENS = Pattern.compile("^-+");
    private static final Pattern TRAILING_HYPHENS = Pattern.compile("-+$");
    private static final Pattern LEADING_SLASH = Pattern.compile("^/+");
    private static final Pattern TRAILING_SLASH = Pattern.compile("/+$");
    private static final Pattern MULTI_SLASH = Pattern.compile("/+");

    private S3Util() {
    }

    public static String cleanS3MetaDataKey(final String metaKey) {
        String s3Name = metaKey;
        s3Name = s3Name.toLowerCase(Locale.ROOT);
        s3Name = S3_META_KEY_INVALID_CHARS_PATTERN.matcher(s3Name).replaceAll("-");
        s3Name = LEADING_HYPHENS.matcher(s3Name).replaceAll("");
        s3Name = TRAILING_HYPHENS.matcher(s3Name).replaceAll("");
        return s3Name;
    }

    public static String cleanBucketName(String bucketName) {
        bucketName = bucketName.toLowerCase(Locale.ROOT);
        bucketName = S3_BUCKET_NAME_INVALID_CHARS_PATTERN.matcher(bucketName).replaceAll("-");
        bucketName = LEADING_HYPHENS.matcher(bucketName).replaceAll("");
        bucketName = TRAILING_HYPHENS.matcher(bucketName).replaceAll("");
        return bucketName;
    }

    public static String cleanKeyName(String keyName) {
        keyName = S3_KEY_NAME_INVALID_CHARS_PATTERN.matcher(keyName).replaceAll("-");
        keyName = MULTI_SLASH.matcher(keyName).replaceAll("/");
        keyName = LEADING_SLASH.matcher(keyName).replaceAll("");
        keyName = TRAILING_SLASH.matcher(keyName).replaceAll("");
        return keyName;
    }
}
