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

package stroom.core.tools;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.util.DatabaseTool;
import stroom.util.concurrent.SimpleConcurrentMap;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ModelStringUtil;

import com.google.common.base.Strings;
import org.apache.commons.lang3.mutable.MutableInt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class StreamRestoreTool extends DatabaseTool {

    private static final int KEY_PAD = 30;
    private static final int COUNT_PAD = 10;
    private static final String VOLUME_PATH = "VolumePath";
    private static final String STREAM_TYPE_PATH = "StreamTypePath";
    private static final String FILE_NAME = "FileName";
    private static final String FEED_ID = "FeedId";
    private static final String STREAM_ID = "StreamId";
    private static final String PARENT_STREAM_ID = "ParentStreamId";
    private static final String CREATE_TIME = "CreateTime";
    private static final String EFFECTIVE_TIME = "EffectiveTime";
    private static final String DATE_PATH = "DatePath";
    private static final String DEPTH = "Depth";

    private final BufferedReader inputReader = new BufferedReader(
            new InputStreamReader(System.in, StreamUtil.DEFAULT_CHARSET));
    private final SimpleConcurrentMap<String, KeyCount> streamTypeStreamCount =
            new SimpleConcurrentMap<String, KeyCount>() {
                @Override
                protected KeyCount initialValue(final String key) {
                    return new KeyCount(key);
                }
            };

    private final SimpleConcurrentMap<List<String>, KeyCount> streamTypeFeedStreamCount =
            new SimpleConcurrentMap<List<String>, KeyCount>() {
                @Override
                protected KeyCount initialValue(final List<String> key) {
                    return new KeyCount(key);
                }
            };

    private final SimpleConcurrentMap<String, SimpleConcurrentMap<String, SimpleConcurrentMap<String, KeyCount>>>
            streamTypeFeedDateStreamCount =
            new SimpleConcurrentMap<String, SimpleConcurrentMap<String, SimpleConcurrentMap<String, KeyCount>>>() {
                @Override
                protected SimpleConcurrentMap<String, SimpleConcurrentMap<String, KeyCount>> initialValue(
                        final String key) {
                    return new SimpleConcurrentMap<String, SimpleConcurrentMap<String, KeyCount>>() {
                        @Override
                        protected SimpleConcurrentMap<String, KeyCount> initialValue(final String key) {
                            return new SimpleConcurrentMap<String, KeyCount>() {
                                @Override
                                protected KeyCount initialValue(final String key) {
                                    return new KeyCount(key);
                                }
                            };
                        }
                    };
                }
            };
    private String deleteFile = null;
    private Map<String, Long> pathStreamTypeMap = null;
    private final Map<String, Long> pathVolumeMap = null;
    private boolean mock = false;
    private boolean inspect = false;
    private boolean sortKey = false;

    public static void main(final String[] args) {
        new StreamRestoreTool().doMain(args);
    }

    public void setDeleteFile(final String deleteFile) {
        this.deleteFile = deleteFile;
    }

    public String readLine(final String question) {
        try {
            System.out.print(question + " : ");
            return inputReader.readLine();
        } catch (final IOException e) {
            e.printStackTrace();
            writeLine(e.getMessage());
            System.exit(1);
            return null;
        }
    }

    private Map<String, Long> getPathStreamTypeMap() throws SQLException {
        if (pathStreamTypeMap == null) {
            pathStreamTypeMap = new HashMap<>();

            // TODO : @66 FIX THIS
//            final String sql = "select " + StreamTypeEntity.NAME + "," + StreamTypeEntity.ID +
//            " from " + StreamTypeEntity.TABLE_NAME;
//            try (final Connection connection = getConnection()) {
//                try (final Statement statement = connection.createStatement()) {
//                    try (final ResultSet resultSet = statement.executeQuery(sql)) {
//                        while (resultSet.next()) {
//                            final String name = resultSet.getString(1);
//                            final long id = resultSet.getLong(2);
//                            final String path = FileSystemStreamTypePaths.getPath(name);
//                            pathStreamTypeMap.put(path, id);
//                        }
//                    }
//                }
//            }
        }
        return pathStreamTypeMap;
    }
//
//    private Map<String, Long> getPathVolumeMap() throws SQLException {
//        if (pathVolumeMap == null) {
//            pathVolumeMap = new HashMap<>();
//            final String sql = "select " + VolumeEntity.PATH + "," + VolumeEntity.ID +
//            " from " + VolumeEntity.TABLE_NAME;
//            try (final Connection connection = getConnection()) {
//                try (final Statement statement = connection.createStatement()) {
//                    try (final ResultSet resultSet = statement.executeQuery(sql)) {
//                        while (resultSet.next()) {
//                            pathVolumeMap.put(resultSet.getString(1), resultSet.getLong(2));
//                        }
//                    }
//                }
//            }
//        }
//        return pathVolumeMap;
//    }

//    private Map<Long, String> getFeedIdNameMap() throws SQLException {
//        if (feedIdNameMap == null) {
//            feedIdNameMap = new HashMap<>();
//            try (final Connection connection = getConnection()) {
//                try (final Statement statement = connection.createStatement()) {
//                    try (final ResultSet resultSet = statement.executeQuery("select " +
//                    FeedEntity.ID + "," + SQLNameConstants.NAME + " from " + FeedEntity.TABLE_NAME)) {
//                        while (resultSet.next()) {
//                            feedIdNameMap.put(resultSet.getLong(1), resultSet.getString(2));
//                        }
//                    }
//                }
//            }
//        }
//        return feedIdNameMap;
//    }

    private void writeLine(final String msg) {
        System.out.println(msg);
    }

    private char readQuestion(final String question, final char[] options, final char def) {
        final String result = readLine(question + " " + Arrays.toString(options) + " " + def + "*")
                .toLowerCase()
                .trim();

        if (result.length() == 0) {
            return def;
        }

        for (final char opt : options) {
            if (opt == result.charAt(0)) {
                return opt;
            }
        }
        return def;
    }

    @Override
    public void run() {
        String fileName = null;

        if (deleteFile != null) {
            fileName = deleteFile;
        } else {
            fileName = readLine("Please enter file name to process");
        }
        try (final BufferedReader reader = Files.newBufferedReader(Paths.get(fileName), StreamUtil.DEFAULT_CHARSET)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(".")) {
                    final StringTokenizer stringTokenizer = new StringTokenizer(line, "/");
                    while (stringTokenizer.hasMoreTokens()) {
                        final String part = stringTokenizer.nextToken();
                        if (part.equals("store")) {
                            break;
                        }
                    }
                    final String type = stringTokenizer.nextToken();
                    final String date = stringTokenizer.nextToken() +
                                        "/" +
                                        stringTokenizer.nextToken() +
                                        "/" +
                                        stringTokenizer.nextToken();

                    final String file = line.substring(line.lastIndexOf("/"));
                    final String feed = file.substring(1, file.indexOf("="));

                    int dotCount = 0;
                    int dotPos = 0;
                    while ((dotPos = (file.indexOf('.', dotPos) + 1)) > 0) {
                        dotCount++;
                    }

                    if (dotCount == 2) {
                        streamTypeStreamCount.get(type).getCount().increment();
                        streamTypeFeedStreamCount.get(Arrays.asList(type, feed)).getCount().increment();
                        streamTypeFeedDateStreamCount.get(type).get(feed).get(date).getCount().increment();
                    }
                }
            }

            final List<KeyCount> sortedList = writeTable(streamTypeStreamCount.values(), "Stream Types");
            final Map<String, Character> streamTypeResponse = new HashMap<>();

            if (!inspect) {
                for (final KeyCount keyCount : sortedList) {
                    final char response = readQuestion(
                            keyCount.toString() + " (D)elete, (R)estore, (I)nspect, (S)kip",
                            new char[]{'d', 'r', 'i', 's'},
                            's');

                    streamTypeResponse.put(keyCount.getKey().get(0), response);

                    if (response == 'd') {
                        processStreamTypeFeed(fileName, keyCount.getKey().get(0), null, response);
                    }

                }
            }

            writeLine("");

            final List<KeyCount> sortedStreamTypeFeed = new ArrayList<>(streamTypeFeedStreamCount.values());
            sort(sortedStreamTypeFeed);

            // TODO : @66 FIX THIS
//            for (final KeyCount streamTypeFeed : sortedStreamTypeFeed) {
//                final String streamType = streamTypeFeed.getKey().get(0);
//                final String feed = streamTypeFeed.getKey().get(1);
//                if (inspect || streamTypeResponse.get(streamType).charValue() == 'i') {
//                    final String feedName = getFeedIdNameMap().get(Long.parseLong(feed));
//
//                    final String longLabel = "Feed " + feed + " '" + feedName + "', Stream Type " + streamType;
//
//                    writeTable(streamTypeFeedDateStreamCount.get(streamType).get(feed).values(), longLabel);
//
//                    if (!inspect) {
//                        if (autoDeleteThreshold != null && streamTypeFeedDateStreamCount.get(streamType).get(feed)
//                                .keySet().size() < autoDeleteThreshold) {
//                            writeLine(longLabel + " Lower than threshold ... deleting");
//
//                            processStreamTypeFeed(fileName, streamType, feed, 'd');
//                        } else {
//                            final char response = readQuestion(longLabel + " (D)elete, (R)estore, (S)kip",
//                                    new char[]{'d', 'r', 's'}, 's');
//
//                            if (response == 'd' || response == 'r') {
//                                processStreamTypeFeed(fileName, streamType, feed, response);
//                            }
//                        }
//                    }
//                }
//            }

        } catch (final IOException | SQLException e) {
            e.printStackTrace();
            writeLine(e.getMessage());
            System.exit(1);
        }
    }

    public void setMock(final boolean mock) {
        this.mock = mock;
    }

    public void setInspect(final boolean inspect) {
        this.inspect = inspect;
    }

    public void setAutoDeleteThreshold(final Integer autoDeleteThreshold) {
        final Integer autoDeleteThreshold1 = autoDeleteThreshold;
    }

    public void setSortKey(final boolean sortKey) {
        this.sortKey = sortKey;
    }

    private void sort(final List<KeyCount> list) {
        Collections.sort(list, (o1, o2) -> {
            if (sortKey) {
                return o1.getKey().toString().compareTo(o2.getKey().toString());
            } else {
                return o1.getCount().compareTo(o2.getCount());
            }
        });
    }

    private Map<String, String> readAttributes(final String line, final String streamType, final String feedId) {
        final Map<String, String> rtnMap = new HashMap<>();

        final StringTokenizer stringTokenizer = new StringTokenizer(line, "/");
        final StringBuilder volumePath = new StringBuilder();
        while (stringTokenizer.hasMoreTokens()) {
            final String part = stringTokenizer.nextToken();
            if (part.equals("store")) {
                break;
            }
            volumePath.append("/");
            volumePath.append(part);
        }
        rtnMap.put(VOLUME_PATH, volumePath.toString());
        rtnMap.put(STREAM_TYPE_PATH, stringTokenizer.nextToken());
        rtnMap.put(
                DATE_PATH,
                stringTokenizer.nextToken() + "-" + stringTokenizer.nextToken() + "-" + stringTokenizer.nextToken());

        final String datePart = "YYYY-MM-DD";

        final String fileName = line.substring(line.lastIndexOf("/"));
        rtnMap.put(FILE_NAME, fileName);

        if (fileName.indexOf(".") > 0) {
            final int splitPos = fileName.indexOf("=");
            rtnMap.put(FEED_ID, fileName.substring(1, splitPos));
            rtnMap.put(STREAM_ID, fileName.substring(splitPos + 1, fileName.indexOf(".")));

            int dotCount = 0;
            int dotPos = 0;
            while ((dotPos = (fileName.indexOf('.', dotPos) + 1)) > 0) {
                dotCount++;
            }
            rtnMap.put(DEPTH, String.valueOf(dotCount - 2));
        }

        // Inspect File? (Expensive)
        if ((streamType == null || streamType.equals(rtnMap.get(STREAM_TYPE_PATH)))
            && (feedId == null || feedId.equals(rtnMap.get(FEED_ID)))) {
            final Path file = Paths.get(line);
            rtnMap.put(CREATE_TIME, rtnMap.get(DATE_PATH) + getTime(file, datePart));
        }

        return rtnMap;
    }

    private String getTime(final Path file, final String datePart) {
        String time = "T00:00:00.000Z";
        try {
            if (Files.exists(file)) {
                final String fileLastModified = DateUtil.createNormalDateTimeString(
                        Files.getLastModifiedTime(file)
                                .toMillis());
                time = fileLastModified.substring(datePart.length());
            }
        } catch (final IOException e) {
            // Ignore.
        }
        return time;
    }

    private Map<String, String> readManifestAttributes(final String rootFile) {
        final Map<String, String> rtnMap = new HashMap<>();
        final Path manifest = Paths.get(rootFile.substring(0, rootFile.lastIndexOf(".")) + ".mf.dat");
        if (Files.isRegularFile(manifest)) {
            final AttributeMap attributeMap = new AttributeMap();
            try (final InputStream inputStream = Files.newInputStream(manifest)) {
                AttributeMapUtil.read(inputStream, attributeMap);
            } catch (final IOException ioEx) {
                // TODO @AT Not sure if we should be swallowing this
            }
            rtnMap.putAll(attributeMap);
        }
        return rtnMap;
    }

    private void processStreamTypeFeed(final String fileName,
                                       final String processStreamType,
                                       final String processFeedId,
                                       final char action) throws IOException, SQLException {
        try (final BufferedReader reader = Files.newBufferedReader(Paths.get(fileName), StreamUtil.DEFAULT_CHARSET)) {
            String line = null;
            int lineCount = 0;
            int count = 0;

            long nextLog = System.currentTimeMillis() + 10000;

            while ((line = reader.readLine()) != null) {
                final Map<String, String> streamAttributes = readAttributes(line, processStreamType, processFeedId);

                lineCount++;
                if (System.currentTimeMillis() > nextLog) {
                    writeLine("Reading line " + lineCount + " " + line);
                    nextLog = System.currentTimeMillis() + 10000;
                }

                if (processStreamType.equals(streamAttributes.get(STREAM_TYPE_PATH))
                    && (processFeedId == null || processFeedId.equals(streamAttributes.get(FEED_ID)))) {
                    if (action == 'd') {
                        if (mock) {
                            writeLine("rm " + line);
                        } else {
                            writeLine("rm " + line);
                            final Path systemFile = Paths.get(line);
                            Files.deleteIfExists(systemFile);
                        }
                    }

                    // Restore and a root file
                    if (action == 'r' && "0".equals(streamAttributes.get(DEPTH))) {
                        streamAttributes.putAll(readManifestAttributes(line));

                        final long streamId = Long.parseLong(streamAttributes.get(STREAM_ID));
                        final long createMs = DateUtil.parseNormalDateTimeString(streamAttributes.get(CREATE_TIME));
                        long effectiveMs = createMs;
                        if (streamAttributes.containsKey(EFFECTIVE_TIME)) {
                            effectiveMs = DateUtil.parseNormalDateTimeString(streamAttributes.get(EFFECTIVE_TIME));
                        }

                        Long parentStreamId = null;
                        if (streamAttributes.containsKey(PARENT_STREAM_ID)) {
                            parentStreamId = Long.valueOf(streamAttributes.get(PARENT_STREAM_ID));
                        }
                        final long feedId = Long.valueOf(streamAttributes.get(FEED_ID));
                        final long streamTypeId = getPathStreamTypeMap().get(streamAttributes.get(STREAM_TYPE_PATH));

                        final String logInfo = Strings.padStart(
                                String.valueOf(streamId),
                                10,
                                ' ') + " " + DateUtil.createNormalDateTimeString(createMs);

                        writeLine("Restore " + logInfo + " for file " + line);

                        // TODO : @66 FIX THIS
//                        if (!mock) {
//                            try (final Connection connection = getConnection()) {
//                                try (final PreparedStatement statement1 = connection.prepareStatement(
//                                        "insert into strm (id,ver, crt_ms,effect_ms, parnt_strm_id,stat, " +
//                                        "fk_fd_id,fk_strm_proc_id, fk_strm_tp_id) "
//                                                + " values (?,1, ?,?, ?,?, ?,?, ?)")) {
//                                    int s1i = 1;
//                                    statement1.setLong(s1i++, streamId);
//                                    statement1.setLong(s1i++, createMs);
//                                    statement1.setLong(s1i++, effectiveMs);
//                                    if (parentStreamId != null) {
//                                        statement1.setLong(s1i++, parentStreamId);
//                                    } else {
//                                        statement1.setNull(s1i++, Types.BIGINT);
//                                    }
//                                    statement1.setByte(s1i++, StreamStatusId.UNLOCKED);
//                                    statement1.setLong(s1i++, feedId);
//                                    statement1.setNull(s1i++, Types.BIGINT);
//                                    statement1.setLong(s1i++, streamTypeId);
//                                    statement1.executeUpdate();
//                                }
//
//                                try (final PreparedStatement statement2 = connection.prepareStatement(
//                                        "insert into strm_vol (ver, fk_strm_id,fk_vol_id) " + " values (1, ?,?)")) {
//                                    int s2i = 1;
//                                    statement2.setLong(s2i++, streamId);
//                                    statement2.setLong(
//                                      s2i++,
//                                      getPathVolumeMap().get(streamAttributes.get(VOLUME_PATH)));
//                                    statement2.executeUpdate();
//                                }
//                            } catch (final RuntimeException e) {
//                                writeLine("Failed " + logInfo + " " + e.getMessage());
//                            }
//                        }
                        count++;
                    }
                }
            }
            writeLine("Processed " + ModelStringUtil.formatCsv(count) + " count");
        }
    }

    private ArrayList<KeyCount> writeTable(final Collection<KeyCount> values, final String heading) {
        writeLine("========================");
        writeLine(heading);
        writeLine("========================");
        final ArrayList<KeyCount> list = new ArrayList<>();
        list.addAll(values);
        sort(list);

        for (final KeyCount keyCount : list) {
            writeLine(Strings.padEnd(
                    keyCount.getKey().toString(),
                    KEY_PAD,
                    ' ') +
                      Strings.padStart(
                              ModelStringUtil.formatCsv(keyCount.getCount()),
                              COUNT_PAD,
                              ' '));
        }
        writeLine("========================");
        return list;

    }

    static class KeyCount {

        List<String> key;
        MutableInt count;

        KeyCount(final String key) {
            this.key = Arrays.asList(key);
            this.count = new MutableInt();
        }

        KeyCount(final List<String> key) {
            this.key = key;
            this.count = new MutableInt();
        }

        public List<String> getKey() {
            return key;
        }

        public MutableInt getCount() {
            return count;
        }

        @Override
        public String toString() {
            return Strings.padEnd(getKey().toString(), KEY_PAD, ' ') +
                   Strings.padStart(ModelStringUtil.formatCsv(getCount()), COUNT_PAD, ' ');
        }
    }

}
