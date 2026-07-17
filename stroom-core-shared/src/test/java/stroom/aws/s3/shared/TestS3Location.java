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

package stroom.aws.s3.shared;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestS3Location {

    @Test
    void testInvalidKey() {
        Assertions.assertThatThrownBy(() ->
                        new S3Location("my-region", "my-bucket", "my-key-${var}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
    }

    @Test
    void testInvalidBucket() {
        Assertions.assertThatThrownBy(() ->
                        new S3Location("my-region", "my-bucket-${var}", "my-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket");
    }

    @Test
    void testInvalidRegion() {
        Assertions.assertThatThrownBy(() ->
                        new S3Location("my-region-${var}", "my-bucket", "my-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("region");
    }
}
