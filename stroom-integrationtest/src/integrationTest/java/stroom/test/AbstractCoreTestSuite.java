package stroom.test;

import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import stroom.dashboard.TestDashboardStoreImpl;
import stroom.data.store.TestFileSystemZipProcessor;
import stroom.data.store.impl.fs.TestFileSystemStreamMaintenanceService;
import stroom.data.store.impl.fs.TestFileSystemStreamStore;
import stroom.xmlschema.TestXMLSchemaStoreImpl;

@Ignore("Don't run this test suite automatically as the tests are already run on their own")
@RunWith(Suite.class)
@Suite.SuiteClasses({
        TestDashboardStoreImpl.class,
        stroom.docstore.db.TestDBPersistence.class,
        stroom.explorer.TestExplorerTree.class,
        stroom.importexport.TestImportExportDashboards.class,
        stroom.importexport.TestImportExportSerializer.class,
        stroom.importexport.TestImportExportServiceImpl.class,
        stroom.importexport.TestImportExportServiceImpl2.class,
        stroom.importexport.TestImportExportServiceImpl3.class,
        stroom.index.TestIndexStoreImpl.class,
        stroom.index.TestIndexShardServiceImpl.class,
        stroom.index.TestIndexShardWriterImpl.class,
        stroom.jobsystem.TestClusterLockService.class,
        stroom.jobsystem.TestJobNodeService.class,
        stroom.node.TestDefaultNodeFactory.class,
        stroom.node.TestDefaultNodeService.class,
        stroom.node.TestVolumeService.class,
        stroom.pipeline.task.TestFullTranslationTask.class,
        stroom.pipeline.task.TestFullTranslationTaskAndStepping.class,
        stroom.pipeline.task.TestTranslationStepping.class,
        stroom.policy.TestDataRetentionStreamFinder.class,
        stroom.refdata.TestReferenceDataWithCache.class,
        stroom.script.TestScriptStoreImpl.class,
        stroom.search.TestBasicSearch.class,
        stroom.search.TestBasicSearch_EndToEnd.class,
        stroom.search.TestDictionaryStoreImpl.class,
        stroom.search.TestEventSearch.class,
        stroom.search.TestInteractiveSearch.class,
        stroom.search.TestQueryServiceImpl.class,
        stroom.search.TestTagCloudSearch.class,
        stroom.security.TestAppPermissionServiceImpl.class,
        stroom.security.TestDocumentPermissionsServiceImpl.class,
        stroom.security.TestUserServiceImpl.class,
        stroom.statistics.common.TestStatisticsDataSourceImportExportSerializer.class,
        stroom.statistics.sql.TestSQLStatisticAggregationManager.class,
        stroom.statistics.sql.TestSQLStatisticFlushTaskHandler.class,
        stroom.stats.TestStroomStatsStoreImportExportSerializer.class,
        TestFileSystemZipProcessor.class,
        TestFileSystemStreamMaintenanceService.class,
        TestFileSystemStreamStore.class,
        stroom.data.store.upload.TestStreamUploadDownloadTaskHandler.class,
        stroom.streamtask.TestDataRetentionExecutor.class,
        stroom.streamtask.TestDataRetentionTransactionHelper.class,
        stroom.streamtask.TestFileSystemCleanTask.class,
        stroom.streamtask.TestProxyAggregationTask.class,
        stroom.streamtask.TestStreamArchiveTask.class,
        stroom.streamtask.TestStreamProcessorFilterService.class,
        stroom.streamtask.TestStreamRetentionExecutor.class,
        stroom.streamtask.TestStreamTaskCreator.class,
        stroom.streamtask.TestStreamTaskCreatorTransactionHelper.class,
        stroom.streamtask.TestStreamTaskService.class,
        stroom.streamtask.TestStreamTaskServiceBatchLocking.class,
        stroom.streamtask.TestTranslationStreamTaskServiceImportExport.class,
        stroom.task.TestTaskManagerImpl.class,
        stroom.test.TestAbstractCoreIntegrationTest.class,
        TestXMLSchemaStoreImpl.class
})
public class AbstractCoreTestSuite {
}
