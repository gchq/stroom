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

/**
 * Used to set
 * <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingMetadata.html#SysMetadata">
 * user controlled system-defined S3 metadata
 * </a>
 *
 * @param cacheControl       Sets {@code Cache-Control}
 * @param contentDisposition Sets {@code Content-Disposition}
 * @param contentEncoding    Sets {@code Content-Encoding}
 * @param contentType        Sets {@code Content-Type}
 */
public record S3UploadProperties(String cacheControl,
                                 String contentDisposition,
                                 String contentEncoding,
                                 String contentType) {

    private S3UploadProperties(final Builder builder) {
        this(
                builder.cacheControl,
                builder.contentDisposition,
                builder.contentEncoding,
                builder.contentType);
    }

    public Builder copy() {
        return builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(final S3UploadProperties copy) {
        final Builder builder = new Builder();
        builder.cacheControl = copy.cacheControl();
        builder.contentDisposition = copy.contentDisposition();
        builder.contentEncoding = copy.contentEncoding();
        builder.contentType = copy.contentType();
        return builder;
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String cacheControl;
        private String contentDisposition;
        private String contentEncoding;
        private String contentType;

        private Builder() {
        }

        public Builder cacheControl(final String cacheControl) {
            this.cacheControl = cacheControl;
            return this;
        }

        public Builder contentDisposition(final String contentDisposition) {
            this.contentDisposition = contentDisposition;
            return this;
        }

        public Builder contentEncoding(final String contentEncoding) {
            this.contentEncoding = contentEncoding;
            return this;
        }

        public Builder contentType(final String contentType) {
            this.contentType = contentType;
            return this;
        }

        public S3UploadProperties build() {
            return new S3UploadProperties(this);
        }
    }
}
