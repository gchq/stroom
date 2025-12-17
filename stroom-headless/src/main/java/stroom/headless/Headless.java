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
import stroom.pipeline.filter.SafeXMLFilter;
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
import stroom.util.shared.NullSafe;
import stroom.util.xml.XMLUtil;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

/**
 * <p>
 * <strong>Stroom-Headless</strong>
 * </p><p>
 * A cut-down and headless version of Stroom for processing data using a pipeline.
 * </p><p>
 * It ingests one or more ZIP files in proxy ZIP format, processes all contained data using the Pipeline(s)
 * that correspond to the Feed name and outputs the result to a file.
 * </p><p>
 * Arguments:
 * <ul>
 *     <li><strong>input</strong> (mandatory) - The path of the directory containing the input data in proxy ZIP format.
 *     All ZIP files found in this directory will be processed. Will recurse into sub-directories.
 *     The meta for the data in the ZIP file(s) must contain a Feed key. The content or config ZIP must then
 *     contain Pipelines with the same name(s) as all Feed keys in the input data.</li>
 *     <li><strong>output</strong> (mandatory) - The path of the output file to create. The output of the
 *     pipeline process (for all input files) will be appended to this file. The file will be deleted if it
 *     already exists.</li>
 *     <li><strong>config</strong> (optional) - The path to a Stroom content pack ZIP file containing the Pipeline(s)
 *     and associated content (e.g. Schemas, XSLTs, TextConverters, etc.).</li>
 *     <li><strong>content</strong> (mandatory) - The path to a directory where the Stroom content will be
 *     imported into or read from. If the 'config' argument is not supplied, content must be present in
 *     this directory.</li>
 *     <li><strong>tmp</strong> (mandatory) - The path to a directory to use as a temporary directory.</li>
 * </ul>
 * </p>
 */
public class Headless extends AbstractCommandLineTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(Headless.class);

    private String input;
    private String output;
    private String config;
    private String content;
    private String tmp;

    private Path inputDir;
    private Path outputFile;
    private Path configFile;
    private Path contentDir;

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
    private Provider<HeadlessTranslationTaskHandler> translationTaskHandlerProvider;

    public static void main(final String[] args) {
        new Headless().doMain(args);
    }

    public void setInput(final String input) {
        this.input = input;
    }

    public void setOutput(final String output) {
        this.output = output;
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
        if (NullSafe.isBlankString(input)) {
            failMissingMandatoryArg("input");
        }
        if (NullSafe.isBlankString(output)) {
            failMissingMandatoryArg("output");
        }
        if (NullSafe.isBlankString(content)) {
            failMissingMandatoryArg("content");
        }
        if (NullSafe.isBlankString(tmp)) {
            failMissingMandatoryArg("tmp");
        }
    }

    private void init() {
        inputDir = Paths.get(input);
        outputFile = Paths.get(output);
        contentDir = Paths.get(content);

        checkIsDir("Input", inputDir);
        checkIsDir("Output", outputFile.getParent());
        if (NullSafe.isBlankString(config)) {
            configFile = null;
            // No config arg, so content must already be present
            checkContentIsPresent();
            LOGGER.info("No config file provided, using existing content in {}",
                    FileUtil.getCanonicalPath(contentDir));
        } else {
            configFile = Paths.get(config);
            checkIsFile("Config", configFile);
        }
        checkIsDir("Content", contentDir);

        final Path tmpDir = Paths.get(tmp);
        // Make sure tmp dir exists and is empty.
        FileUtil.deleteFile(outputFile);
        FileUtil.mkdirs(tmpDir);
        FileUtil.deleteContents(tmpDir);
    }

    private void checkContentIsPresent() {
        try (Stream<Path> pathStream = Files.list(contentDir)) {
            final long count = pathStream.count();
            if (count == 0) {
                throw new RuntimeException(LogUtil.message(
                        "Content directory '{}' is empty. Expecting it to contain Stroom content. " +
                        "A content pack ZIP file can be loaded using the 'config' argument.",
                        FileUtil.getCanonicalPath(contentDir)));
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
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

    @Override
    public void run() {
        try {
            // Initialise some variables.
            init();

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

        LOGGER.info("Output written to {}", FileUtil.getCanonicalPath(outputFile));
        LOGGER.info("Processing completed in "
                    + ModelStringUtil.formatDurationString(System.currentTimeMillis() - startTime));
    }

    private void processInPipelineScope() {
        // Set the content directory.
        fsPersistenceConfig.setPath(contentDir.toAbsolutePath().toString());

        // Read the configuration.
        if (configFile != null) {
            readConfig();
        }

        OutputStreamWriter outputStreamWriter = null;
        try {
            // Create the required output stream writer.
            final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(outputFile));
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

        } catch (final IOException | TransformerConfigurationException | SAXException | RuntimeException e) {
            LOGGER.error("Unable to process headless", e);
        } finally {
            try {
                // Close the output stream writer.
                if (outputStreamWriter != null) {
                    outputStreamWriter.flush();
                    outputStreamWriter.close();
                }
            } catch (final IOException e) {
                LOGGER.error("Unable to flush and close outputStreamWriter", e);
            }
        }
    }

    private void processRepository(final HeadlessFilter headlessFilter) {
        try {
            // Loop over all of the data files in the repository.
            try {
                final AtomicInteger fileCount = new AtomicInteger();
                Files.walkFileTree(
                        inputDir,
                        EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                        Integer.MAX_VALUE,
                        new AbstractFileVisitor() {
                            @Override
                            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                                try {
                                    if (file.toString().endsWith(".zip")) {
                                        process(headlessFilter, file);
                                        fileCount.incrementAndGet();
                                    }
                                } catch (final RuntimeException e) {
                                    LOGGER.error(e.getMessage(), e);
                                }
                                return super.visitFile(file, attrs);
                            }
                        });
                LOGGER.info("Processed {} ZIP files", fileCount.get());
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to process repository!", e);
        }
    }

    private void process(final HeadlessFilter headlessFilter, final Path zipFile) {
        LOGGER.info("Processing: {}", FileUtil.getCanonicalPath(zipFile));

        try (final StroomZipFile stroomZipFile = new StroomZipFile(zipFile)) {
            final List<String> baseNames = stroomZipFile.getBaseNames();

            // Process each base file in a consistent order
            for (final String baseName : baseNames) {
                final InputStream dataStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.DATA);
                final InputStream metaStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.META);
                final InputStream contextStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.CONTEXT);
                final HeadlessTranslationTaskHandler handler = translationTaskHandlerProvider.get();
                handler.exec(
                        IgnoreCloseInputStream.wrap(dataStream),
                        IgnoreCloseInputStream.wrap(metaStream),
                        IgnoreCloseInputStream.wrap(contextStream),
                        headlessFilter,
                        new SimpleTaskContext());
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void readConfig() {
        LOGGER.info("Reading configuration from: " + FileUtil.getCanonicalPath(configFile));
        importExportService.importConfig(configFile, ImportSettings.auto(), new ArrayList<>());
        LOGGER.info("Configuration imported into: " + FileUtil.getCanonicalPath(contentDir));
    }

    private void createInjector(final Path homeDir, final Path tempDir) {
        final Injector injector = Guice.createInjector(new CliModule(homeDir, tempDir));
        injector.injectMembers(this);
    }
}
