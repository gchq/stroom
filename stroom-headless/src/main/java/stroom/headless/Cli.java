/*
 * Copyright 2017 Crown Copyright
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

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.fs.FSPersistenceConfig;
import stroom.entity.util.XMLUtil;
import stroom.pipeline.scope.PipelineScopeRunnable;
import stroom.importexport.ImportExportService;
import stroom.persist.CoreConfig;
import stroom.proxy.repo.StroomZipFile;
import stroom.proxy.repo.StroomZipFileType;
import stroom.proxy.repo.StroomZipNameSet;
import stroom.proxy.repo.StroomZipRepository;
import stroom.task.ExternalShutdownController;
import stroom.util.AbstractCommandLineTool;
import stroom.util.io.FileUtil;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Command line tool to process some files from a proxy stroom.
 */
public class Cli extends AbstractCommandLineTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(Cli.class);

    private String input;
    private String error;
    private String config;
    private String content;
    private String tmp;

    private Path inputDir;
    private Path errorFile;
    private Path configFile;
    private Path contentDir;
    private Path tmpDir;

    @Inject
    private CoreConfig coreConfig;
    @Inject
    private FSPersistenceConfig fsPersistenceConfig;
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;
    @Inject
    private ImportExportService importExportService;
    @Inject
    private Provider<CliTranslationTaskHandler> translationTaskHandlerProvider;

    public static void main(final String[] args) {
        new Cli().doMain(args);
    }

    public void setInput(final String input) {
        this.input = input;
    }

    public void setError(final String error) {
        this.error = error;
    }

    public void setConfig(final String config) {
        this.config = config;
    }

    public void setContent(final String content) {
        this.content = content;
    }

    public void setTmp(final String tmp) {
        this.tmp = tmp;
    }

    @Override
    protected void checkArgs() {
        if (input == null) {
            failArg("input", "required");
        }
        if (error == null) {
            failArg("error", "required");
        }
//        if (config == null && content == null) {
//            failArg("config", "required");
//        }
        if (content == null) {
            failArg("content", "required");
        }
        if (tmp == null) {
            failArg("tmp", "required");
        }
    }

    private void init() {
        inputDir = getPath(input);
        errorFile = getPath(error);
        configFile = getPath(config);
        contentDir = getPath(content);
        tmpDir = getPath(tmp);

        if (!Files.isDirectory(inputDir)) {
            throw new RuntimeException("Input directory \"" + FileUtil.getCanonicalPath(inputDir) + "\" cannot be found!");
        }
        if (!Files.isDirectory(errorFile.getParent())) {
            throw new RuntimeException("Output file \"" + FileUtil.getCanonicalPath(errorFile.getParent())
                    + "\" parent directory cannot be found!");
        }
//        if (!Files.isRegularFile(configFile)) {
//            throw new RuntimeException("Config file \"" + FileUtil.getCanonicalPath(configFile) + "\" cannot be found!");
//        }
        if (!Files.isDirectory(contentDir)) {
            throw new RuntimeException("Content dir \"" + FileUtil.getCanonicalPath(contentDir) + "\" cannot be found!");
        }

        // Make sure tmp dir exists and is empty.
        FileUtil.mkdirs(tmpDir);
        FileUtil.deleteFile(errorFile);
        FileUtil.deleteContents(tmpDir);
    }

    private Path getPath(final String string) {
        if (string == null) {
            return null;
        }
        return Paths.get(string);
    }

    @Override
    public void run() {
        try {
            // Initialise some variables.
            init();

            // Create the Guice injector and inject members.
            createInjector();

            // Setup temp dir.
            final Path tempDir = Paths.get(tmp);
            coreConfig.setTemp(FileUtil.getCanonicalPath(tempDir));

            process();
        } finally {
            ExternalShutdownController.shutdown();
        }
    }

    private void process() {
        final long startTime = System.currentTimeMillis();

        pipelineScopeRunnable.scopeRunnable(this::processInPipelineScope);

        LOGGER.info("Processing completed in "
                + ModelStringUtil.formatDurationString(System.currentTimeMillis() - startTime));
    }

    private void processInPipelineScope() {
        // Set the content directory.
        fsPersistenceConfig.setPath(contentDir.toAbsolutePath().toString());

        // Read the configuration.
        readConfig();

        Writer errorWriter = null;
        try {
            // Create the required output stream writer.
            final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(errorFile));
            errorWriter = new OutputStreamWriter(outputStream, StreamUtil.DEFAULT_CHARSET);

            // Create an XML writer.
            final TransformerHandler th = XMLUtil.createTransformerHandler(true);
            th.setResult(new StreamResult(errorWriter));


            processRepository(errorWriter);


        } catch (final IOException | TransformerConfigurationException | RuntimeException e) {
            LOGGER.error("Unable to process", e);
        } finally {
            try {
                // Close the output stream writer.
                if (errorWriter != null) {
                    errorWriter.flush();
                    errorWriter.close();
                }
            } catch (final IOException e) {
                LOGGER.error("Unable to flush and close outputStreamWriter", e);
            }
        }
    }

    private void processRepository(final Writer errorWriter) {
        try {
            // Loop over all of the data files in the repository.
            final StroomZipRepository repo = new StroomZipRepository(FileUtil.getCanonicalPath(inputDir));
            final List<Path> zipFiles = repo.listAllZipFiles();
            zipFiles.sort(Comparator.naturalOrder());
            try (final Stream<Path> stream = zipFiles.stream()) {
                stream.forEach(p -> {
                    try {
                        LOGGER.info("Processing: " + FileUtil.getCanonicalPath(p));

                        final StroomZipFile stroomZipFile = new StroomZipFile(p);
                        final StroomZipNameSet nameSet = stroomZipFile.getStroomZipNameSet();

                        // Process each base file in a consistent order
                        for (final String baseName : nameSet.getBaseNameList()) {
                            final InputStream dataStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.Data);
                            final InputStream metaStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.Meta);
                            final InputStream contextStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.Context);

                            final CliTranslationTask task = new CliTranslationTask(
                                    IgnoreCloseInputStream.wrap(dataStream), IgnoreCloseInputStream.wrap(metaStream),
                                    IgnoreCloseInputStream.wrap(contextStream), errorWriter);
                            final CliTranslationTaskHandler handler = translationTaskHandlerProvider.get();
                            handler.exec(task);
                        }

                        // Close the zip file.
                        stroomZipFile.close();
                    } catch (final IOException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                });
            }
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to process repository!", e);
        }
    }

    private void readConfig() {
        if (configFile != null && Files.isRegularFile(configFile)) {
            LOGGER.info("Reading configuration from: " + FileUtil.getCanonicalPath(configFile));
            importExportService.performImportWithoutConfirmation(configFile);
        }
    }

    private void createInjector() {
        final Injector injector = Guice.createInjector(new CliModule());
        injector.injectMembers(this);
    }
}
