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

package stroom.headless;

import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
import stroom.docstore.impl.fs.FSPersistenceConfig;
import stroom.importexport.impl.ImportExportService;
import stroom.importexport.shared.ImportSettings;
import stroom.task.api.SimpleTaskContext;
import stroom.task.impl.ExternalShutdownController;
import stroom.util.AbstractCommandLineTool;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.FileUtil;
import stroom.util.io.HomeDirProviderImpl;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.io.TempDirProviderImpl;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.ModelStringUtil;
import stroom.util.xml.XMLUtil;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

/**
 * Command line tool to process some files from a proxy stroom.
 * <p>
 * No idea why this class was created or what purpose it was intended for.
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
    private HomeDirProviderImpl homeDirProvider;
    @Inject
    private TempDirProviderImpl tempDirProvider;
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
            failMissingMandatoryArg("input");
        }
        if (error == null) {
            failMissingMandatoryArg("error");
        }
//        if (config == null && content == null) {
//            failMissingMandatoryArg("config");
//        }
        if (content == null) {
            failMissingMandatoryArg("content");
        }
        if (tmp == null) {
            failMissingMandatoryArg("tmp");
        }
    }

    private void init() {
        inputDir = getPath(input);
        errorFile = getPath(error);
        configFile = getPath(config);
        contentDir = getPath(content);
        tmpDir = getPath(tmp);

        checkIsDir("Input", inputDir);
        checkIsDir("Error", errorFile.getParent());
//        checkIsFile("Config", configFile);
        checkIsDir("Content", contentDir);

        // Make sure tmp dir exists and is empty.
        FileUtil.deleteFile(errorFile);
        FileUtil.mkdirs(tmpDir);
        FileUtil.deleteContents(tmpDir);
    }

    private void checkIsDir(final String name,
                            final Path path) {
        if (!Files.exists(path)) {
            throw new RuntimeException(LogUtil.message("{} directory '{}' does not exist.",
                    name,
                    FileUtil.getCanonicalPath(path)));
        }
        if (!Files.isDirectory(path)) {
            throw new RuntimeException(LogUtil.message("{} directory '{}' is not a directory.",
                    name,
                    FileUtil.getCanonicalPath(path)));
        }
        if (!Files.isReadable(path)) {
            throw new RuntimeException(LogUtil.message("{} directory '{}' is not readable.",
                    name,
                    FileUtil.getCanonicalPath(path)));
        }
    }

    private void checkIsFile(final String name,
                             final Path path) {
        if (!Files.exists(path)) {
            throw new RuntimeException(LogUtil.message("{} file '{}' does not exist.",
                    name,
                    FileUtil.getCanonicalPath(path)));
        }
        if (!Files.isRegularFile(path)) {
            throw new RuntimeException(LogUtil.message("{} file '{}' is not a file.",
                    name,
                    FileUtil.getCanonicalPath(path)));
        }
        if (!Files.isReadable(path)) {
            throw new RuntimeException(LogUtil.message("{} file '{}' is not readable.",
                    name,
                    FileUtil.getCanonicalPath(path)));
        }
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

            // Setup temp dir.
            final Path tempDir = Paths.get(tmp);

            // Create the Guice injector and inject members.
            createInjector(tempDir, tempDir);

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
            try {
                Files.walkFileTree(inputDir,
                        EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                        Integer.MAX_VALUE,
                        new AbstractFileVisitor() {
                            @Override
                            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                                try {
                                    if (file.toString().endsWith(".zip")) {
                                        process(errorWriter, file);
                                    }
                                } catch (final RuntimeException e) {
                                    LOGGER.error(e.getMessage(), e);
                                }
                                return super.visitFile(file, attrs);
                            }
                        });
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to process repository!", e);
        }
    }

    private void process(final Writer errorWriter, final Path path) {
        LOGGER.info("Processing: " + FileUtil.getCanonicalPath(path));

        try (final StroomZipFile stroomZipFile = new StroomZipFile(path)) {
            final List<String> baseNames = stroomZipFile.getBaseNames();

            // Process each base file in a consistent order
            for (final String baseName : baseNames) {
                final InputStream dataStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.DATA);
                final InputStream metaStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.META);
                final InputStream contextStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.CONTEXT);

                final CliTranslationTaskHandler handler = translationTaskHandlerProvider.get();
                handler.exec(IgnoreCloseInputStream.wrap(dataStream), IgnoreCloseInputStream.wrap(metaStream),
                        IgnoreCloseInputStream.wrap(contextStream), errorWriter, new SimpleTaskContext());
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void readConfig() {
        if (configFile != null && Files.isRegularFile(configFile)) {
            LOGGER.info("Reading configuration from: " + FileUtil.getCanonicalPath(configFile));
            importExportService.importConfig(configFile, ImportSettings.auto(), new ArrayList<>());
        }
    }

    private void createInjector(final Path homeDir, final Path tempDir) {
        final Injector injector = Guice.createInjector(new CliModule(homeDir, tempDir));
        injector.injectMembers(this);
    }
}
