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

package stroom.util.zip;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public interface StroomHeaderArguments {
    String GUID = "GUID";
    String COMPRESSION = "Compression";
    String COMPRESSION_ZIP = "ZIP";
    String COMPRESSION_GZIP = "GZIP";
    String COMPRESSION_NONE = "NONE";

   Set<String> VALID_COMPRESSION_SET = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList(COMPRESSION_GZIP, COMPRESSION_ZIP, COMPRESSION_NONE)));

    String CONTENT_LENGTH = "content-length";
    String USER_AGENT = "user-agent";

    String REMOTE_ADDRESS = "RemoteAddress";
    String REMOTE_HOST = "RemoteHost";
    String RECEIVED_TIME = "ReceivedTime";
    String RECEIVED_PATH = "ReceivedPath";
    String EFFECTIVE_TIME = "EffectiveTime";
    String REMOTE_DN = "RemoteDN";
    String REMOTE_CERT_EXPIRY = "RemoteCertExpiry";
    String REMOTE_FILE = "RemoteFile";

    String STREAM_SIZE = "StreamSize";

    String STROOM_STATUS = "Stroom-Status";

    String FEED = "Feed";

    Set<String> HEADER_CLONE_EXCLUDE_SET = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("accept", "connection", "content-length", "transfer-encoding", "expect", COMPRESSION)));
}