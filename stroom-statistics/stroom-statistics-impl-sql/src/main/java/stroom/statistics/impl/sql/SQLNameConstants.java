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

package stroom.statistics.impl.sql;

/**
 * <p>
 * Class used to enforce name standards in the application.
 * </p>
 */
final class SQLNameConstants {
    public static final String DATE_SUFFIX = "_DT";
    public static final String MS_SUFFIX = "_MS";
    public static final String TYPE_SUFFIX = "_TP";
    public static final String STATUS_SUFFIX = "_STAT";
    public static final String COUNT_SUFFIX = "_CT";
    public static final String LIMIT_SUFFIX = "_LMT";
    public static final String ACCESS = "ACCESS";
    public static final String AGE = "AGE";
    public static final String ANALYTICS = "ANAL";
    public static final String APPLICATION = "APP";
    public static final String ATTRIBUTE = "ATR";
    public static final String BENCHMARK = "BMARK";
    public static final String BY = "BY";
    public static final String BYTES = "BYTES";
    public static final String CLASSIFICATION = "CLS";
    public static final String CLUSTER = "CLSTR";
    public static final String CONCURRENT = "CONCUR";
    public static final String CONTEXT = "CTX";
    public static final String CONVERTER = "CONV";
    public static final String COMMIT = "CMT";
    public static final String COUNT = "CT";
    public static final String CREATE = "CRT";
    public static final String CURRENT = "CUR";
    public static final String DASHBOARD = "DASH";
    public static final String DATA = "DAT";
    public static final String DAY = "DAY";
    public static final String DEFAULT = "DEF";
    public static final String DEPENDENCIES = "DEP";
    public static final String DESCRIPTION = "DESCRIP";
    public static final String DEPRECATED = "DEPRC";
    public static final String DICTIONARY = "DICT";
    public static final String DOCUMENT = "DOC";
    public static final String DURATION = "DUR";
    public static final String EFFECTIVE = "EFFECT";
    public static final String ENABLED = "ENBL";
    public static final String ENCODING = "ENC";
    public static final String ENGINE = "ENGINE";
    public static final String EVENT = "EVT";
    public static final String EXPIRY = "EXPIRY";
    public static final String EXTENSION = "EXT";
    public static final String FAILURE = "FAIL";
    public static final String FAVOURITE = "FAVOURITE";
    public static final String FEED = "FD";
    public static final String FIELD = "FLD";
    public static final String FIELDS = "FLDS";
    public static final String FILE = "FILE";
    public static final String FILTER = "FILT";
    public static final String FOLDER = "FOLDER";
    public static final String FREE = "FREE";
    public static final String FROM = "FROM";
    public static final String FUNCTION = "FUNC";
    public static final String GLOBAL = "GLOB";
    public static final String GROUP = "GRP";
    public static final String HASH = "HASH";
    public static final String HOUR = "HR";
    public static final String INDEX = "IDX";
    public static final String JOB = "JB";
    public static final String KEY = "KEY";
    public static final String LAST = "LAST";
    public static final String LOCK = "LK";
    public static final String LOGIN = "LOGIN";
    public static final String MAX = "MAX";
    public static final String MIN = "MIN";
    public static final String NAME = "NAME";
    public static final String NAMESPACE = "NS";
    public static final String NUMBER = "NUM";
    public static final String PURPOSE = "PURP";
    public static final String NODE = "ND";
    public static final String OUTPUT = "OUT";
    public static final String PARTITION = "PART";
    public static final String PATH = "PATH";
    public static final String PARENT = "PARNT";
    public static final String PASSWORD = "PASS";
    public static final String PERMISSION = "PERM";
    public static final String PRECISION = "PRES";
    public static final String PIPELINE = "PIPE";
    public static final String POLICY = "POLICY";
    public static final String POLL = "POLL";
    public static final String PROCESSOR = "PROC";
    public static final String PROPERTY = "PROP";
    public static final String PRIORITY = "PRIOR";
    public static final String RACK = "RK";
    public static final String RECEIVE = "RECV";
    public static final String REFERENCE = "REF";
    public static final String RETENTION = "RETEN";
    public static final String ROLLUP = "ROLLUP";
    public static final String SCHEMA = "SCHEMA";
    public static final String SCRIPT = "SCRIPT";
    public static final String SETTINGS = "SETTINGS";
    public static final String QUERY = "QUERY";
    public static final String RES = "RES";
    public static final String SHARD = "SHRD";
    public static final String SIZE = "SZ";
    public static final String SOURCE = "SRC";
    public static final String SQL = "SQL";
    public static final String STATE = "STATE";
    public static final String STATUS = "STAT";
    public static final String STATISTIC = "STAT";
    public static final String STREAM = "STRM";
    public static final String STRING = "STR";
    public static final String SYSTEM = "SYSTEM";
    public static final String TASK = "TASK";
    public static final String TEXT = "TXT";
    public static final String TIME = "TIME";
    public static final String TEMP = "TMP";
    public static final String TO = "TO";
    public static final String TOTAL = "TOTL";
    public static final String TRACKER = "TRAC";
    public static final String TYPE = "TP";
    public static final String URL = "URL";
    public static final String USED = "USED";
    public static final String USER = "USR";
    public static final String UUID = "UUID";
    public static final String VALID = "VALID";
    public static final String VALUE = "VAL";
    public static final String VERSION = "VER";
    public static final String VISUALISATION = "VIS";
    public static final String VOLUME = "VOL";
    public static final String XML = "XML";
    public static final String XSLT = "XSLT";
    public static final String STATUS_MS = STATUS + MS_SUFFIX;

    private SQLNameConstants() {
        // This is a constants only class so prevent instantiation.
    }
}
