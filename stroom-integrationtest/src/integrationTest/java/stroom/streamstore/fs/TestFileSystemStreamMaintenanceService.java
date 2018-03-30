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

package stroom.streamstore.fs;

import org.junit.Assert;
import org.junit.Test;
import stroom.feed.shared.Feed;
import stroom.jobsystem.MockTask;
import stroom.streamstore.FindStreamVolumeCriteria;
import stroom.streamstore.StreamMaintenanceService;
import stroom.streamstore.StreamRange;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TestFileSystemStreamMaintenanceService extends AbstractCoreIntegrationTest {
    @Inject
    private FileSystemStreamMaintenanceService streamMaintenanceService;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private FileSystemCleanExecutor fileSystemCleanTaskExecutor;

    @Test
    public void testSimple() throws IOException {
        // commonTestControl.deleteDir();

        final Feed eventFeed = commonTestScenarioCreator.createSimpleFeed();

        final Stream md = commonTestScenarioCreator.createSample2LineRawFile(eventFeed, StreamType.RAW_EVENTS);

        commonTestScenarioCreator.createSampleBlankProcessedFile(eventFeed, md);

        final List<Path> files = streamMaintenanceService.findAllStreamFile(md);

        Assert.assertTrue(files.size() > 0);

        final String path = FileUtil.getCanonicalPath(files.get(0).getParent());
        final String volPath = path.substring(path.indexOf("RAW_EVENTS"));

        final StreamRange streamRange = new StreamRange(volPath);
        final FindStreamVolumeCriteria findStreamVolumeCriteria = new FindStreamVolumeCriteria();
        findStreamVolumeCriteria.setStreamRange(streamRange);
        Assert.assertTrue(streamMaintenanceService.find(findStreamVolumeCriteria).size() > 0);

        final Path dir = files.iterator().next().getParent();

        final Path test1 = dir.resolve("badfile.dat");

        Files.createFile(test1);

        fileSystemCleanTaskExecutor.exec(new MockTask("Test"));
    }

    // @Test
    // public void testChartQuery() throws IOException {
    //
    // FindStreamChartCriteria findStreamChartCriteria = new
    // FindStreamChartCriteria();
    // findStreamChartCriteria.setPeriod(new Period(0L, Long.MAX_VALUE));
    //
    // ResultList<SharedString> chartList = streamMaintenanceService
    // .getChartList(findStreamChartCriteria);
    // ChartCriteria chartCriteria = new ChartCriteria();
    // for (SharedString chartType : chartList) {
    // chartCriteria.add(chartType.toString(), ChartFunc.MAX);
    // chartCriteria.add(chartType.toString(), ChartFunc.MIN);
    // chartCriteria.add(chartType.toString(), ChartFunc.AVG);
    // }
    // findStreamChartCriteria.setChartCriteria(chartCriteria);
    // streamMaintenanceService.getChartData(findStreamChartCriteria);
    // }
}
