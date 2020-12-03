package stroom.config.app;

import stroom.util.io.StreamUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Strings;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Function;

class TestStroomConfigurationSourceProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestStroomConfigurationSourceProvider.class);

    @Test
    void test() throws IOException {

        Path file = Paths.get("/home/dev/tmp/1.yml");

        final ConfigurationSourceProvider delegate = new SubstitutingSourceProvider(
                new FileConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false));


        try (final InputStream in = delegate.open(file.toAbsolutePath().toString())) {
            // This is the string form of the yaml after passing though the delegate
            // substitutions
            String yaml = StreamUtil.streamToString(in);

//            final String yaml = Files.toString(file.toFile(), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            JsonNode rootNode = mapper.readValue(yaml, JsonNode.class);
//            JsonNode loggingNode = node.at("/logging");
//
//            final List<JsonNode> currentLogFilenameNodes = loggingNode.findValues("currentLogFilename");
//            final List<JsonNode> currentLogFilenameParentNodes = loggingNode.findParents("currentLogFilename");
//
//            currentLogFilenameParentNodes.forEach(parent -> {
//
//                ObjectNode objectNode = (ObjectNode) node;
//                objectNode.pa
//            });

            JsonNode logFileNameParent = rootNode.at("/logging/appenders/1");
            JsonNode logFileName = rootNode.at("/logging/appenders/1/currentLogFilename");

            LOGGER.info("{}", logFileName.asText());

            ((ObjectNode) logFileNameParent).put("currentLogFilename", "xxx");

            final Function<String, String> mutator = str -> "xxx" + str;

            List.of(rootNode.at("/server"), rootNode.at("/logging"))
                    .forEach(node ->
                            mutateNode(
                                    node,
                                    List.of("currentLogFilename", "archivedLogFilenamePattern"),
                                    mutator,
                                    ""));

            LOGGER.info("{}", mapper.writeValueAsString(rootNode));
        }
    }



    private void mutateNode(final JsonNode parent,
                            final List<String> names,
                            final Function<String, String> valueMutator,
                            final String path) {

        if (parent instanceof ArrayNode) {
            for (int i = 0; i < parent.size(); i++) {
                mutateNode(parent.get(i), names, valueMutator, path + "/" + i);
            }
        } else if (parent instanceof ObjectNode) {
            parent.fields().forEachRemaining(entry -> {
                final String newPath = path + "/" + entry.getKey();
                if (names.contains(entry.getKey())) {
                    // found our node so mutate it
                    LOGGER.info("Mutating {}", newPath);
                    final String value = entry.getValue().textValue();
                    final String newValue = valueMutator.apply(value);
                    ((ObjectNode) parent).put(entry.getKey(), newValue);
                } else {
                    // not our node so recurse into it
                    mutateNode(entry.getValue(), names, valueMutator, path + "/" + entry.getKey());
                }
            });
        }
    }


    @Test
    void testHexViewer() throws IOException {

        final int bytesPerLine = 30;
        final Charset charset = StandardCharsets.UTF_8;
        final CharsetDecoder charsetDecoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        final String sourceStr = "These are a load of boring words to view in hex.\n" +
                "And so are these, waffle, waffle, etc.\n" +
                "And here is a cheeky emoji 😀.";

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
                sourceStr.getBytes(charset));

        final StringBuilder stringBuilder = new StringBuilder();
        int lineNo = 0;
        while (true) {
            if (lineNo != 0) {
                stringBuilder.append("\n");
            }
            final byte[] lineBytes = new byte[bytesPerLine];
            final int len = byteArrayInputStream.read(lineBytes);

            if (len == -1) {
                break;
            }

            bytesToString(stringBuilder, lineNo++, lineBytes, len, bytesPerLine, charsetDecoder);
        }

        System.out.println(stringBuilder.toString());
    }

    private void bytesToString(final StringBuilder stringBuilder,
                               final long lineNo,
                               final byte[] lineBytes,
                               final int len,
                               final int bytesPerLine,
                               final CharsetDecoder charsetDecoder) {
        final long firstByteNo = lineNo * bytesPerLine;
        stringBuilder
                .append(Strings.padStart(Long.toHexString(firstByteNo), 10, '0'))
                .append(" ");

        final StringBuilder hexStringBuilder = new StringBuilder();
        final StringBuilder decodedStringBuilder = new StringBuilder();
        for (int i = 0; i < lineBytes.length; i++) {
            if (i < len) {
                byte[] arr = new byte[] {lineBytes[i]};

                hexStringBuilder
                        .append(Hex.encodeHexString(arr))
                        .append(" ");

                ByteBuffer byteBuffer = ByteBuffer.wrap(arr);
                char chr;
                try {
                    CharBuffer charBuffer = charsetDecoder.decode(byteBuffer);
                    chr = charBuffer.charAt(0);
                } catch (CharacterCodingException e) {
                    chr = '�';
                }
                appendChar(chr, decodedStringBuilder);

            } else {
                hexStringBuilder
                        .append("   ");
                decodedStringBuilder.append(" ");
            }

            if (i != 0
                    && (i + 1) % 4 == 0) {
                hexStringBuilder.append(" ");
            }
        }
        stringBuilder
                .append(hexStringBuilder)
                .append(decodedStringBuilder);
    }

    private void appendChar(final char c, final StringBuilder stringBuilder) {
        if (c == 0) {
            stringBuilder.append(" ");
        } else if (c == ' ') {
            stringBuilder.append('␣');
        } else if (c == '\n') {
            stringBuilder.append('↲');
        } else if (c == '\r') {
            stringBuilder.append('↩');
        } else if (c == '\t') {
            stringBuilder.append('↹');
        } else {
            stringBuilder.append(c);
        }
    }


}