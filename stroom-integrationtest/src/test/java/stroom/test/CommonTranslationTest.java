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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.feed.shared.Feed;
import stroom.node.server.NodeCache;
import stroom.pipeline.shared.TextConverter.TextConverterType;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.tools.StoreCreationTool;
import stroom.streamtask.server.StreamProcessorTask;
import stroom.streamtask.server.StreamProcessorTaskExecutor;
import stroom.streamtask.server.StreamTaskCreator;
import stroom.streamtask.shared.StreamTask;
import stroom.task.server.TaskManager;
import stroom.task.server.TaskMonitorImpl;
import stroom.util.spring.StroomSpringProfiles;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Profile(StroomSpringProfiles.IT)
public class CommonTranslationTest {
    private static final String DIR = "CommonTranslationTest/";

    public static final String FEED_NAME = "TEST_FEED";
    public static final File VALID_RESOURCE_NAME = StroomProcessTestFileUtil
            .getTestResourcesFile(DIR + "NetworkMonitoringSample.in");
    public static final File INVALID_RESOURCE_NAME = StroomProcessTestFileUtil.getTestResourcesFile(DIR + "Invalid.in");

    private static final File CSV = StroomProcessTestFileUtil.getTestResourcesFile(DIR + "CSV.ds");
    private static final File CSV_WITH_HEADING = StroomProcessTestFileUtil.getTestResourcesFile(DIR + "CSVWithHeading.ds");
    private static final File XSLT_HOST_NAME_TO_LOCATION = StroomProcessTestFileUtil
            .getTestResourcesFile(DIR + "SampleRefData-HostNameToLocation.xsl");
    private static final File XSLT_HOST_NAME_TO_IP = StroomProcessTestFileUtil
            .getTestResourcesFile(DIR + "SampleRefData-HostNameToIP.xsl");
    private static final File XSLT_NETWORK_MONITORING = StroomProcessTestFileUtil
            .getTestResourcesFile(DIR + "NetworkMonitoring.xsl");
    private static final File REFDATA_HOST_NAME_TO_LOCATION = StroomProcessTestFileUtil
            .getTestResourcesFile(DIR + "SampleRefData-HostNameToLocation.in");
    private static final File REFDATA_HOST_NAME_TO_IP = StroomProcessTestFileUtil
            .getTestResourcesFile(DIR + "SampleRefData-HostNameToIP.in");

    private static final String REFFEED_HOSTNAME_TO_LOCATION = "HOSTNAME_TO_LOCATION";
    private static final String REFFEED_HOSTNAME_TO_IP = "HOSTNAME_TO_IP";

    private static final String ID_TO_USER = "ID_TO_USER";
    private static final File EMPLOYEE_REFERENCE_XSL = StroomProcessTestFileUtil
            .getTestResourcesFile(DIR + "EmployeeReference.xsl");
    private static final File EMPLOYEE_REFERENCE_CSV = StroomProcessTestFileUtil
            .getTestResourcesFile(DIR + "EmployeeReference.in");

    @Resource
    private NodeCache nodeCache;
    @Resource
    private StreamTaskCreator streamTaskCreator;
    @Resource
    private StoreCreationTool storeCreationTool;
    @Resource
    private TaskManager taskManager;
    @Resource
    private StreamStore streamStore;

    public List<StreamProcessorTaskExecutor> processAll() throws Exception {
        // Force creation of stream tasks.
        if (streamTaskCreator instanceof StreamTaskCreator) {
            streamTaskCreator.createTasks(new TaskMonitorImpl());
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
        setup(FEED_NAME, VALID_RESOURCE_NAME);
    }

    public void setup(final String feedName, final File dataLocation) throws IOException {
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

        storeCreationTool.addEventData(feedName, TextConverterType.DATA_SPLITTER, CSV_WITH_HEADING,
                XSLT_NETWORK_MONITORING, dataLocation, referenceFeeds);

        Assert.assertEquals(0, streamStore.getLockCount());
    }
}
