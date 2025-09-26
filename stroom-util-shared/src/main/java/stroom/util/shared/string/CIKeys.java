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

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    // Have to be concurrent maps as various classes in potentially multiple threads will call
    // commonKey(). Not much perf difference as compared to a HashMap in tests and ConcurrentHashMap
    // is non-blocking for reads which is what these will mostly see
    // TODO consider borrowing the CopyOnWriteMap from the kafka-clients lib to improve the read perf
    //  as we have hardly any writes and MANY reads. Needs to be a copy as this is shared with GWT.
    //  CopyOnWriteMap would need a method for doing bulk puts to save the copy on each one.
    private static final Map<String, CIKey> KEY_TO_COMMON_CIKEY_MAP = new ConcurrentHashMap<>();
    private static final Map<String, CIKey> LOWER_KEY_TO_COMMON_CIKEY_MAP = new ConcurrentHashMap<>();

    public static final CIKey EMPTY = internCommonKey("");

    // Upper camel case keys
    public static final CIKey ACCOUNT_ID = internCommonKey("AccountId");
    public static final CIKey ACCOUNT_NAME = internCommonKey("AccountName");
    public static final CIKey CLASSIFICATION = internCommonKey("Classification");
    public static final CIKey CONTEXT_FORMAT = internCommonKey("ContextFormat");
    public static final CIKey CONTEXT_ENCODING = internCommonKey("ContextEncoding");
    public static final CIKey COMPONENT = internCommonKey("Component");
    public static final CIKey COMPRESSION = internCommonKey("Compression");
    public static final CIKey DURATION = internCommonKey("Duration");
    public static final CIKey EFFECTIVE_TIME = internCommonKey("EffectiveTime");
    public static final CIKey ENCODING = internCommonKey("Encoding");
    public static final CIKey END = internCommonKey("End");
    public static final CIKey ENVIRONMENT = internCommonKey("Environment");
    public static final CIKey EVENT_ID = internCommonKey("EventId");
    public static final CIKey EVENT_TIME = internCommonKey("EventTime");
    public static final CIKey FEED = internCommonKey("Feed");
    public static final CIKey FORMAT = internCommonKey("Format");
    public static final CIKey FORWARD_ERROR = internCommonKey("ForwardError");
    public static final CIKey FILES = internCommonKey("Files");
    public static final CIKey GUID = internCommonKey("GUID");
    public static final CIKey ID = internCommonKey("Id");
    public static final CIKey INDEX = internCommonKey("Index");
    public static final CIKey INSERT_TIME = internCommonKey("InsertTime");
    public static final CIKey KEY = internCommonKey("Key");
    public static final CIKey KEY_END = internCommonKey("KeyEnd");
    public static final CIKey KEY_START = internCommonKey("KeyStart");
    public static final CIKey NAME = internCommonKey("Name");
    public static final CIKey NODE = internCommonKey("Node");
    public static final CIKey PARTITION = internCommonKey("Partition");
    public static final CIKey PIPELINE = internCommonKey("Pipeline");
    public static final CIKey PROXY_FORWARD_ID = internCommonKey("ProxyForwardId");
    public static final CIKey RECEIVED_PATH = internCommonKey("ReceivedPath");
    public static final CIKey RECEIVED_TIME = internCommonKey("ReceivedTime");
    public static final CIKey RECEIVED_TIME_HISTORY = internCommonKey("ReceivedTimeHistory");
    public static final CIKey REMOTE_ADDRESS = internCommonKey("RemoteAddress");
    public static final CIKey REMOTE_CERT_EXPIRY = internCommonKey("RemoteCertExpiry");
    public static final CIKey REMOTE_FILE = internCommonKey("RemoteFile");
    public static final CIKey REMOTE_HOST = internCommonKey("RemoteHost");
    public static final CIKey REMOTE_DN = internCommonKey("RemoteDn");
    public static final CIKey SCHEMA = internCommonKey("Schema");
    public static final CIKey SCHEMA_VERSION = internCommonKey("SchemaVersion");
    public static final CIKey START = internCommonKey("Start");
    public static final CIKey STATUS = internCommonKey("Status");
    public static final CIKey STREAM_ID = internCommonKey("StreamId");
    public static final CIKey STREAM_SIZE = internCommonKey("StreamSize");
    public static final CIKey SUBJECT = internCommonKey("Subject");
    public static final CIKey SYSTEM = internCommonKey("System");
    public static final CIKey TERMINAL = internCommonKey("Terminal");
    public static final CIKey TIME = internCommonKey("Time");
    public static final CIKey TITLE = internCommonKey("Title");
    public static final CIKey TYPE = internCommonKey("Type");
    public static final CIKey UPLOAD_USERNAME = internCommonKey("UploadUsername");
    public static final CIKey UPLOAD_USER_ID = internCommonKey("UploadUserId");
    public static final CIKey USER = internCommonKey("User");
    public static final CIKey UUID = internCommonKey("UUID");
    public static final CIKey VALUE = internCommonKey("Value");
    public static final CIKey VALUE_TYPE = internCommonKey("ValueType");

    // Lower case keys
    public static final CIKey ACCEPT = internCommonKey("accept");
    public static final CIKey CONNECTION = internCommonKey("connection");
    public static final CIKey EXPECT = internCommonKey("expect");

    // kebab case keys
    public static final CIKey CONTENT___ENCODING = internCommonKey("content-encoding");
    public static final CIKey CONTENT___LENGTH = internCommonKey("content-length");
    public static final CIKey TRANSFER___ENCODING = internCommonKey("transfer-encoding");
    public static final CIKey USER___AGENT = internCommonKey("user-agent");
    public static final CIKey X___FORWARDED___FOR = internCommonKey("X-Forwarded-For");

    // Upper sentence case keys
    public static final CIKey ANALYTIC__RULE = internCommonKey("Analytic Rule");
    public static final CIKey CREATE__TIME = internCommonKey("Create Time");
    public static final CIKey CREATE__TIME__MS = internCommonKey("Create Time Ms");
    public static final CIKey DOC__COUNT = internCommonKey("Doc Count");
    public static final CIKey EFFECTIVE__TIME = internCommonKey("Effective Time");
    public static final CIKey END__TIME = internCommonKey("End Time");
    public static final CIKey END__TIME__MS = internCommonKey("End Time Ms");
    public static final CIKey ERROR__COUNT = internCommonKey("Error Count");
    public static final CIKey FATAL__ERROR__COUNT = internCommonKey("Fatal Error Count");
    public static final CIKey FILE__SIZE = internCommonKey("File Size");
    public static final CIKey INDEX__NAME = internCommonKey("Index Name");
    public static final CIKey INFO__COUNT = internCommonKey("Info Count");
    public static final CIKey LAST__COMMIT = internCommonKey("Last Commit");
    public static final CIKey META__ID = internCommonKey("Meta Id");
    public static final CIKey PARENT__CREATE__TIME = internCommonKey("Parent Create Time");
    public static final CIKey PARENT__FEED = internCommonKey("Parent Feed");
    public static final CIKey PARENT__ID = internCommonKey("Parent Id");
    public static final CIKey PARENT__STATUS = internCommonKey("Parent Status");
    public static final CIKey PIPELINE__NAME = internCommonKey("Pipeline Name");
    public static final CIKey PROCESSOR__DELETED = internCommonKey("Processor Deleted");
    public static final CIKey PROCESSOR__ENABLED = internCommonKey("Processor Enabled");
    public static final CIKey PROCESSOR__FILTER__DELETED = internCommonKey("Processor Filter Deleted");
    public static final CIKey PROCESSOR__FILTER__ENABLED = internCommonKey("Processor Filter Enabled");
    public static final CIKey PROCESSOR__FILTER__ID = internCommonKey("Processor Filter Id");
    public static final CIKey PROCESSOR__FILTER__LAST__POLL__MS = internCommonKey("Processor Filter Last Poll Ms");
    public static final CIKey PROCESSOR__FILTER__PRIORITY = internCommonKey("Processor Filter Priority");
    public static final CIKey PROCESSOR__FILTER__UUID = internCommonKey("Processor Filter UUID");
    public static final CIKey PROCESSOR__ID = internCommonKey("Processor Id");
    public static final CIKey PROCESSOR__PIPELINE = internCommonKey("Processor Pipeline");
    public static final CIKey PROCESSOR__TASK__ID = internCommonKey("Processor Task Id");
    public static final CIKey PROCESSOR__TYPE = internCommonKey("Processor Type");
    public static final CIKey PROCESSOR__UUID = internCommonKey("Processor UUID");
    public static final CIKey RAW__SIZE = internCommonKey("Raw Size");
    public static final CIKey READ__COUNT = internCommonKey("Read Count");
    public static final CIKey START__TIME = internCommonKey("Start Time");
    public static final CIKey START__TIME__MS = internCommonKey("Start Time Ms");
    public static final CIKey STATUS__TIME = internCommonKey("Status Time");
    public static final CIKey STATUS__TIME__MS = internCommonKey("Status Time Ms");
    public static final CIKey TASK__ID = internCommonKey("Task Id");
    public static final CIKey VOLUME__GROUP = internCommonKey("Volume Group");
    public static final CIKey VOLUME__PATH = internCommonKey("Volume Path");
    public static final CIKey WARNING__COUNT = internCommonKey("Warning Count");
    public static final CIKey WRITE__COUNT = internCommonKey("Write Count");

    // Reference Data fields
    public static final CIKey FEED__NAME = internCommonKey("Feed Name");
    public static final CIKey LAST__ACCESSED__TIME = internCommonKey("Last Accessed Time");
    public static final CIKey MAP__NAME = internCommonKey("Map Name");
    public static final CIKey PART__NUMBER = internCommonKey("Part Number");
    public static final CIKey PIPELINE__VERSION = internCommonKey("Pipeline Version");
    public static final CIKey PROCESSING__STATE = internCommonKey("Processing State");
    public static final CIKey REFERENCE__LOADER__PIPELINE = internCommonKey("Reference Loader Pipeline");
    public static final CIKey STREAM__ID = internCommonKey("Stream ID");
    public static final CIKey VALUE__REFERENCE__COUNT = internCommonKey("Value Reference Count");

    // Annotations keys
    public static final CIKey ANNO_ASSIGNED_TO = internCommonKey("annotation:AssignedTo");
    public static final CIKey ANNO_COMMENT = internCommonKey("annotation:Comment");
    public static final CIKey ANNO_CREATED_BY = internCommonKey("annotation:CreatedBy");
    public static final CIKey ANNO_CREATED_ON = internCommonKey("annotation:CreatedOn");
    public static final CIKey ANNO_HISTORY = internCommonKey("annotation:History");
    public static final CIKey ANNO_ID = internCommonKey("annotation:Id");
    public static final CIKey ANNO_STATUS = internCommonKey("annotation:Status");
    public static final CIKey ANNO_SUBJECT = internCommonKey("annotation:Subject");
    public static final CIKey ANNO_TITLE = internCommonKey("annotation:Title");
    public static final CIKey ANNO_UPDATED_BY = internCommonKey("annotation:UpdatedBy");
    public static final CIKey ANNO_UPDATED_ON = internCommonKey("annotation:UpdatedOn");

    public static final CIKey UNDERSCORE_EVENT_ID = internCommonKey("__event_id__");
    public static final CIKey UNDERSCORE_STREAM_ID = internCommonKey("__stream_id__");
    public static final CIKey UNDERSCORE_TIME = internCommonKey("__time__");

    private CIKeys() {
    }

    public static CIKey getCommonKey(final String key) {
        if (key == null) {
            return null;
        } else {
            return KEY_TO_COMMON_CIKEY_MAP.get(key);
        }
    }

    public static CIKey getCommonKeyByLowerCase(final String lowerCaseKey) {
        if (lowerCaseKey == null) {
            return null;
        } else {
            return LOWER_KEY_TO_COMMON_CIKEY_MAP.get(lowerCaseKey);
        }
    }

    /**
     * Add key to the map of common keys for re-use.
     * <p>
     * Only intended for use on static and commonly used {@link CIKey} instances due to the cost of
     * string interning and storage.
     * </p>
     */
    static CIKey internCommonKey(final String key) {
        final CIKey ciKey;

        if (key == null) {
            // Not interning null, so just return null
            return null;
        } else {
            // Someone else may have already interned this key so check first
            final CIKey existingCIKey = KEY_TO_COMMON_CIKEY_MAP.get(key);
            if (existingCIKey != null) {
                // Already interned
                ciKey = existingCIKey;
            } else {
                if (key.isEmpty()) {
                    ciKey = CIKey.EMPTY_STRING;
                    addKey(ciKey);
                } else {
                    // Ensure we are using string pool instances for both
                    final String k = key.intern();
                    ciKey = new CIKey(k, CIKey.toLowerCase(k).intern());
                    addKey(ciKey);
                }
            }
        }
        return ciKey;
    }

    private static void addKey(final CIKey ciKey) {
        // Add it to our static maps, so we can get a common CIKey either from its
        // exact case or its lower case form.
        KEY_TO_COMMON_CIKEY_MAP.put(ciKey.get(), ciKey);
        LOWER_KEY_TO_COMMON_CIKEY_MAP.put(ciKey.getAsLowerCase(), ciKey);
    }

    /**
     * @return The set of all common {@link CIKey}s
     */
    static Set<CIKey> commonKeys() {
        //noinspection Java9CollectionFactory // GWT :-(
        return Collections.unmodifiableSet(new HashSet<>(KEY_TO_COMMON_CIKEY_MAP.values()));
    }
}
