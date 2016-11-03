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

package stroom.util.zip;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class StroomHeaderArguments {
    public static final String GUID = "GUID";
    public static final String COMPRESSION = "Compression";
    public static final String COMPRESSION_ZIP = "ZIP";
    public static final String COMPRESSION_GZIP = "GZIP";
    public static final String COMPRESSION_NONE = "NONE";

    public static final Set<String> VALID_COMPRESSION_SET = Collections
            .unmodifiableSet(new HashSet<String>(Arrays.asList(COMPRESSION_GZIP, COMPRESSION_ZIP, COMPRESSION_NONE)));

    public static final String CONTENT_LENGTH = "content-length";
    public static final String USER_AGENT = "user-agent";

    public static final String REMOTE_ADDRESS = "RemoteAddress";
    public static final String REMOTE_HOST = "RemoteHost";
    public static final String RECEIVED_TIME = "ReceivedTime";
    public static final String RECEIVED_PATH = "ReceivedPath";
    public static final String EFFECTIVE_TIME = "EffectiveTime";
    public static final String REMOTE_DN = "RemoteDN";
    public static final String REMOTE_CERT_EXPIRY = "RemoteCertExpiry";
    public static final String REMOTE_FILE = "RemoteFile";

    public static final String STREAM_SIZE = "StreamSize";

    public static final String STROOM_STATUS = "Stroom-Status";

    public static final String FEED = "Feed";

    public static final Set<String> HEADER_CLONE_EXCLUDE_SET = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList("accept", "connection", "content-length", "transfer-encoding", "expect", COMPRESSION)));

}
