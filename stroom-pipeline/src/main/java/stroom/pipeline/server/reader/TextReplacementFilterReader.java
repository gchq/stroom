/*
 * Copyright 2018 Crown Copyright
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

package stroom.pipeline.server.reader;

import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.util.shared.Severity;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextReplacementFilterReader extends FilterReader {
    private static final int MIN_SIZE = 20000;

    private final Pattern pattern;
    private final String replacement;
    private final Integer maxReplacements;
    private final int bufferSize;
    private final ErrorReceiver errorReceiver;
    private final String elementId;

    private int replacementCount;
    private final BufferedReader inBuffer;
    private StringBuffer outBuffer = new StringBuffer();

    private TextReplacementFilterReader(final Reader reader,
                                        final String regex,
                                        final String replacement,
                                        final Integer maxReplacements,
                                        final boolean literal,
                                        final boolean dotAll,
                                        final int bufferSize,
                                        final ErrorReceiver errorReceiver,
                                        final String elementId) {
        super(reader);
        this.bufferSize = Math.max(MIN_SIZE, bufferSize);
        this.errorReceiver = errorReceiver;
        this.elementId = elementId;

        final int doubleBufferSize = 2 * this.bufferSize;
        inBuffer = new BufferedReader(reader, MIN_SIZE, doubleBufferSize);

        if (regex == null) {
            this.pattern = null;
            this.replacement = null;
            this.maxReplacements = 0;

        } else {
            this.maxReplacements = maxReplacements;

            if (literal) {
                this.pattern = Pattern.compile(regex, Pattern.LITERAL);
                this.replacement = Matcher.quoteReplacement(replacement);
            } else {
                if (dotAll) {
                    this.pattern = Pattern.compile(regex, Pattern.DOTALL);
                } else {
                    this.pattern = Pattern.compile(regex);
                }
                this.replacement = replacement;
            }
        }
    }

    /**
     * Read text into the input buffer, replace the first match if possible and copy replaced text to the output buffer.
     */
    private void performReplacement() throws IOException {
        inBuffer.fillBuffer();
        final Matcher matcher = pattern.matcher(inBuffer);
        if (matcher.find()) {
            replacementCount++;

            if (matcher.end() == inBuffer.length()) {
                error("The pattern matched all text in the buffer. Consider changing your match expression or making the buffer bigger");
            }

            matcher.appendReplacement(outBuffer, replacement);
            inBuffer.move(matcher.end());

        } else if (inBuffer.isEof() || inBuffer.length() < bufferSize) {
            // Copy all remaining text to the output buffer.
            copyBuffer(inBuffer.length());
        } else {
            // Copy the current buffer text up to a maximum length of buffer size.
            copyBuffer(Math.min(inBuffer.length(), bufferSize));
        }
    }

    private void copyBuffer(int length) {
        if (length > 0) {
            final CharSequence charSequence = inBuffer.subSequence(0, length);
            outBuffer.append(charSequence, 0, charSequence.length());
            inBuffer.move(charSequence.length());
        }
    }

    @Override
    public int read(final char[] cbuf, final int offset, final int length) throws IOException {
        if (outBuffer.length() == 0) {
            if (maxReplacements != null && replacementCount == maxReplacements) {
                // Put any remaining text from the input buffer into the output buffer.
                if (inBuffer.length() > 0) {
                    copyBuffer(inBuffer.length());

                } else {
                    // There is no more text to be replaced so pass through.
                    return super.read(cbuf, offset, length);
                }
            } else {
                // Read text into the input buffer, replace the first match if possible and copy replaced text to the output buffer.
                performReplacement();
            }
        }

        // Copy text from the output buffer to the supplied char array up to the requested length or length of the output buffer.
        final int len = Math.min(length, outBuffer.length());
        outBuffer.getChars(0, len, cbuf, offset);

        // Do we have anything left in the output buffer?
        if (outBuffer.length() == len) {
            // If there was nothing left in the output buffer, we didn't return any content and we are EOF then return -1.
            if (len == 0 && inBuffer.isEof()) {
                return -1;
            }

            outBuffer.setLength(0);
        } else {
            // If we didn't read all of the text from the output buffer then change the output buffer so that it contains the remaining text.
            outBuffer = new StringBuffer(outBuffer.substring(len));
        }

        return len;
    }

    @Override
    public int read() throws IOException {
        final char[] buffer = new char[1];
        final int len = read(buffer, 0, 1);
        if (len == -1) {
            return len;
        }
        return buffer[0];
    }

    int getReplacementCount() {
        return replacementCount;
    }

    private void error(final String error) {
        if (errorReceiver != null) {
            errorReceiver.log(Severity.ERROR, null, elementId, error, null);
        }
    }

    static class Builder {
        private Reader reader;
        private String regex;
        private String replacement = "";
        private Integer maxReplacements;
        private boolean literal;
        private boolean dotAll;
        private int bufferSize;
        private ErrorReceiver errorReceiver;
        private String elementId;

        public Builder reader(final Reader reader) {
            this.reader = reader;
            return this;
        }

        public Builder regex(final String regex) {
            this.regex = regex;
            return this;
        }

        public Builder replacement(final String replacement) {
            if (replacement != null) {
                this.replacement = replacement;
            }
            return this;
        }

        public Builder maxReplacements(final Integer maxReplacements) {
            this.maxReplacements = maxReplacements;
            return this;
        }

        public Builder literal(final boolean literal) {
            this.literal = literal;
            return this;
        }

        public Builder dotAll(final boolean dotAll) {
            this.dotAll = dotAll;
            return this;
        }

        public Builder bufferSize(final int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        public Builder errorReceiver(final ErrorReceiver errorReceiver) {
            this.errorReceiver = errorReceiver;
            return this;
        }

        public Builder elementId(final String elementId) {
            this.elementId = elementId;
            return this;
        }

        public TextReplacementFilterReader build() {
            return new TextReplacementFilterReader(reader, regex, replacement, maxReplacements, literal, dotAll, bufferSize, errorReceiver, elementId);
        }
    }
}
