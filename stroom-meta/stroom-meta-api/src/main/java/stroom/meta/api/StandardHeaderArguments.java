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

package stroom.meta.api;

import stroom.util.shared.string.CIKey;
import stroom.util.shared.string.CIKeys;

import java.util.Set;

public interface StandardHeaderArguments {

    /**
     * Intended to be set to a new GUID on receipt by the first proxy/stroom instance
     * then never changed.
     */
    String GUID = "GUID";
    String COMPRESSION = "Compression";
    String COMPRESSION_ZIP = "ZIP";
    String COMPRESSION_GZIP = "GZIP";
    String COMPRESSION_NONE = "NONE";

    Set<String> VALID_COMPRESSION_SET = Set.of(
            COMPRESSION_GZIP,
            COMPRESSION_ZIP,
            COMPRESSION_NONE);

    String CONTENT_LENGTH = "content-length";

    String CONTENT_ENCODING = "content-encoding";
    String CONTENT_ENCODING_GZIP = "gzip";
    String CONTENT_ENCODING_DEFLATE = "deflate";
    String CONTENT_ENCODING_BROTLI = "br";
    String CONTENT_ENCODING_ZSTD = "zstd";

    String USER_AGENT = "user-agent";

    /**
     * The IP address of the client that sent data to the FIRST proxy/stroom instance
     * in the chain.
     */
    String REMOTE_ADDRESS = "RemoteAddress";
    /**
     * The hostname of the client that sent data to the FIRST proxy/stroom instance
     * in the chain.
     */
    String REMOTE_HOST = "RemoteHost";
    /**
     * To be set to the current time by EACH proxy/stroom instance on receipt.
     */
    String RECEIVED_TIME = "ReceivedTime";
    /**
     * A comma delimited list of ReceivedTime values, oldest first that includes the
     * ReceivedTime value as its last item.
     */
    String RECEIVED_TIME_HISTORY = "ReceivedTimeHistory";
    /**
     * A comma delimited list of hostnames of EACH proxy/stroom instance that have received
     * this data. The most recent host is the last item in the list.
     */
    String RECEIVED_PATH = "ReceivedPath";
    String EFFECTIVE_TIME = "EffectiveTime";
    /**
     * If an X509 certificate is present on a request, this will be set with the subject
     * distinguished name from the certificate.
     */
    String REMOTE_DN = "RemoteDN";
    /**
     * If an X509 certificate is present on a request, this will be set with the expiry
     * datetime of the certificate in Stroom normal date format.
     */
    String REMOTE_CERT_EXPIRY = "RemoteCertExpiry";
    /**
     * Set to the name of the file when uploading file based data to stroom.
     */
    String REMOTE_FILE = "RemoteFile";
    /**
     * To be set to a unique receipt ID by EACH proxy/stroom instance on receipt.
     */
    String RECEIPT_ID = "ReceiptId";
    /**
     * To have ReceiptId appended to it by EACH proxy/stroom instance on receipt.
     */
    String RECEIPT_ID_PATH = "ReceiptIdPath";

    /**
     * The number of the Data Receipt rule that matched the data, or 'NO_MATCH' if
     * no rule matched.
     */
    String DATA_RECEIPT_RULE = "DataReceiptRule";
    /**
     * The unique message from an AWS SQS queue.
     */
    String SQS_MESSAGE_ID = "SqsMessageId";

    // The unique identifier of the user on the IDP
    String UPLOAD_USER_ID = "UploadUserId";
    // Username of the user on the IDP, may not be unique
    String UPLOAD_USERNAME = "UploadUsername";

    String STREAM_SIZE = "StreamSize";

    String STROOM_STATUS = "Stroom-Status";
    String STROOM_ERROR = "Stroom-Error";

    String ACCOUNT_ID = "AccountId";
    String ACCOUNT_NAME = "AccountName";
    String SYSTEM = "System";
    String COMPONENT = "Component";
    String FEED = "Feed";
    String HOST = "Host"; // Receiving host
    String TYPE = "Type";
    String ENVIRONMENT = "Environment";
    String FORMAT = "Format"; // The data format, e.g. XML, JSON, CSV, etc.
    String CONTEXT_FORMAT = "ContextFormat"; // The data format of the context sub-stream, e.g. XML, JSON, CSV, etc.
    String SCHEMA = "Schema"; // The name of the schema for the data format if applicable, e.g. event-logging
    String SCHEMA_VERSION = "SchemaVersion"; // The version of the schema if applicable, e.g. 4.0.1

    String ENCODING = "Encoding";
    String CONTEXT_ENCODING = "ContextEncoding";
    String CLASSIFICATION = "Classification";

    // Typically added in by nginx
    String X_FORWARDED_FOR = "X-Forwarded-For";

    Set<String> HEADER_CLONE_EXCLUDE_SET = Set.of(
            "accept",
            "connection",
            "content-length",
            "transfer-encoding",
            "expect",
            COMPRESSION);

    /**
     * A base allow-set of meta keys for inclusion in the request when proxy forwards data downstream.
     * This set represents the headers that stroom/proxy may make use of when ingesting data.
     */
    Set<CIKey> HTTP_POST_BASE_META_ALLOW_SET = Set.of(
            CIKeys.ACCOUNT_ID,
            CIKeys.ACCOUNT_NAME,
            CIKeys.CLASSIFICATION,
            CIKeys.COMPONENT,
            CIKeys.CONTEXT_ENCODING,
            CIKeys.CONTEXT_FORMAT,
            CIKeys.ENCODING,
            CIKeys.ENVIRONMENT,
            CIKeys.FEED,
            CIKeys.FORMAT,
            CIKeys.GUID,
            CIKeys.SCHEMA,
            CIKeys.SCHEMA_VERSION,
            CIKeys.SYSTEM,
            CIKeys.TYPE);

    /**
     * Header keys for values that are date/time strings
     */
    Set<String> DATE_HEADER_KEYS = Set.of(
            EFFECTIVE_TIME,
            RECEIVED_TIME);
}

