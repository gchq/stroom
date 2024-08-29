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

package stroom.util.shared.string;

import java.util.HashMap;
import java.util.Map;

/**
 * A set of static common {@link CIKey} instances
 */
public class CIKeys {

    // Hold some common keys, so we can just re-use instances rather than creating new each time
    // Some of these map values will get used statically in code, but a lot will come from dash/query
    // fields/columns/tokens which are not known at compile time so the map lets us save on
    // object creation for common ones.
    // The cost of a hashmap get is less than the combined cost of CIKey object creation and
    // the toLowerCase call.
    static final Map<String, CIKey> KEY_TO_COMMON_CIKEY_MAP = new HashMap<>();
    static final Map<String, CIKey> LOWER_KEY_TO_COMMON_CIKEY_MAP = new HashMap<>();

    // Upper camel case keys
    public static final CIKey AUTHORIZATION = commonKey("Authorization");
    public static final CIKey COMPRESSION = commonKey("Compression");
    public static final CIKey DURATION = commonKey("Duration");
    public static final CIKey EFFECTIVE_TIME = commonKey("EffectiveTime");
    public static final CIKey END = commonKey("End");
    public static final CIKey EVENT_ID = commonKey("EventId");
    public static final CIKey EVENT_TIME = commonKey("EventTime");
    public static final CIKey FEED = commonKey("Feed");
    public static final CIKey FORWARD_ERROR = commonKey("ForwardError");
    public static final CIKey FILES = commonKey("Files");
    public static final CIKey GUID = commonKey("GUID");
    public static final CIKey ID = commonKey("Id");
    public static final CIKey INDEX = commonKey("Index");
    public static final CIKey INSERT_TIME = commonKey("InsertTime");
    public static final CIKey KEY = commonKey("Key");
    public static final CIKey KEY_END = commonKey("KeyEnd");
    public static final CIKey KEY_START = commonKey("KeyStart");
    public static final CIKey NAME = commonKey("Name");
    public static final CIKey NODE = commonKey("Node");
    public static final CIKey PARTITION = commonKey("Partition");
    public static final CIKey PIPELINE = commonKey("Pipeline");
    public static final CIKey PROXY_FORWARD_ID = commonKey("ProxyForwardId");
    public static final CIKey RECEIVED_PATH = commonKey("ReceivedPath");
    public static final CIKey RECEIVED_TIME = commonKey("ReceivedTime");
    public static final CIKey RECEIVED_TIME_HISTORY = commonKey("ReceivedTimeHistory");
    public static final CIKey REMOTE_ADDRESS = commonKey("RemoteAddress");
    public static final CIKey REMOTE_CERT_EXPIRY = commonKey("RemoteCertExpiry");
    public static final CIKey REMOTE_FILE = commonKey("RemoteFile");
    public static final CIKey REMOTE_HOST = commonKey("RemoteHost");
    public static final CIKey REMOTE_DN = commonKey("RemoteDn");
    public static final CIKey START = commonKey("Start");
    public static final CIKey STATUS = commonKey("Status");
    public static final CIKey STREAM_ID = commonKey("StreamId");
    public static final CIKey STREAM_SIZE = commonKey("StreamSize");
    public static final CIKey SUBJECT = commonKey("Subject");
    public static final CIKey TERMINAL = commonKey("Terminal");
    public static final CIKey TIME = commonKey("Time");
    public static final CIKey TITLE = commonKey("Title");
    public static final CIKey TYPE = commonKey("Type");
    public static final CIKey UPLOAD_USERNAME = commonKey("UploadUsername");
    public static final CIKey UPLOAD_USER_ID = commonKey("UploadUserId");
    public static final CIKey UUID = commonKey("UUID");
    public static final CIKey VALUE = commonKey("Value");
    public static final CIKey VALUE_TYPE = commonKey("ValueType");

    // Lower case keys
    public static final CIKey ACCEPT = commonKey("accept");
    public static final CIKey CONNECTION = commonKey("connection");
    public static final CIKey EXPECT = commonKey("expect");

    // kebab case keys
    public static final CIKey CONTENT___ENCODING = commonKey("content-encoding");
    public static final CIKey CONTENT___LENGTH = commonKey("content-length");
    public static final CIKey TRANSFER___ENCODING = commonKey("transfer-encoding");
    public static final CIKey USER___AGENT = commonKey("user-agent");
    public static final CIKey X___FORWARDED___FOR = commonKey("X-Forwarded-For");

    // Upper sentence case keys
    public static final CIKey ANALYTIC__RULE = commonKey("Analytic Rule");
    public static final CIKey CREATE__TIME = commonKey("Create Time");
    public static final CIKey CREATE__TIME__MS = commonKey("Create Time Ms");
    public static final CIKey DOC__COUNT = commonKey("Doc Count");
    public static final CIKey EFFECTIVE__TIME = commonKey("Effective Time");
    public static final CIKey END__TIME = commonKey("End Time");
    public static final CIKey END__TIME__MS = commonKey("End Time Ms");
    public static final CIKey ERROR__COUNT = commonKey("Error Count");
    public static final CIKey FATAL__ERROR__COUNT = commonKey("Fatal Error Count");
    public static final CIKey FILE__SIZE = commonKey("File Size");
    public static final CIKey INDEX__NAME = commonKey("Index Name");
    public static final CIKey INFO__COUNT = commonKey("Info Count");
    public static final CIKey LAST__COMMIT = commonKey("Last Commit");
    public static final CIKey META__ID = commonKey("Meta Id");
    public static final CIKey PARENT__CREATE__TIME = commonKey("Parent Create Time");
    public static final CIKey PARENT__FEED = commonKey("Parent Feed");
    public static final CIKey PARENT__ID = commonKey("Parent Id");
    public static final CIKey PARENT__STATUS = commonKey("Parent Status");
    public static final CIKey PIPELINE__NAME = commonKey("Pipeline Name");
    public static final CIKey PROCESSOR__DELETED = commonKey("Processor Deleted");
    public static final CIKey PROCESSOR__ENABLED = commonKey("Processor Enabled");
    public static final CIKey PROCESSOR__FILTER__DELETED = commonKey("Processor Filter Deleted");
    public static final CIKey PROCESSOR__FILTER__ENABLED = commonKey("Processor Filter Enabled");
    public static final CIKey PROCESSOR__FILTER__ID = commonKey("Processor Filter Id");
    public static final CIKey PROCESSOR__FILTER__LAST__POLL__MS = commonKey("Processor Filter Last Poll Ms");
    public static final CIKey PROCESSOR__FILTER__PRIORITY = commonKey("Processor Filter Priority");
    public static final CIKey PROCESSOR__FILTER__UUID = commonKey("Processor Filter UUID");
    public static final CIKey PROCESSOR__ID = commonKey("Processor Id");
    public static final CIKey PROCESSOR__PIPELINE = commonKey("Processor Pipeline");
    public static final CIKey PROCESSOR__TASK__ID = commonKey("Processor Task Id");
    public static final CIKey PROCESSOR__TYPE = commonKey("Processor Type");
    public static final CIKey PROCESSOR__UUID = commonKey("Processor UUID");
    public static final CIKey RAW__SIZE = commonKey("Raw Size");
    public static final CIKey READ__COUNT = commonKey("Read Count");
    public static final CIKey START__TIME = commonKey("Start Time");
    public static final CIKey START__TIME__MS = commonKey("Start Time Ms");
    public static final CIKey STATUS__TIME = commonKey("Status Time");
    public static final CIKey STATUS__TIME__MS = commonKey("Status Time Ms");
    public static final CIKey TASK__ID = commonKey("Task Id");
    public static final CIKey VOLUME__GROUP = commonKey("Volume Group");
    public static final CIKey VOLUME__PATH = commonKey("Volume Path");
    public static final CIKey WARNING__COUNT = commonKey("Warning Count");
    public static final CIKey WRITE__COUNT = commonKey("Write Count");

    // Reference Data fields
    public static final CIKey FEED__NAME = commonKey("Feed Name");
    public static final CIKey LAST__ACCESSED__TIME = commonKey("Last Accessed Time");
    public static final CIKey MAP__NAME = commonKey("Map Name");
    public static final CIKey PART__NUMBER = commonKey("Part Number");
    public static final CIKey PIPELINE__VERSION = commonKey("Pipeline Version");
    public static final CIKey PROCESSING__STATE = commonKey("Processing State");
    public static final CIKey REFERENCE__LOADER__PIPELINE = commonKey("Reference Loader Pipeline");
    public static final CIKey STREAM__ID = commonKey("Stream ID");
    public static final CIKey VALUE__REFERENCE__COUNT = commonKey("Value Reference Count");

    // Annotations keys
    public static final CIKey ANNO_ASSIGNED_TO = commonKey("annotation:AssignedTo");
    public static final CIKey ANNO_COMMENT = commonKey("annotation:Comment");
    public static final CIKey ANNO_CREATED_BY = commonKey("annotation:CreatedBy");
    public static final CIKey ANNO_CREATED_ON = commonKey("annotation:CreatedOn");
    public static final CIKey ANNO_HISTORY = commonKey("annotation:History");
    public static final CIKey ANNO_ID = commonKey("annotation:Id");
    public static final CIKey ANNO_STATUS = commonKey("annotation:Status");
    public static final CIKey ANNO_SUBJECT = commonKey("annotation:Subject");
    public static final CIKey ANNO_TITLE = commonKey("annotation:Title");
    public static final CIKey ANNO_UPDATED_BY = commonKey("annotation:UpdatedBy");
    public static final CIKey ANNO_UPDATED_ON = commonKey("annotation:UpdatedOn");

    public static final CIKey UNDERSCORE_EVENT_ID = commonKey("__event_id__");
    public static final CIKey UNDERSCORE_STREAM_ID = commonKey("__stream_id__");
    public static final CIKey UNDERSCORE_TIME = commonKey("__time__");

    private CIKeys() {
    }

    /**
     * Only intended for use on static {@link CIKey} instances due to the cost of
     * interning
     */
    static CIKey commonKey(final String key) {
        final CIKey ciKey;
        if (key == null) {
            ciKey = CIKey.NULL_STRING;
        } else if (key.isEmpty()) {
            ciKey = CIKey.EMPTY_STRING;
        } else {
            // Ensure we are using string pool instances for both
            final String k = key.intern();
            ciKey = new CIKey(k, CIKey.toLowerCase(k).intern());
        }
        // Add it to our static maps, so we can get a common CIKey either from its
        // exact case or its lower case form.
        CIKeys.KEY_TO_COMMON_CIKEY_MAP.put(key, ciKey);
        CIKeys.LOWER_KEY_TO_COMMON_CIKEY_MAP.put(ciKey.getAsLowerCase(), ciKey);
        return ciKey;
    }
}
