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

package stroom.util.config;

import stroom.util.AbstractCommandLineTool;
import stroom.util.io.FileUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
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

    private List<Path> processFile = new ArrayList<>();
    private ParameterFile parameterFile;

    public static void printUsage() {
        System.out.println(
                "java stroom.util.config.Configure parameterFile=<parameter file> processFile=<comma delimited files to process>");
    }

    public static void main(String[] args) {
        new Configure().doMain(args);
    }

    public void setParameterFile(String parameterFile) {
        this.parameterFilePath = parameterFile;
    }

    public void setProcessFile(String processFile) {
        this.processFilePath = processFile;
    }

    public void setReadParameter(boolean readParameter) {
        this.readParameter = readParameter;
    }

    public void setExitOnError(boolean exitOnError) {
        this.exitOnError = exitOnError;
    }

    public void marshal(ParameterFile data, OutputStream outputStream) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(ParameterFile.class, Parameter.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(data, outputStream);
        } catch (final JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    public ParameterFile unmarshal(InputStream inputStream) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(ParameterFile.class, Parameter.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            ParameterFile parameterFile = (ParameterFile) unmarshaller.unmarshal(inputStream);

            // When XML is formatted sometimes extra spaces are introduced that
            // we need to remove
            for (Parameter parameter : parameterFile.getParameter()) {
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

            Path parameter = Paths.get(parameterFilePath);

            if (!Files.isRegularFile(parameter)) {
                printUsage();
                System.out.print(parameterFilePath + " does not exist");
                exitWithError();
            }
            // Create parameters in
            try (final InputStream inputStream = Files.newInputStream(parameter)) {
                parameterFile = unmarshal(inputStream);
            }

            StringTokenizer processFiles = new StringTokenizer(processFilePath, ",");
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

            for (Path file : processFile) {
                processFile(file);
            }

        } catch (IllegalStateException isEx) {
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

    public void processFile(Path file) throws IOException {
        writeHeading(FileUtil.getCanonicalPath(file));

        Path newFile = file.getParent().resolve(file.getFileName().toString() + ".tmp");
        Files.deleteIfExists(newFile);
        int replaceCount = 0;
        try (BufferedReader buffReader = new BufferedReader(Files.newBufferedReader(file));
             BufferedWriter buffWriter = new BufferedWriter(Files.newBufferedWriter(newFile))) {
            String line;
            int lineCount = 0;
            while ((line = buffReader.readLine()) != null) {
                lineCount++;
                for (Parameter parameter : parameterFile.getParameter()) {
                    if (line.contains(parameter.getName())) {
                        line = line.replace(parameter.getName(), parameter.getValue());
                        replaceCount++;
                        System.out.println(FileUtil.getCanonicalPath(file) + ": Replaced " + parameter.getName() + " with "
                                + parameter.getValue() + " at line " + lineCount);
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

        InputStreamReader inputStreamReader = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(inputStreamReader);

        int maxName = 0;
        int maxValue = 0;
        int maxDescription = 0;
        for (Parameter parameter : parameterFile.getParameter()) {
            maxName = Math.max(maxName, parameter.getName().length());
            maxValue = Math.max(maxValue, parameter.getValue().length());
            maxDescription = Math.max(maxDescription, parameter.getDescription().length());
        }

        for (Parameter parameter : parameterFile.getParameter()) {
            int tryCount = 0;
            boolean ok;
            do {
                System.out.print(parameter.getName() + " : " + parameter.getDescription() + " [" + parameter.getValue()
                        + "] > ");
                if (readParameter) {
                    String inputLine = reader.readLine();
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

    private void writeHeading(String label) {
        System.out.println(label);
        for (int i = 0; i < label.length(); i++) {
            System.out.print("=");
        }
        System.out.println();
        System.out.println();
    }

}
