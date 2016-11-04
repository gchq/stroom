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

package stroom.headless;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import stroom.util.zip.StroomZipFile;
import stroom.util.zip.StroomZipFileType;
import stroom.util.zip.StroomZipNameSet;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import stroom.entity.server.util.XMLUtil;
import stroom.importexport.server.ImportExportService;
import stroom.node.server.NodeCache;
import stroom.node.shared.Volume;
import stroom.node.shared.Volume.VolumeType;
import stroom.node.shared.VolumeService;
import stroom.pipeline.server.filter.SafeXMLFilter;
import stroom.pipeline.spring.PipelineConfiguration;
import stroom.spring.CachedServiceConfiguration;
import stroom.spring.PersistenceConfiguration;
import stroom.spring.ScopeConfiguration;
import stroom.spring.ServerComponentScanConfiguration;
import stroom.spring.ServerConfiguration;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.task.server.GenericServerTask;
import stroom.task.server.TaskManager;
import stroom.util.AbstractCommandLineTool;
import stroom.util.config.StroomProperties;
import stroom.util.io.FileUtil;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomSpringProfiles;
import stroom.util.task.TaskScopeRunnable;
import stroom.util.thread.ThreadScopeRunnable;
import stroom.util.zip.StroomZipRepository;

/**
 * Command line tool to process some files from a proxy stroom.
 */
public class Headless extends AbstractCommandLineTool {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(Headless.class);

    private ApplicationContext appContext = null;

    private String input;
    private String output;
    private String config;
    private String tmp;

    private File inputDir;
    private File outputFile;
    private File configFile;
    private File tmpDir;

    public void setInput(final String input) {
        this.input = input;
    }

    public void setOutput(final String output) {
        this.output = output;
    }

    public void setConfig(final String config) {
        this.config = config;
    }

    public void setTmp(final String tmp) throws IOException {
        this.tmp = tmp;

        final File tempDir = new File(tmp);

        // Redirect the temp dir for headless.
        StroomProperties.setOverrideProperty(StroomProperties.STROOM_TEMP, tempDir.getCanonicalPath(), StroomProperties.Source.USER_CONF);

        FileUtil.forgetTempDir();
    }

    @Override
    protected void checkArgs() {
        if (input == null) {
            failArg("input", "required");
        }
        if (output == null) {
            failArg("output", "required");
        }
        if (config == null) {
            failArg("config", "required");
        }
        if (tmp == null) {
            failArg("tmp", "required");
        }
    }

    private void init() {
        inputDir = new File(input);
        outputFile = new File(output);
        configFile = new File(config);
        tmpDir = new File(tmp);

        if (!inputDir.isDirectory()) {
            throw new RuntimeException("Input directory \"" + inputDir.getAbsolutePath() + "\" cannot be found!");
        }
        if (!outputFile.getParentFile().isDirectory()) {
            throw new RuntimeException("Output file \"" + outputFile.getParentFile().getAbsolutePath()
                    + "\" parent directory cannot be found!");
        }
        if (!configFile.isFile()) {
            throw new RuntimeException("Config file \"" + configFile.getAbsolutePath() + "\" cannot be found!");
        }

        // Make sure tmp dir exists and is empty.
        FileUtil.mkdirs(tmpDir);
        FileUtil.deleteFile(outputFile);
        FileSystemUtil.deleteContents(tmpDir);
    }

    @Override
    public void run() {
        new TaskScopeRunnable(new GenericServerTask(null, null, null, "Headless Stroom", null)) {
            @Override
            protected void exec() {
                new ThreadScopeRunnable() {
                    @Override
                    protected void exec() {
                        process();
                    }
                }.run();
            }
        }.run();
    }

    private void process() {
        final long startTime = System.currentTimeMillis();

        // Initialise some variables.
        init();

        // Read the configuration.
        readConfig();

        OutputStreamWriter outputStreamWriter = null;
        try {
            // Create the required output stream writer.
            final OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
            outputStreamWriter = new OutputStreamWriter(outputStream, StreamUtil.DEFAULT_CHARSET);

            // Create an XML writer.
            final TransformerHandler th = XMLUtil.createTransformerHandler(true);
            th.setResult(new StreamResult(outputStreamWriter));

            // Make sure the output is safe.
            final SafeXMLFilter safeXMLFilter = new SafeXMLFilter();
            safeXMLFilter.setContentHandler(th);

            // Create a filter that will deal with errors etc.
            final HeadlessFilter headlessFilter = new HeadlessFilter();
            headlessFilter.setContentHandler(safeXMLFilter);

            // Output the start root element.
            headlessFilter.beginOutput();

            processRepository(headlessFilter);

            // Output the end root element.
            headlessFilter.endOutput();

        } catch (final Throwable e) {
            LOGGER.error(e, e);
        } finally {
            try {
                // Close the output stream writer.
                if (outputStreamWriter != null) {
                    outputStreamWriter.flush();
                    outputStreamWriter.close();
                }
            } catch (final IOException e) {
                LOGGER.error(e, e);
            }
        }

        LOGGER.info("Processing completed in "
                + ModelStringUtil.formatDurationString(System.currentTimeMillis() - startTime));
    }

    private void processRepository(final HeadlessFilter headlessFilter) {
        try {
            final TaskManager taskManager = getAppContext().getBean(TaskManager.class);

            // Loop over all of the data files in the repository.
            final StroomZipRepository repo = new StroomZipRepository(inputDir.getAbsolutePath());
            for (final File zipFile : repo.getZipFiles()) {
                LOGGER.info("Processing: " + zipFile.getAbsolutePath());

                final StroomZipFile stroomZipFile = new StroomZipFile(zipFile);
                final StroomZipNameSet nameSet = stroomZipFile.getStroomZipNameSet();

                // Process each base file in a consistent order
                for (final String baseName : nameSet.getBaseNameList()) {
                    final InputStream dataStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.Data);
                    final InputStream metaStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.Meta);
                    final InputStream contextStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.Context);

                    final HeadlessTranslationTask task = new HeadlessTranslationTask(
                            IgnoreCloseInputStream.wrap(dataStream), IgnoreCloseInputStream.wrap(metaStream),
                            IgnoreCloseInputStream.wrap(contextStream), headlessFilter);
                    taskManager.exec(task);
                }

                // Close the zip file.
                stroomZipFile.close();
            }

        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }

    private void readConfig() {
        LOGGER.info("Reading configuration from: " + configFile.getAbsolutePath());

        final ImportExportService importExportService = getAppContext().getBean(ImportExportService.class);
        importExportService.performImportWithoutConfirmation(configFile);

        final NodeCache nodeCache = getAppContext().getBean(NodeCache.class);
        final VolumeService volumeService = getAppContext().getBean(VolumeService.class);
        volumeService
                .save(Volume.create(nodeCache.getDefaultNode(), tmpDir.getAbsolutePath() + "/cvol", VolumeType.PUBLIC));
    }

    private ApplicationContext getAppContext() {
        if (appContext == null) {
            appContext = buildAppContext();
        }
        return appContext;
    }

    private ApplicationContext buildAppContext() {
        System.setProperty("spring.profiles.active", StroomSpringProfiles.PROD);
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ScopeConfiguration.class, PersistenceConfiguration.class, ServerComponentScanConfiguration.class,
                ServerConfiguration.class, CachedServiceConfiguration.class, PipelineConfiguration.class);
        return context;
    }

    public static void main(final String[] args) throws Exception {
        new Headless().doMain(args);
    }
}
