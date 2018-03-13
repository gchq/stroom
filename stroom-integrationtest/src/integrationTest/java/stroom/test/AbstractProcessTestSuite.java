package stroom.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        stroom.index.TestIndexingFilter.class,
        stroom.index.TestIndexingPipeline.class,
        stroom.pipeline.TestFileAppender.class,
        stroom.pipeline.TestRecordOutputFilter.class,
        stroom.pipeline.TestRollingFileAppender.class,
        stroom.pipeline.TestRollingStreamAppender.class,
        stroom.pipeline.TestStreamAppender.class,
        stroom.pipeline.TestXMLHttpBlankTokenFix.class,
        stroom.pipeline.TestXMLTransformer.class,
        stroom.pipeline.TestXMLWithErrorsInTransform.class,
        stroom.pipeline.factory.TestPipelineFactory.class,
        stroom.pipeline.task.TestTranslationTask.class,
        stroom.pipeline.task.TestTranslationTaskContextAndFlattening.class,
        stroom.pipeline.task.TestTranslationTaskFactory.class,
        stroom.pipeline.task.TestTranslationTaskWithoutTranslation.class,
        stroom.streamtask.TestStreamTargetStroomStreamHandler.class,
        stroom.xml.converter.datasplitter.TestDataSplitter.class,
        stroom.xml.converter.datasplitter.TestDataSplitter2.class,
        stroom.xml.converter.xmlfragment.TestXMLFragmentWrapper.class
})
public class AbstractProcessTestSuite {
}
