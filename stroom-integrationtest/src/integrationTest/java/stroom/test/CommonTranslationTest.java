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

package stroom.test;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.shared.Feed;
import stroom.node.NodeCache;
import stroom.pipeline.shared.TextConverter.TextConverterType;
import stroom.streamstore.StreamStore;
import stroom.streamstore.tools.StoreCreationTool;
import stroom.streamtask.StreamProcessorTask;
import stroom.streamtask.StreamProcessorTaskExecutor;
import stroom.streamtask.StreamTaskCreator;
import stroom.streamtask.shared.StreamTask;
import stroom.task.SimpleTaskContext;
import stroom.task.TaskManager;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CommonTranslationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonTranslationTest.class);
    public static final String FEED_NAME = "TEST_FEED";
    private static final String DIR = "CommonTranslationTest/";
    public static final Path VALID_RESOURCE_NAME = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "NetworkMonitoringSample.in");
    public static final Path INVALID_RESOURCE_NAME = StroomPipelineTestFileUtil.getTestResourcesFile(DIR + "Invalid.in");

    private static final Path CSV = StroomPipelineTestFileUtil.getTestResourcesFile(DIR + "CSV.ds");
    private static final Path CSV_WITH_HEADING = StroomPipelineTestFileUtil.getTestResourcesFile(DIR + "CSVWithHeading.ds");
    private static final Path XSLT_HOST_NAME_TO_LOCATION = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "SampleRefData-HostNameToLocation.xsl");
    private static final Path XSLT_HOST_NAME_TO_IP = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "SampleRefData-HostNameToIP.xsl");
    private static final Path XSLT_NETWORK_MONITORING = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "NetworkMonitoring.xsl");
    private static final Path REFDATA_HOST_NAME_TO_LOCATION = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "SampleRefData-HostNameToLocation.in");
    private static final Path REFDATA_HOST_NAME_TO_IP = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "SampleRefData-HostNameToIP.in");

    private static final String REFFEED_HOSTNAME_TO_LOCATION = "HOSTNAME_TO_LOCATION";
    private static final String REFFEED_HOSTNAME_TO_IP = "HOSTNAME_TO_IP";

    private static final String ID_TO_USER = "ID_TO_USER";
    private static final Path EMPLOYEE_REFERENCE_XSL = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "EmployeeReference.xsl");
    private static final Path EMPLOYEE_REFERENCE_CSV = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "EmployeeReference.in");

    private final NodeCache nodeCache;
    private final StreamTaskCreator streamTaskCreator;
    private final StoreCreationTool storeCreationTool;
    private final TaskManager taskManager;
    private final StreamStore streamStore;

    @Inject
    CommonTranslationTest(final NodeCache nodeCache,
                          final StreamTaskCreator streamTaskCreator,
                          final StoreCreationTool storeCreationTool,
                          final TaskManager taskManager,
                          final StreamStore streamStore) {
        this.nodeCache = nodeCache;
        this.streamTaskCreator = streamTaskCreator;
        this.storeCreationTool = storeCreationTool;
        this.taskManager = taskManager;
        this.streamStore = streamStore;
    }

    public List<StreamProcessorTaskExecutor> processAll() {
        // Force creation of stream tasks.
        if (streamTaskCreator instanceof StreamTaskCreator) {
            streamTaskCreator.createTasks(new SimpleTaskContext());
        }

        final List<StreamProcessorTaskExecutor> results = new ArrayList<>();
        List<StreamTask> streamTasks = streamTaskCreator.assignStreamTasks(nodeCache.getDefaultNode(), 100);
        while (streamTasks.size() > 0) {
            for (final StreamTask streamTask : streamTasks) {
                final StreamProcessorTask task = new StreamProcessorTask(streamTask);
                taskManager.exec(task);
                results.add(task.getStreamProcessorTaskExecutor());
            }
            streamTasks = streamTaskCreator.assignStreamTasks(nodeCache.getDefaultNode(), 100);
        }

        return results;
    }

    public void setup() throws IOException {
        LOGGER.info("Writing test data");
        final Writer writer = Files.newBufferedWriter(VALID_RESOURCE_NAME);
        writer.write("Date,Time,EventType,Device,UserName,ID,ErrorCode,IPAddress,Server,Message\n");
        for (int i = 0; i < 100000; i++) {
            writer.write("18/08/2007,09:49:51,authenticationFailed,device0,user1,192.168.0.2,E0123,192.168.0.3,server1,Invalid password\n");
            writer.write("18/09/2007,13:18:56,authorisationFailed,device1,user2,192.168.0.2,E0567,192.168.0.3,server1,A message that I made up 1\n");
            writer.write("18/10/2007,13:19:23,authorisationFailed,device2,user3,192.168.0.2,E0567,192.168.0.3,server2,A message that I made up 2\n");
            writer.write("18/11/2007,13:20:23,authorisationFailed,device3,user4,192.168.0.2,E0567,192.168.0.3,server3,Another message that I made up 1\n");
            writer.write("18/12/2007,13:21:48,authorisationFailed,device4,user5,192.168.0.2,E0567,192.168.0.3,server4,Another message that I made up 2\n");
            writer.write("18/08/2007,13:22:27,authorisationFailed,device5,user2,192.168.0.2,E0567,192.168.0.3,server5,msg=foo bar\n");
            writer.write("18/08/2007,13:23:49,authorisationFailed,device6,user3,192.168.0.2,E0567,192.168.0.3,server6,msg=foo bar\n");
            writer.write("18/08/2007,13:43:16,authorisationFailed,device7,user4,192.168.0.2,E0567,192.168.0.3,server7,msg=foo bar\n");
            writer.write("18/08/2007,13:44:23,authorisationFailed,device8,user5,192.168.0.2,E0567,192.168.0.3,server8,msg=foo bar\n");
            writer.write("18/08/2007,13:46:21,authorisationFailed,device9,user6,192.168.0.2,E0567,192.168.0.3,server9,msg=foo bar\n");
            writer.write("18/08/2007,13:47:21,authorisationFailed,device1,user2,192.168.0.2,E0567,192.168.0.3,server1,msg=foo bar\n");
            writer.write("18/08/2007,13:49:12,authorisationFailed,device2,user3,192.168.0.2,E0567,192.168.0.3,server2,msg=foo bar\n");
            writer.write("18/08/2007,13:50:56,authorisationFailed,device3,user4,192.168.0.2,E0567,192.168.0.3,server3,msg=foo bar\n");
            writer.write("18/01/2007,13:56:42,authorisationFailed,device4,user5,192.168.0.2,E0567,192.168.0.3,server4,msg=foo bar\n");
            writer.write("18/08/2007,14:11:33,authorisationFailed,device5,user1,192.168.0.2,E0567,192.168.0.3,server6,msg=foo bar\n");
            writer.write("18/08/2007,14:13:11,authorisationFailed,device6,user2,192.168.0.2,E0567,192.168.0.3,server6,msg=foo bar\n");
            writer.write("18/08/2007,14:14:24,authorisationFailed,device7,user3,192.168.0.2,E0567,192.168.0.3,server7,msg=foo bar\n");
            writer.write("18/08/2007,14:16:56,authorisationFailed,device8,user2,192.168.0.2,E0567,192.168.0.3,server8,msg=foo bar\n");
            writer.write("18/08/2007,14:19:59,authorisationFailed,device9,user4,192.168.0.2,E0567,192.168.0.3,server9,msg=foo bar\n");
            writer.write("18/02/2007,14:23:43,authorisationFailed,device1,user5,192.168.0.2,E0567,192.168.0.3,server1,msg=foo bar\n");
            writer.write("18/08/2007,14:25:16,authorisationFailed,device2,user2,192.168.0.2,E0567,192.168.0.3,server2,msg=foo bar\n");
            writer.write("18/08/2007,14:28:15,authorisationFailed,device3,user4,192.168.0.2,E0567,192.168.0.3,server3,msg=foo bar\n");
            writer.write("18/03/2007,14:34:41,authorisationFailed,device4,user5,192.168.0.2,E0567,192.168.0.3,server4,msg=foo bar\n");
            writer.write("18/08/2007,14:37:23,authorisationFailed,device5,user6,192.168.0.2,E0567,192.168.0.3,server5,msg=foo bar\n");
            writer.write("18/08/2007,14:39:55,authorisationFailed,device6,user1,192.168.0.2,E0567,192.168.0.3,server6,some message\n");
            writer.write("18/08/2007,14:45:52,authorisationFailed,device6,user1,192.168.0.2,E0567,192.168.0.3,server7,some other message 2\n");

            if (i % 1000 == 0) {
                LOGGER.info("Done: " + i);
            }
        }
        writer.close();
        LOGGER.info("Done writing test data");

        setup(FEED_NAME, VALID_RESOURCE_NAME);
    }

    public void setup(final Path dataLocation) throws IOException {
        setup(FEED_NAME, Collections.singletonList(dataLocation));
    }

    public void setup(final List<Path> dataLocations) throws IOException {
        setup(FEED_NAME, dataLocations);
    }

    public void setup(final String feedName, final Path dataLocation) throws IOException {
        setup(feedName, Collections.singletonList(dataLocation));
    }

    public void setup(final String feedName, final List<Path> dataLocations) throws IOException {
        // commonTestControl.setup();

        // Setup the feed definitions.
        final Feed hostNameToIP = storeCreationTool.addReferenceData(REFFEED_HOSTNAME_TO_IP,
                TextConverterType.DATA_SPLITTER, CSV_WITH_HEADING, XSLT_HOST_NAME_TO_IP, REFDATA_HOST_NAME_TO_IP);
        final Feed hostNameToLocation = storeCreationTool.addReferenceData(REFFEED_HOSTNAME_TO_LOCATION,
                TextConverterType.DATA_SPLITTER, CSV_WITH_HEADING, XSLT_HOST_NAME_TO_LOCATION,
                REFDATA_HOST_NAME_TO_LOCATION);
        final Feed idToUser = storeCreationTool.addReferenceData(ID_TO_USER, TextConverterType.DATA_SPLITTER,
                CSV_WITH_HEADING, EMPLOYEE_REFERENCE_XSL, EMPLOYEE_REFERENCE_CSV);

        final Set<Feed> referenceFeeds = new HashSet<>();
        referenceFeeds.add(hostNameToIP);
        referenceFeeds.add(hostNameToLocation);
        referenceFeeds.add(idToUser);

        dataLocations.forEach(dataLocation -> {
            try {
                LOGGER.info("Adding data from file {}", FileUtil.getCanonicalPath(dataLocation));
                storeCreationTool.addEventData(feedName, TextConverterType.DATA_SPLITTER, CSV_WITH_HEADING,
                        XSLT_NETWORK_MONITORING, dataLocation, referenceFeeds);
            } catch (IOException e) {
                throw new RuntimeException(String.format("Error adding event data for file %s",
                        FileUtil.getCanonicalPath(dataLocation)), e);
            }
        });

        Assert.assertEquals(0, streamStore.getLockCount());
    }
}
