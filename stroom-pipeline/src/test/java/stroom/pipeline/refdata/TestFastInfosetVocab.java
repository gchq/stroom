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

package stroom.pipeline.refdata;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.sun.xml.fastinfoset.sax.SAXDocumentParser;
import com.sun.xml.fastinfoset.sax.SAXDocumentSerializer;
import com.sun.xml.fastinfoset.tools.VocabularyGenerator;
import com.sun.xml.fastinfoset.vocab.ParserVocabulary;
import com.sun.xml.fastinfoset.vocab.SerializerVocabulary;
import org.junit.jupiter.api.Test;
import org.jvnet.fastinfoset.FastInfosetException;
import org.jvnet.fastinfoset.Vocabulary;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * A proof of concept for https://github.com/gchq/stroom/issues/1945
 * Trying to prove that we can generate a fast infoset vocab
 * for some xml and then append to it. This is to reduce the volume of data
 * stored in the LMDB ref data store.
 */
public class TestFastInfosetVocab {

    private static final String URI = "MY_URI";

    protected static final String XML_1 = """
            <?xml version="1.0" encoding="UTF-8" standalone="no" ?>
            <evt:UserDetails xmlns:evt="event-logging">
              <evt:StaffNumber>staff1</evt:StaffNumber>
              <evt:Surname>Bentley</evt:Surname>
              <evt:Initials>J W</evt:Initials>
              <evt:GivenName>Jameson</evt:GivenName>
              <evt:Location>Kinross-shire</evt:Location>
              <evt:Phone>04331 444528</evt:Phone>
              <evt:SupervisorStaffNumber>staff173</evt:SupervisorStaffNumber>
            </evt:UserDetails>""";

    protected static final String XML_2 = """
            <?xml version="1.0" encoding="UTF-8" standalone="no" ?>
            <evt:UserDetails xmlns:evt="event-logging">
              <evt:StaffNumber>staff2</evt:StaffNumber>
              <evt:Surname>Bloggs</evt:Surname>
              <evt:Initials>J</evt:Initials>
              <evt:GivenName>Joe</evt:GivenName>
              <evt:Location>The-shire</evt:Location>
              <evt:Phone>1234</evt:Phone>
              <evt:SupervisorStaffNumber>staff174</evt:SupervisorStaffNumber>
            </evt:UserDetails>""";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestFastInfosetVocab.class);

    @Test
    void simpleTest() throws ParserConfigurationException, SAXException, IOException, FastInfosetException {

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final SAXParser saxParser = factory.newSAXParser();

        final ByteArrayOutputStream os;
        try (final Reader reader = new StringReader(XML_1)) {
            try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                os = byteArrayOutputStream;
                final SAXDocumentSerializer saxDocumentSerializer = new SAXDocumentSerializer();
                saxDocumentSerializer.setOutputStream(byteArrayOutputStream);
                saxParser.parse(new InputSource(reader), saxDocumentSerializer);
            }
        }

        final byte[] fastInfosetBytes = os.toByteArray();

        LOGGER.info("xml bytes len: {}", XML_1.getBytes().length);
        LOGGER.info("ser bytes len: {}", fastInfosetBytes.length);

        final SAXDocumentParser saxDocumentParser = new SAXDocumentParser();
        saxDocumentParser.setContentHandler(new LoggingHandler());
        //private static class LoggingHandler extends DefaultHandler {

        try (final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fastInfosetBytes)) {
            saxDocumentParser.parse(byteArrayInputStream);
        }
    }

    @Test
    void test() throws ParserConfigurationException, SAXException, IOException, FastInfosetException {

        LOGGER.info("Serialise XML1 with no vocab");
        final byte[] fastInfosetBytes1a = ser(XML_1, null);

        LOGGER.info(LogUtil.inSeparatorLine("Deser 1"));
        deser(fastInfosetBytes1a, (ParserVocabulary) null);

//        final Vocabulary vocabulary1 = buildVocab(XML_1, null);
        final Vocabs vocabs1 = buildVocab(
                XML_1,
                null,
                null);
        dumpVocabs(vocabs1);
        final Vocabulary vocabulary1 = vocabs1.vocabulary();

        LOGGER.info("Serialise XML1 with vocab1");
        final byte[] fastInfosetBytes1b = ser(XML_1, vocabs1.serializerVocabulary());

        LOGGER.info("Serialise XML2 with vocab1");
        final byte[] fastInfosetBytes2a = ser(XML_2, vocabs1.serializerVocabulary());

        LOGGER.info(LogUtil.inSeparatorLine("Deser 2a1"));
        deser(fastInfosetBytes2a, vocabs1.parserVocabulary());

        final Vocabs vocabs2 = buildVocab(XML_2, vocabs1.serializerVocabulary(), vocabs1.parserVocabulary());
        dumpVocabs(vocabs2);
        final Vocabulary vocabulary2 = vocabs2.vocabulary();

        LOGGER.info("Serialise XML2 with vocab2");
        final byte[] fastInfosetBytes2b = ser(XML_2, vocabs2.serializerVocabulary());

        LOGGER.info(LogUtil.inSeparatorLine("Deser 2a2"));
        deser(fastInfosetBytes2a, vocabs2.parserVocabulary());

        LOGGER.info(LogUtil.inSeparatorLine("Deser 2b2"));
        deser(fastInfosetBytes2b, vocabs2.parserVocabulary());

//        inputStream.reset();
//        byteArrayOutputStream.reset();
//        saxParser.reset();
//
//        final SerialiserHandler serialiserHandler2 = new SerialiserHandler();
//        serialiserHandler2.setOutputStream(byteArrayOutputStream);
//        serialiserHandler2.setVocab(vocabulary);
//        saxParser.parse(inputStream, serialiserHandler2);
//        final byte[] fastInfosetBytes2 = byteArrayOutputStream.toByteArray();
//
//        LOGGER.info("bytes len: {}", fastInfosetBytes2.length);

//        LOGGER.info("deser:\n{}", deser(fastInfosetBytes, vocabulary));
    }

    private void dumpVocabs(final Vocabs vocabs) {
        LOGGER.info("""

                        vocabulary:           {}
                        serializerVocabulary: {}
                        parserVocabulary:     {}""",
                vocabs.vocabulary().characterContentChunks.size(),
                vocabs.serializerVocabulary().characterContentChunk.size(),
                vocabs.parserVocabulary().characterContentChunk.getSize());
    }

    private Vocabulary buildVocab(final String xml, final Vocabulary vocabulary)
            throws ParserConfigurationException, SAXException, IOException {

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final SAXParser saxParser = factory.newSAXParser();

        final InputStream inputStream = new ByteArrayInputStream(xml.getBytes());

        final VocabularyGenerator vocabularyGenerator;
        SerializerVocabulary serializerVocabulary = null;
        ParserVocabulary parserVocabulary = null;
        if (vocabulary != null) {

            serializerVocabulary = createSerializerVocabulary(vocabulary);
            parserVocabulary = new ParserVocabulary(vocabulary);
            vocabularyGenerator = new VocabularyGenerator(serializerVocabulary, parserVocabulary);
        } else {
            vocabularyGenerator = new VocabularyGenerator();
        }
        LOGGER.info(LogUtil.inSeparatorLine("Generating vocab"));
        saxParser.parse(inputStream, vocabularyGenerator);
        return vocabularyGenerator.getVocabulary();
    }

    private Vocabs buildVocab(
            final String xml,
            final SerializerVocabulary serializerVocabulary,
            final ParserVocabulary parserVocabulary)
            throws ParserConfigurationException, SAXException, IOException {

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final SAXParser saxParser = factory.newSAXParser();

        final InputStream inputStream = new ByteArrayInputStream(xml.getBytes());

        SerializerVocabulary serializerVocabularyLocal = null;
        ParserVocabulary parserVocabularyLocal = null;

        final VocabularyGenerator vocabularyGenerator;
        if (serializerVocabulary != null) {
            serializerVocabularyLocal = serializerVocabulary;
            parserVocabularyLocal = parserVocabulary;
        } else {
            serializerVocabularyLocal = new SerializerVocabulary();
            parserVocabularyLocal = new ParserVocabulary();
        }
//        vocabularyGenerator = new VocabularyGenerator(serializerVocabularyLocal, parserVocabularyLocal);

        vocabularyGenerator = new NoCharsVocabularyGenerator(serializerVocabularyLocal, parserVocabularyLocal);
        LOGGER.info(LogUtil.inSeparatorLine("Generating vocab"));
        saxParser.parse(inputStream, vocabularyGenerator);

        return new Vocabs(vocabularyGenerator.getVocabulary(), serializerVocabularyLocal, parserVocabularyLocal);
    }

    private byte[] ser(final String xml, final SerializerVocabulary serializerVocabulary)
            throws IOException, SAXException, ParserConfigurationException {

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final SAXParser saxParser = factory.newSAXParser();

        final ByteArrayOutputStream os;
        try (final Reader reader = new StringReader(xml)) {
            try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                os = byteArrayOutputStream;
                final SAXDocumentSerializer saxDocumentSerializer = new SAXDocumentSerializer();
                saxDocumentSerializer.setOutputStream(byteArrayOutputStream);
                if (serializerVocabulary != null) {
//                    saxDocumentSerializer.setVocabulary(createSerializerVocabulary(vocabulary));
//                    saxDocumentSerializer.setVocabulary(createSerializerVocabulary(vocabulary));
                    // Not quite sure why we have to set our serializerVocabulary as an externalVocab on
                    // a new SerializerVocabulary.
                    final SerializerVocabulary serializerVocabularyLocal = new SerializerVocabulary();
                    serializerVocabularyLocal.setExternalVocabulary(URI, serializerVocabulary, false);
//                    serializerVocabulary = createSerializerVocabulary(vocabulary);
//                    serializerVocabulary.setExternalVocabulary(
//                            URI, createSerializerVocabulary(vocabulary), false);
                    saxDocumentSerializer.setVocabulary(serializerVocabularyLocal);
//                    dumpVocab(vocabulary, "Pre ser input vocab");
//                    dumpVocab(vocabulary, "Pre ser serialiser vocab");
                }

                LOGGER.info("Serialise xml");
                saxParser.parse(new InputSource(reader), saxDocumentSerializer);

//                if (vocabulary != null) {
//                    dumpVocab(vocabulary, "Post ser input vocab");
//                    dumpVocab(vocabulary, "Post ser serialiser vocab");
//                }
            }
        }

        final byte[] fastInfosetBytes = os.toByteArray();

        LOGGER.info("Serialising {} bytes of XML to {} bytes", xml.getBytes().length, fastInfosetBytes.length);
        return fastInfosetBytes;
    }

    private void dumpVocab(final Vocabulary vocabulary, final String name) {

        LOGGER.info("""
                        {} - {}:
                        namespaces: {}
                        localNames: {}
                        elements: {}
                        charChunks: {}""",
                name,
                vocabulary.getClass().getSimpleName(),
                vocabulary.namespaceNames.size(),
                vocabulary.localNames.size(),
                vocabulary.elements.size(),
                vocabulary.characterContentChunks);
    }

    private void deser(final byte[] fastInfosetBytes,
                       final Vocabulary vocabulary)
            throws FastInfosetException, IOException, SAXException {

        final SAXDocumentParser saxDocumentParser = new SAXDocumentParser();
        saxDocumentParser.setContentHandler(new LoggingHandler());
        if (vocabulary != null) {
            final ParserVocabulary parserVocabulary = new ParserVocabulary(vocabulary);
            saxDocumentParser.setExternalVocabularies(Map.of(URI, parserVocabulary));
//            saxDocumentParser.setVocabulary();
        }
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fastInfosetBytes);
        saxDocumentParser.parse(byteArrayInputStream);

        //https://github.com/pbielicki/fastinfoset-java/blob/master/samples/src/main/java/samples/sax/SAXParsingSample.java
    }

    private void deser(final byte[] fastInfosetBytes,
                       final ParserVocabulary parserVocabulary)
            throws FastInfosetException, IOException, SAXException {

        final SAXDocumentParser saxDocumentParser = new SAXDocumentParser();
        saxDocumentParser.setContentHandler(new LoggingHandler());
        if (parserVocabulary != null) {
            saxDocumentParser.setExternalVocabularies(Map.of(URI, parserVocabulary));
//            saxDocumentParser.setVocabulary();
        }
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fastInfosetBytes);
        saxDocumentParser.parse(byteArrayInputStream);

        //https://github.com/pbielicki/fastinfoset-java/blob/master/samples/src/main/java/samples/sax/SAXParsingSample.java
    }

    private static SerializerVocabulary createSerializerVocabulary(final Vocabulary vocabulary) {
        return new SerializerVocabulary(vocabulary, true);
    }


    // --------------------------------------------------------------------------------


    private static class SerialiserHandler extends DefaultHandler {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SerialiserHandler.class);

        final SAXDocumentSerializer saxDocumentSerializer = new SAXDocumentSerializer();

        public void setOutputStream(final OutputStream outputStream) {
            saxDocumentSerializer.setOutputStream(outputStream);
        }

        public void setVocab(final Vocabulary vocab) {
            final SerializerVocabulary serializerVocabulary = createSerializerVocabulary(vocab);
            saxDocumentSerializer.setVocabulary(serializerVocabulary);
        }

        @Override
        public void startDocument() throws SAXException {
            LOGGER.debug("startDocument()");
            super.startDocument();
            saxDocumentSerializer.reset();
            saxDocumentSerializer.startDocument();
        }

        @Override
        public void endDocument() throws SAXException {
            LOGGER.debug("endDocument()");
            super.endDocument();
            saxDocumentSerializer.endDocument();
        }

        @Override
        public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
            LOGGER.debug("startPrefixMapping({}, {})", prefix, uri);
            super.startPrefixMapping(prefix, uri);
            saxDocumentSerializer.startPrefixMapping(prefix, uri);
        }

        @Override
        public void endPrefixMapping(final String prefix) throws SAXException {
            LOGGER.debug("endPrefixMapping({})", prefix);
            super.endPrefixMapping(prefix);
            saxDocumentSerializer.endPrefixMapping(prefix);
        }

        @Override
        public void startElement(final String uri,
                                 final String localName,
                                 final String qName,
                                 final Attributes attributes)
                throws SAXException {
            LOGGER.debug("startElement {} {} {}", uri, localName, qName);
            super.startElement(uri, localName, qName, attributes);
            saxDocumentSerializer.startElement(uri, localName, qName, attributes);
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) throws SAXException {
            LOGGER.debug("endElement   {} {} {}", uri, localName, qName);
            super.endElement(uri, localName, qName);
            saxDocumentSerializer.endElement(uri, localName, qName);
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
            super.characters(ch, start, length);
            saxDocumentSerializer.characters(ch, start, length);
        }
    }


    // --------------------------------------------------------------------------------


    private static class LoggingHandler extends DefaultHandler {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LoggingHandler.class);

        @Override
        public void startDocument() throws SAXException {
            LOGGER.debug("startDocument()");
            super.startDocument();
        }

        @Override
        public void endDocument() throws SAXException {
            LOGGER.debug("endDocument()");
            super.endDocument();
        }

        @Override
        public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
            LOGGER.debug("startPrefixMapping({}, {})", prefix, uri);
            super.startPrefixMapping(prefix, uri);
        }

        @Override
        public void endPrefixMapping(final String prefix) throws SAXException {
            LOGGER.debug("endPrefixMapping({})", prefix);
            super.endPrefixMapping(prefix);
        }

        @Override
        public void startElement(final String uri,
                                 final String localName,
                                 final String qName,
                                 final Attributes attributes)
                throws SAXException {
            LOGGER.debug("startElement {} {} {}", uri, localName, qName);
            super.startElement(uri, localName, qName, attributes);
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) throws SAXException {
            LOGGER.debug("endElement   {} {} {}", uri, localName, qName);
            super.endElement(uri, localName, qName);
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
            final String chrs = String.copyValueOf(ch, start, length);
            if (!chrs.isBlank()) {
                LOGGER.debug("characters \"{}\"", String.copyValueOf(ch, start, length));
            }
            super.characters(ch, start, length);
        }
    }


    // --------------------------------------------------------------------------------


    private static class NoCharsVocabularyGenerator extends VocabularyGenerator {

        public NoCharsVocabularyGenerator(final SerializerVocabulary serializerVocabulary,
                                          final ParserVocabulary parserVocabulary) {
            super(serializerVocabulary, parserVocabulary);
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
            // Don't add char data to the vocab as it will make the vocab huge and we already de-dup
            // ref data values, which will achieve the same as indexing char data.
        }
    }


    // --------------------------------------------------------------------------------


    private record Vocabs(Vocabulary vocabulary,
                          SerializerVocabulary serializerVocabulary,
                          ParserVocabulary parserVocabulary) {

    }
}
