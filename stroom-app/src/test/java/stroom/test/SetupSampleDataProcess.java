/*
 * Copyright 2017-2024 Crown Copyright
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

import stroom.dashboard.impl.DashboardStore;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.impl.fs.FsVolumeService;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.feed.api.FeedProperties;
import stroom.feed.api.FeedStore;
import stroom.importexport.api.ImportExportSerializer;
import stroom.index.api.IndexVolumeGroupService;
import stroom.index.impl.IndexStore;
import stroom.index.impl.IndexVolumeService;
import stroom.index.shared.IndexVolume;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.receive.common.StreamTargetStreamHandlers;
import stroom.security.impl.AppPermissionDao;
import stroom.security.impl.UserDao;
import stroom.security.shared.AppPermission;
import stroom.security.shared.User;
import stroom.statistics.impl.hbase.entity.StroomStatsStoreStore;
import stroom.statistics.impl.sql.entity.StatisticStoreStore;
import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Script to create some base data for testing.
 */
public final class SetupSampleDataProcess {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SetupSampleDataProcess.class);

    public static final String ROOT_DIR_NAME = "samples";

    private static final String STATS_COUNT_FEED_LARGE_NAME = "COUNT_FEED_LARGE";
    private static final String STATS_COUNT_FEED_SMALL_NAME = "COUNT_FEED_SMALL";
    private static final String STATS_VALUE_FEED_LARGE_NAME = "VALUE_FEED_LARGE";
    private static final String STATS_VALUE_FEED_SMALL_NAME = "VALUE_FEED_SMALL";
    // 52,000 is just over 3 days at 5000ms intervals
    private static final int STATS_ITERATIONS_LARGE = 52_000;
    // 1,000 is just over 1hr at 5000ms intervals
    private static final int STATS_ITERATIONS_SMALL = 1_000;
    private static final String STATS_COUNT_API_FEED_NAME = "COUNT_V3";
    private static final String STATS_COUNT_API_DATA_FILE =
            "./stroom-app/src/test/resources/SetupSampleDataBean_COUNT_V3.xml";

    private static final int LOAD_CYCLES = 10;

    private final FeedStore feedStore;
    private final FeedProperties feedProperties;
    private final ImportExportSerializer importExportSerializer;
    private final ProcessorService processorService;
    private final ProcessorFilterService processorFilterService;
    private final PipelineStore pipelineStore;
    private final DashboardStore dashboardStore;
    private final IndexStore indexStore;
    private final IndexVolumeService indexVolumeService;
    private final IndexVolumeGroupService indexVolumeGroupService;
    private final FsVolumeService fsVolumeService;
    private final StatisticStoreStore statisticStoreStore;
    private final StroomStatsStoreStore stroomStatsStoreStore;
    private final SampleDataGenerator sampleDataGenerator;
    private final StreamTargetStreamHandlers streamTargetStreamHandlers;
    private final UserDao userDao;
    private final AppPermissionDao appPermissionDao;

    @Inject
    SetupSampleDataProcess(final FeedStore feedStore,
                           final FeedProperties feedProperties,
                           final ImportExportSerializer importExportSerializer,
                           final ProcessorService processorService,
                           final ProcessorFilterService processorFilterService,
                           final PipelineStore pipelineStore,
                           final DashboardStore dashboardStore,
                           final IndexStore indexStore,
                           final IndexVolumeService indexVolumeService,
                           final IndexVolumeGroupService indexVolumeGroupService,
                           final FsVolumeService fsVolumeService,
                           final StatisticStoreStore statisticStoreStore,
                           final StroomStatsStoreStore stroomStatsStoreStore,
                           final SampleDataGenerator sampleDataGenerator,
                           final StreamTargetStreamHandlers streamTargetStreamHandlers,
                           final UserDao userDao,
                           final AppPermissionDao appPermissionDao) {
        this.feedStore = feedStore;
        this.feedProperties = feedProperties;
        this.importExportSerializer = importExportSerializer;
        this.processorService = processorService;
        this.processorFilterService = processorFilterService;
        this.pipelineStore = pipelineStore;
        this.dashboardStore = dashboardStore;
        this.indexStore = indexStore;
        this.indexVolumeService = indexVolumeService;
        this.indexVolumeGroupService = indexVolumeGroupService;
        this.fsVolumeService = fsVolumeService;
        this.statisticStoreStore = statisticStoreStore;
        this.stroomStatsStoreStore = stroomStatsStoreStore;
        this.sampleDataGenerator = sampleDataGenerator;
        this.streamTargetStreamHandlers = streamTargetStreamHandlers;
        this.userDao = userDao;
        this.appPermissionDao = appPermissionDao;
    }

    public void run(final boolean shutdown) {
        // Create sample users [optional].
        createSampleUsers();

        checkVolumesExist();

        // Sample data/config can exist in many projects so here we define all
        // the root directories that we want to
        // process
        final Path coreServerSamplesDir = StroomCoreServerTestFileUtil.getTestResourcesDir()
                .resolve(ROOT_DIR_NAME);

        final Path[] rootDirs = new Path[]{
                coreServerSamplesDir};

        // Load various streams that we generate on the fly
        sampleDataGenerator.generateData(coreServerSamplesDir.resolve("generated").resolve("input"));

        // process each root dir in turn, importing content and loading data into feeds
        for (final Path dir : rootDirs) {
            loadDirectory(shutdown, dir);
        }

        // Add volumes to all indexes.
        final List<DocRef> indexList = indexStore.list();
        logDocRefs(indexList, "indexes");

        final List<DocRef> feeds = getSortedDocRefs(feedStore::list);

        logDocRefs(feeds, "feeds");

        generateSampleStatisticsData();

        // Enable all processing filters.
        processorFilterService.find(new ExpressionCriteria()).forEach(filter ->
                processorFilterService.update(filter.copy().enabled(true).build()));

//        // code to check that the statisticsDataSource objects are stored
//        // correctly
//        final List<DocRef> statisticsDataSources = getSortedDocRefs(statisticStoreStore::list);
//        logDocRefs(statisticsDataSources, "statisticStores");
//
//        final List<DocRef> stroomStatsStoreEntities = getSortedDocRefs(stroomStatsStoreStore::list);
//        logDocRefs(stroomStatsStoreEntities, "stroomStatsStores");

//        createProcessorFilters(feeds);

//        if (shutdown) {
//            commonTestControl.shutdown();
//        }
    }

    private List<DocRef> getSortedDocRefs(final Supplier<List<DocRef>> docRefsSupplier) {
        return docRefsSupplier.get()
                .stream()
                .sorted(Comparator.comparing(DocRef::getName))
                .collect(Collectors.toList());
    }

    private void createProcessorFilters(final List<DocRef> feeds) {
        // Create stream processors for all feeds.
        feeds.parallelStream()
                .forEach(feed -> {
                    // Find the pipeline for this feed.
                    final List<DocRef> pipelines = pipelineStore.list().stream()
                            .filter(docRef -> feed.getName()
                                    .equals(docRef.getName()))
                            .collect(Collectors.toList());

                    if (pipelines == null || pipelines.size() == 0) {
                        LOGGER.warn("No pipeline found for feed '" + feed.getName() + "'");
                    } else if (pipelines.size() > 1) {
                        LOGGER.warn("More than 1 pipeline found for feed '" + feed.getName() + "'");
                    } else {
                        final DocRef pipeline = pipelines.get(0);

                        // Create a processor for this feed.
                        final QueryData criteria = QueryData.builder()
                                .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                                .expression(ExpressionOperator.builder()
                                        .addTextTerm(MetaFields.FEED, ExpressionTerm.Condition.EQUALS, feed.getName())
                                        .addOperator(ExpressionOperator.builder().op(Op.OR)
                                                .addTextTerm(MetaFields.TYPE,
                                                        ExpressionTerm.Condition.EQUALS,
                                                        StreamTypeNames.RAW_EVENTS)
                                                .addTextTerm(MetaFields.TYPE,
                                                        ExpressionTerm.Condition.EQUALS,
                                                        StreamTypeNames.RAW_REFERENCE)
                                                .build())
                                        .build())
                                .build();
                        final Processor processor = processorService.create(ProcessorType.PIPELINE, pipeline, true);
                        LOGGER.info("Creating processor filter on {} for feed {}", pipeline.getName(), feed.getName());
                        final ProcessorFilter processorFilter = processorFilterService.create(
                                processor,
                                CreateProcessFilterRequest
                                        .builder()
                                        .pipeline(pipeline)
                                        .queryData(criteria)
                                        .export(true)
                                        .build());
                        LOGGER.debug(processorFilter.toString());
                    }
                });
    }

    private void checkVolumesExist() {
        final List<IndexVolume> indexVolumes = indexVolumeGroupService.getNames()
                .parallelStream()
                .flatMap(groupName -> indexVolumeService.find(new ExpressionCriteria()).stream())
                .collect(Collectors.toList());

        LOGGER.info("Checking available index volumes, found:\n{}",
                indexVolumes.stream()
                        .map(IndexVolume::getPath)
                        .collect(Collectors.joining("\n")));

        final List<FsVolume> dataVolumes = fsVolumeService.find(FindFsVolumeCriteria.matchAll()).getValues();
        LOGGER.info("Checking available data volumes, found:\n{}",
                dataVolumes.stream()
                        .map(FsVolume::getPath)
                        .collect(Collectors.joining("\n")));

        if (dataVolumes.isEmpty() || indexVolumes.isEmpty()) {
            LOGGER.error("Missing volumes, quiting");
            System.exit(1);
        }
    }

    private static void logDocRefs(final List<DocRef> entities, final String entityTypes) {
        LOGGER.info("Listing loaded {}:", entityTypes);
        entities.stream()
                .map(DocRef::getName)
                .sorted()
                .forEach(name -> LOGGER.info("  {}", name));
    }

    public void loadDirectory(final boolean shutdown, final Path importRootDir) {
        LOGGER.info("Loading sample data for directory: " + FileUtil.getCanonicalPath(importRootDir));

        final Path dataDir = importRootDir.resolve("input");
        final Path generatedDataDir = importRootDir.resolve("generated").resolve("input");

        final Path exampleDataDir = importRootDir.resolve("example_data");

        LOGGER.info("Checking data dir {}", dataDir.toAbsolutePath().normalize());
        if (Files.exists(dataDir)) {
            // Load data.
            final DataLoader dataLoader = new DataLoader(feedProperties, streamTargetStreamHandlers);

            // We spread the received time over 10 min intervals to help test
            // repo
            // layout start 2 weeks ago.
            final long dayMs = 1000 * 60 * 60 * 24;
            final long tenMinMs = 1000 * 60 * 10;

            // Load each data item 10 times to create a reasonable amount to
            // test.
            final String feedName = "DATA_SPLITTER-EVENTS";
            final CompletableFuture[] futures = new CompletableFuture[LOAD_CYCLES];
            for (int i = 0; i < LOAD_CYCLES; i++) {
                final int finalI = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    LOGGER.info("Loading data from {}, iteration {}",
                            dataDir.toAbsolutePath().normalize(), finalI);
                    long startTime = System.currentTimeMillis() - (14 * dayMs);

                    // Load reference data first.
                    // Force the effective data to be before the event time in the data that is 2010.....
                    dataLoader.read(dataDir, true,
                            DateUtil.parseNormalDateTimeString("2000-01-01T00:00:00.000Z"));

                    // Then load event data.
                    dataLoader.read(dataDir, false, startTime);
                    startTime += tenMinMs;

                    // Load some randomly generated data.
                    final String randomData = createRandomData();
                    dataLoader.loadInputStream(
                            feedName,
                            "Gen data",
                            null,
                            StreamUtil.stringToStream(randomData),
                            false,
                            startTime);
                    startTime += tenMinMs;
                });
            }
            CompletableFuture.allOf(futures).join();
        } else {
            LOGGER.info("Directory {} doesn't exist so skipping", dataDir.toAbsolutePath().normalize());
        }

        // Load the example data that we don't want to duplicate as is done above
        List.of(exampleDataDir, generatedDataDir)
                .forEach(dir -> {
                    if (Files.exists(dir)) {
                        LOGGER.info("Loading data from {}", dir.toAbsolutePath().normalize());
                        // Load data.
                        final DataLoader dataLoader = new DataLoader(feedProperties, streamTargetStreamHandlers);
                        final long startTime = System.currentTimeMillis();

                        // Then load event data.
                        dataLoader.read(dir, false, startTime);
                    } else {
                        LOGGER.info("Directory {} doesn't exist so skipping",
                                dir.toAbsolutePath().normalize());
                    }
                });

        // processorTaskManager.doCreateTasks();

        // // Add an index.
        // final Index index = addIndex();
        // addUserSearch(index);
        // addDictionarySearch(index);
    }

    private void loadStatsData(final DataLoader dataLoader,
                               final String feedName,
                               final int iterations,
                               final Instant startTime,
                               final BiFunction<Integer, Instant, String> dataGenerationFunction) {
        try {
            LOGGER.info("Generating statistics test data for feed {}", feedName);
            dataLoader.loadInputStream(
                    feedName,
                    "Auto generated statistics data",
                    null,
                    StreamUtil.stringToStream(dataGenerationFunction.apply(iterations, startTime)),
                    false,
                    startTime.toEpochMilli());
        } catch (final RuntimeException e) {
            LOGGER.error("Feed {} does not exist so cannot load the sample statistics data", feedName, e);
        }
    }

    /**
     * Generates some sample statistics data in two feeds. If the feed doesn't
     * exist it will fail silently
     */
    private void generateSampleStatisticsData() {
        final DataLoader dataLoader = new DataLoader(feedProperties, streamTargetStreamHandlers);
        final long startTime = System.currentTimeMillis();

        //keep the big and small feeds apart in terms of their event times
        final Instant startOfToday = Instant.now().truncatedTo(ChronoUnit.DAYS);
        final Instant startOfAWeekAgo = startOfToday.minus(7, ChronoUnit.DAYS);

        final List<CompletableFuture<Void>> futures = new ArrayList<>();

        futures.add(CompletableFuture.runAsync(() -> {
            loadStatsData(
                    dataLoader,
                    STATS_COUNT_FEED_LARGE_NAME,
                    STATS_ITERATIONS_LARGE,
                    startOfAWeekAgo,
                    GenerateSampleStatisticsData::generateCountData);
        }));

        futures.add(CompletableFuture.runAsync(() -> {
            loadStatsData(
                    dataLoader,
                    STATS_COUNT_FEED_SMALL_NAME,
                    STATS_ITERATIONS_SMALL,
                    startOfToday,
                    GenerateSampleStatisticsData::generateCountData);
        }));

        futures.add(CompletableFuture.runAsync(() -> {
            loadStatsData(
                    dataLoader,
                    STATS_VALUE_FEED_LARGE_NAME,
                    STATS_ITERATIONS_LARGE,
                    startOfAWeekAgo,
                    GenerateSampleStatisticsData::generateValueData);
        }));

        futures.add(CompletableFuture.runAsync(() -> {
            loadStatsData(
                    dataLoader,
                    STATS_VALUE_FEED_SMALL_NAME,
                    STATS_ITERATIONS_SMALL,
                    startOfToday,
                    GenerateSampleStatisticsData::generateValueData);
        }));

        futures.add(CompletableFuture.runAsync(() -> {
            try {
                final String sampleData = new String(Files.readAllBytes(Paths.get(STATS_COUNT_API_DATA_FILE)));
                dataLoader.loadInputStream(
                        STATS_COUNT_API_FEED_NAME,
                        "Sample statistics count data for export to API",
                        null,
                        StreamUtil.stringToStream(sampleData),
                        false,
                        startTime);
            } catch (final RuntimeException | IOException e) {
                LOGGER.warn("Feed {} does not exist so cannot load the sample count for export to API statistics data.",
                        STATS_COUNT_API_FEED_NAME);
            }
        }));

        LOGGER.info("Waiting for {} async tasks to complete", futures.size());
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        LOGGER.info("Completed generation");
    }

    private String createRandomData() {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy,HH:mm:ss", Locale.ENGLISH);
        final ZonedDateTime refDateTime = ZonedDateTime.of(
                2010, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        final StringBuilder sb = new StringBuilder();
        sb.append("Date,Time,FileNo,LineNo,User,Message\n");

        for (int i = 0; i < 1000; i++) {
            final ZonedDateTime dateTime = refDateTime.plusSeconds((long) (Math.random() * 10000));
            sb.append(formatter.format(dateTime));
            sb.append(",");
            sb.append(createNum(4));
            sb.append(",");
            sb.append(createNum(10));
            sb.append(",user");
            sb.append(createNum(10));
            sb.append(",Some message ");
            sb.append(createNum(10));
            sb.append("\n");
        }
        return sb.toString();
    }

    private String createNum(final int max) {
        return String.valueOf(ThreadLocalRandom.current().nextInt(max) + 1);
    }

    // private Folder get(String name) {
    // Folder parentGroup = null;
    // Folder folder = null;
    //
    // String[] parts = name.split("/");
    // for (String part : parts) {
    // parentGroup = folder;
    // folder = folderService.loadByName(parentGroup, part);
    // }
    // return folder;
    // }
    //
    // private Index addIndex() {
    // try {
    // final Folder folder = get("Indexes/Example index");
    // final Pipeline indexTranslation = findTranslation("Example index");
    // return setupIndex(folder, "Example index", indexTranslation);
    //
    // } catch (final IOException e) {
    // throw new RuntimeException(e.getMessage(), e);
    // }
    // }
    //
    // private Pipeline findTranslation(final String name) {
    // final FindPipelineCriteria findTranslationCriteria = new
    // FindPipelineCriteria();
    // findTranslationCriteria.setName(name);
    // final BaseResultList<Pipeline> list = pipelineStore
    // .find(findTranslationCriteria);
    // if (list != null && list.size() > 0) {
    // return list.getFirst();
    // }
    //
    // throw new RuntimeException("No translation found with name: " + name);
    // }
    //
    // private XSLT findXSLT(final String name) {
    // final FindXSLTCriteria findXSLTCriteria = new FindXSLTCriteria();
    // findXSLTCriteria.setName(name);
    // final BaseResultList<XSLT> list = xsltStore.find(findXSLTCriteria);
    // if (list != null && list.size() > 0) {
    // return list.getFirst();
    // }
    //
    // throw new RuntimeException("No translation found with name: " + name);
    // }
    //
    // private Index setupIndex(final Folder folder,
    // final String indexName, final Pipeline indexTranslation)
    // throws IOException {
    // Index index = new Index();
    // index.setFolder(folder);
    // index.setName(indexName);
    //
    // index = indexStore.save(index);
    //
    // return index;
    // }
    //
    // private void addUserSearch(final Index index) {
    // final Folder folder = get(SEARCH + "/Search Examples");
    // final XSLT resultXSLT = findXSLT("Search Result Table - Show XML");
    //
    // final SearchExpressionTerm content1 = new SearchExpressionTerm();
    // content1.setField("UserId");
    // content1.setValue("userone");
    // final SearchExpressionOperator andOperator = new
    // SearchExpressionOperator(
    // true);
    // andOperator.addChild(content1);
    //
    // // FIXME : Set result pipeline.
    // final Search expression = new Search(index, null, andOperator);
    // expression.setName("User search");
    // expression.setFolder(folder);
    // searchExpressionService.save(expression);
    //
    // final DictionaryDocument dictionary = new Dictionary();
    // dictionary.setName("User list");
    // dictionary.setWords("userone\nuser1");
    // }
    //
    // private void addDictionarySearch(final Index index) {
    // final Folder folder = get(SEARCH + "/Search Examples");
    // final XSLT resultXSLT = findXSLT("Search Result Table - Show XML");
    //
    // final DictionaryDocument dictionary = new Dictionary();
    // dictionary.setName("User list");
    // dictionary.setWords("userone\nuser1");
    // dictionary.setFolder(folder);
    //
    // dictionaryStore.save(dictionary);
    //
    // final SearchExpressionTerm content1 = new SearchExpressionTerm();
    // content1.setField("UserId");
    // content1.setOperator(Operator.IN_DICTIONARY);
    // content1.setValue("User list");
    // final SearchExpressionOperator andOperator = new
    // SearchExpressionOperator(
    // true);
    // andOperator.addChild(content1);
    //
    // // FIXME : Set result pipeline.
    // final Search expression = new Search(index, null, andOperator);
    // expression.setName("Dictionary search");
    // expression.setFolder(folder);
    //
    // searchExpressionService.save(expression);
    // }


    private void createSampleUsers() {
        for (final AppPermission permission : AppPermission.values()) {
            // Create a user that has explicit permission.
            final User user = createUser("user__" + permission.name().toLowerCase(Locale.ROOT));
            appPermissionDao.addPermission(user.getUuid(), permission);

            // Create a group that has explicit permission.
            final User group1 = createGroup("group1__" + permission.name().toLowerCase(Locale.ROOT));
            appPermissionDao.addPermission(group1.getUuid(), permission);

            // Create a user that is a member of the group so inherits permission.
            final User group1user = createUser("group1user__" + permission.name().toLowerCase(Locale.ROOT));
            userDao.addUserToGroup(group1user.getUuid(), group1.getUuid());

            // Create a group that is a member of the group so inherits permission.
            final User group2 = createGroup("group2__" + permission.name().toLowerCase(Locale.ROOT));
            userDao.addUserToGroup(group2.getUuid(), group1.getUuid());

            // Create a user that is a member of group2 so inherits permission.
            final User group2user = createUser("group2user__" + permission.name().toLowerCase(Locale.ROOT));
            userDao.addUserToGroup(group2user.getUuid(), group2.getUuid());
        }
    }

    private User createGroup(final String name) {
        final Optional<User> optionalGroup = userDao.getGroupByName(name);
        return optionalGroup.orElseGet(() -> {
            final User user = User.builder()
                    .subjectId(name)
                    .displayName(name)
                    .uuid(UUID.randomUUID().toString())
                    .group(true)
                    .build();
            user.setCreateUser("admin");
            user.setUpdateUser("admin");
            user.setCreateTimeMs(System.currentTimeMillis());
            user.setUpdateTimeMs(System.currentTimeMillis());
            return userDao.create(user);
        });
    }

    private User createUser(final String name) {
        final Optional<User> optional = userDao.getUserBySubjectId(name);
        return optional.orElseGet(() -> {
            final User user = User.builder()
                    .subjectId(name)
                    .displayName(name)
                    .uuid(UUID.randomUUID().toString())
                    .group(false)
                    .build();
            user.setCreateUser("admin");
            user.setUpdateUser("admin");
            user.setCreateTimeMs(System.currentTimeMillis());
            user.setUpdateTimeMs(System.currentTimeMillis());
            return userDao.create(user);
        });
    }
}
