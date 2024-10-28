/*
 * Copyright 2024 Crown Copyright
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

package stroom.meta.api;

import stroom.util.shared.string.CIKey;
import stroom.util.shared.string.CIKeys;

import java.util.Set;

public interface StandardHeaderArguments {

    CIKey GUID = CIKeys.GUID;
    CIKey COMPRESSION = CIKeys.COMPRESSION;
    String COMPRESSION_ZIP = "ZIP";
    String COMPRESSION_GZIP = "GZIP";
    String COMPRESSION_NONE = "NONE";

    Set<String> VALID_COMPRESSION_SET = Set.of(
            COMPRESSION_GZIP,
            COMPRESSION_ZIP,
            COMPRESSION_NONE);

    CIKey CONTENT_LENGTH = CIKeys.CONTENT___LENGTH;

    CIKey CONTENT_ENCODING = CIKeys.CONTENT___ENCODING;
    String CONTENT_ENCODING_GZIP = "gzip";
    String CONTENT_ENCODING_DEFLATE = "deflate";
    String CONTENT_ENCODING_BROTLI = "br";
    String CONTENT_ENCODING_ZSTD = "zstd";

    CIKey USER_AGENT = CIKeys.USER___AGENT;

    CIKey REMOTE_ADDRESS = CIKeys.REMOTE_ADDRESS;
    CIKey REMOTE_HOST = CIKeys.REMOTE_HOST;
    CIKey RECEIVED_TIME = CIKeys.RECEIVED_TIME;
    /**
     * A comma delimited list of ReceivedTime values, oldest first that includes the
     * ReceivedTime value as its last item.
     */
    CIKey RECEIVED_TIME_HISTORY = CIKeys.RECEIVED_TIME_HISTORY;
    CIKey RECEIVED_PATH = CIKeys.RECEIVED_PATH;
    CIKey EFFECTIVE_TIME = CIKeys.EFFECTIVE_TIME;
    CIKey REMOTE_DN = CIKeys.REMOTE_DN;
    CIKey REMOTE_CERT_EXPIRY = CIKeys.REMOTE_CERT_EXPIRY;
    CIKey REMOTE_FILE = CIKeys.REMOTE_FILE;

    // The unique identifier of the user on the IDP
    CIKey UPLOAD_USER_ID = CIKeys.UPLOAD_USER_ID;
    // Username of the user on the IDP, may not be unique
    CIKey UPLOAD_USERNAME = CIKeys.UPLOAD_USERNAME;

    CIKey STREAM_SIZE = CIKeys.STREAM_SIZE;

    String STROOM_STATUS = "Stroom-Status";
    String STROOM_ERROR = "Stroom-Error";

    CIKey FEED = CIKeys.FEED;
    CIKey TYPE = CIKeys.TYPE;

    // Typically added in by nginx
    CIKey X_FORWARDED_FOR = CIKeys.X___FORWARDED___FOR;

    Set<CIKey> HEADER_CLONE_EXCLUDE_SET = Set.of(
            CIKeys.ACCEPT,
            CIKeys.CONNECTION,
            CIKeys.CONTENT___LENGTH,
            CIKeys.TRANSFER___ENCODING,
            CIKeys.EXPECT,
            CIKeys.COMPRESSION);

    /**
     * Header keys for values that are date/time strings
     */
    Set<CIKey> DATE_HEADER_KEYS = Set.of(
            CIKeys.EFFECTIVE_TIME,
            CIKeys.RECEIVED_TIME);
}
