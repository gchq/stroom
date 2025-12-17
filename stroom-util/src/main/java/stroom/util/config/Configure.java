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

package stroom.util.config;

import stroom.util.AbstractCommandLineTool;
import stroom.util.io.FileUtil;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Usage java stroom.util.config.Configure
 * <pre><code>{@code <parameter file> <files to process>}</code></pre>
 */
public class Configure extends AbstractCommandLineTool {

    private String parameterFilePath = null;
    private String processFilePath = null;
    private boolean readParameter = true;
    private boolean exitOnError = true;

    private final List<Path> processFile = new ArrayList<>();
    private ParameterFile parameterFile;
    private final JAXBContext jaxbContext;

    public static void printUsage() {
        System.out.println(
                "java stroom.util.config.Configure " +
                        "parameterFile=<parameter file> " +
                        "processFile=<comma delimited files to process>");
    }

    public static void main(final String[] args) {
        new Configure().doMain(args);
    }

    public Configure() {
        try {
            jaxbContext = JAXBContext.newInstance(ParameterFile.class, Parameter.class);
        } catch (final JAXBException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void setParameterFile(final String parameterFile) {
        this.parameterFilePath = parameterFile;
    }

    public void setProcessFile(final String processFile) {
        this.processFilePath = processFile;
    }

    public void setReadParameter(final boolean readParameter) {
        this.readParameter = readParameter;
    }

    public void setExitOnError(final boolean exitOnError) {
        this.exitOnError = exitOnError;
    }

    public void marshal(final ParameterFile data, final OutputStream outputStream) {
        try {
            final Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(data, outputStream);
        } catch (final JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    public ParameterFile unmarshal(final InputStream inputStream) {
        try {
            final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            final ParameterFile parameterFile = (ParameterFile) unmarshaller.unmarshal(inputStream);

            // When XML is formatted sometimes extra spaces are introduced that
            // we need to remove
            for (final Parameter parameter : parameterFile.getParameter()) {
                if (parameter.getValue() != null) {
                    parameter.setValue(parameter.getValue().trim());
                }
            }

            return parameterFile;
        } catch (final JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    private void exitWithError() {
        throw new IllegalStateException();
    }

    @Override
    public void run() {
        try {
            if (parameterFilePath == null || processFilePath == null) {
                printUsage();
                exitWithError();
            }

            final Path parameter = Paths.get(parameterFilePath);

            if (!Files.isRegularFile(parameter)) {
                printUsage();
                System.out.print(parameterFilePath + " does not exist");
                exitWithError();
            }
            // Create parameters in
            try (final InputStream inputStream = Files.newInputStream(parameter)) {
                parameterFile = unmarshal(inputStream);
            }

            final StringTokenizer processFiles = new StringTokenizer(processFilePath, ",");
            while (processFiles.hasMoreTokens()) {
                final Path process = Paths.get(processFiles.nextToken());
                if (!Files.isRegularFile(process)) {
                    printUsage();
                    System.out.print(parameterFile + " does not exist");
                    exitWithError();
                }
                processFile.add(process);
            }

            readParameter();

            writeParameter();

            for (final Path file : processFile) {
                processFile(file);
            }

        } catch (final IllegalStateException isEx) {
            if (exitOnError) {
                System.exit(1);
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public void writeParameter() throws IOException {
        writeHeading("Updating " + parameterFilePath);
        try (final OutputStream outputStream = Files.newOutputStream(Paths.get(parameterFilePath))) {
            marshal(parameterFile, outputStream);
        }
    }

    public void processFile(final Path file) throws IOException {
        writeHeading(FileUtil.getCanonicalPath(file));

        final Path newFile = file.getParent().resolve(file.getFileName().toString() + ".tmp");
        Files.deleteIfExists(newFile);
        int replaceCount = 0;
        try (final BufferedReader buffReader = new BufferedReader(Files.newBufferedReader(file));
                final BufferedWriter buffWriter = new BufferedWriter(Files.newBufferedWriter(newFile))) {
            String line;
            int lineCount = 0;
            while ((line = buffReader.readLine()) != null) {
                lineCount++;
                for (final Parameter parameter : parameterFile.getParameter()) {
                    if (line.contains(parameter.getName())) {
                        line = line.replace(parameter.getName(), parameter.getValue());
                        replaceCount++;
                        System.out.println(FileUtil.getCanonicalPath(file) +
                                ": Replaced " + parameter.getName() + " with " + parameter.getValue() +
                                " at line " + lineCount);
                    }
                }
                buffWriter.write(line);
                buffWriter.newLine();
            }
        }

        if (replaceCount == 0) {
            System.out.println("Nothing replaced in file");
            Files.delete(newFile);
        } else {
            Files.delete(file);
            Files.move(newFile, file);
        }
        System.out.println();
    }

    private void readParameter() throws IOException {
        writeHeading("Parameters - Hit enter to use the default in brackets");

        final InputStreamReader inputStreamReader = new InputStreamReader(System.in);
        final BufferedReader reader = new BufferedReader(inputStreamReader);

        int maxName = 0;
        int maxValue = 0;
        int maxDescription = 0;
        for (final Parameter parameter : parameterFile.getParameter()) {
            maxName = Math.max(maxName, parameter.getName().length());
            maxValue = Math.max(maxValue, parameter.getValue().length());
            maxDescription = Math.max(maxDescription, parameter.getDescription().length());
        }

        for (final Parameter parameter : parameterFile.getParameter()) {
            int tryCount = 0;
            boolean ok;
            do {
                System.out.print(parameter.getName() + " : " + parameter.getDescription() + " [" + parameter.getValue()
                        + "] > ");
                if (readParameter) {
                    final String inputLine = reader.readLine();
                    if (inputLine != null && !inputLine.equals("")) {
                        parameter.setValue(inputLine.trim());
                    }
                } else {
                    System.out.println();
                }
                ok = parameter.validate();
                if (!ok) {
                    tryCount++;
                    System.out.println("Error [" + parameter.getValue() + "] does not match " + parameter.getRegEx());
                }
                System.out.println();
            } while (!ok && tryCount < 3);
            if (!ok) {
                System.out.println("Giving up!");
                exitWithError();
            }
        }
        System.out.println();
    }

    private void writeHeading(final String label) {
        System.out.println(label);
        for (int i = 0; i < label.length(); i++) {
            System.out.print("=");
        }
        System.out.println();
        System.out.println();
    }

}
