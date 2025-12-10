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

package stroom.test;

import stroom.util.AbstractCommandLineTool;

/**
 * Script to create some base data for testing.
 */
public final class SetupCorruptSampleData extends AbstractCommandLineTool {
//    private ApplicationContext appContext;
//

    /**
     * Main.
     *
     * @param args NA
     */
    public static void main(final String[] args) {
//        final SetupCorruptSampleData setupSampleData = new SetupCorruptSampleData();
//        setupSampleData.doMain(args);
    }

    //
//    public ApplicationContext getAppContext() {
//        return appContext;
//    }
//
    @Override
    public void run() {
//        FileUtil.deleteContents(FileUtil.getTempDir());
//
//        // Force nodes to be created
//        appContext.getInstance(NodeInfo.class).get();
//
//        final CommonTestControl commonTestControl = appContext.getInstance(CommonTestControl.class);
//        final CommonTestScenarioCreator commonTestScenarioCreator =
//        appContext.getInstance(CommonTestScenarioCreator.class);
//        final StreamStore streamStore = appContext.getInstance(StreamStore.class);
//
//        final Feed feed = commonTestScenarioCreator.createSimpleFeed();
//
//        final Stream sourceStream = commonTestScenarioCreator.createSample2LineRawFile(feed, StreamType.RAW_EVENTS);
//
//        final StreamTarget target = streamStore.openTarget(
//                Stream.createProcessedStream(sourceStream, sourceStream.getFeed(), StreamType.EVENTS, null, null));
//
//        try {
//            target.getOutputStream().write(" ".getBytes(StreamUtil.DEFAULT_CHARSET));
//        } catch (final RuntimeException e) {
//            throw new RuntimeException(ex);
//        }
//        streamStore.closeStreamTarget(target);
//
//        commonTestControl.shutdown();
    }
}
