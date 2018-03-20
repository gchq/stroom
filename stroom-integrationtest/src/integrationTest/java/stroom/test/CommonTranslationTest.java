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

import javax.inject.Inject;
import java.io.IOException;
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

    public List<StreamProcessorTaskExecutor> processAll() throws Exception {
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
        setup(FEED_NAME, Collections.singletonList(VALID_RESOURCE_NAME.toFile()));
    }

    public void setup(final File dataLocation) throws IOException {
        setup(FEED_NAME, Collections.singletonList(dataLocation));
    }

    public void setup(final List<File> dataLocations) throws IOException {
        setup(FEED_NAME, dataLocations);
    }

    public void setup(final String feedName, final File dataLocation) throws IOException {
        setup(feedName, Collections.singletonList(dataLocation));
    }

    public void setup(final String feedName, final List<File> dataLocations) throws IOException {
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
                LOGGER.info("Adding data from file {}", dataLocation.getAbsolutePath());
                storeCreationTool.addEventData(feedName, TextConverterType.DATA_SPLITTER, CSV_WITH_HEADING,
                        XSLT_NETWORK_MONITORING, dataLocation.toPath(), referenceFeeds);
            } catch (IOException e) {
                throw new RuntimeException(String.format("Error adding event data for file %s",
                        dataLocation.getAbsolutePath()), e);
            }
        });

        Assert.assertEquals(0, streamStore.getLockCount());
    }
}
