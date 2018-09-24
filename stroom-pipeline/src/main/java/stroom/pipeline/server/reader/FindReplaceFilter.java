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

public class FindReplaceFilter extends FilterReader {
    private static final int MIN_SIZE = 1000;

    private final Pattern pattern;
    private final String replacement;
    private final int maxReplacements;
    private final int bufferSize;
    private final ErrorReceiver errorReceiver;
    private final String elementId;

    private boolean firstPass = true;
    private int replacementCount;
    private final BufferedReader inBuffer;
    private final OutBuffer outBuffer = new OutBuffer();

    private FindReplaceFilter(final Reader reader,
                              final String find,
                              final String replacement,
                              final Integer maxReplacements,
                              final boolean regex,
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

        if (find == null) {
            this.pattern = null;
            this.replacement = null;
            this.maxReplacements = 0;

        } else {
            this.maxReplacements = maxReplacements;

            if (regex) {
                if (dotAll) {
                    this.pattern = Pattern.compile(find, Pattern.DOTALL);
                } else {
                    this.pattern = Pattern.compile(find);
                }
                this.replacement = replacement;
            } else {
                this.pattern = Pattern.compile(find, Pattern.LITERAL);
                this.replacement = Matcher.quoteReplacement(replacement);
            }
        }
    }

    /**
     * Read text into the input buffer, replace the first match if possible and copy replaced text to the output buffer.
     */
    private void performReplacement() throws IOException {
        inBuffer.fillBuffer();

        boolean doneReplacement = false;
        final Matcher matcher = pattern.matcher(inBuffer);
        if (matcher.find()) {
            final boolean matchedBufferStart = matcher.start() == 0 && !firstPass;
            final boolean matchedBufferEnd = matcher.end() == inBuffer.length() && !inBuffer.isEof();

            if (matchedBufferStart && matchedBufferEnd) {
                // If we matched all text in the buffer but aren't actually at the start of the stream or at the end then this is not likely to be correct.
                error("The pattern matched all text in the buffer. Consider changing your match expression or making the buffer bigger.");
            }
            if (matchedBufferStart) {
                // If we matched from the start of the buffer but aren't actually at the start of the text then this is not likely to be correct.
                error("The pattern matched text at the start of the buffer when we are not at the start of the stream. Consider changing your match expression or making the buffer bigger.");
            } else if (matchedBufferEnd) {
                // If we matched to the end of the buffer and we are not at the end of the stream then this is not likely to be correct.
                error("The pattern matched text at the end of the buffer when we are not at the end of the stream. Consider changing your match expression or making the buffer bigger");

            } else {
                matcher.appendReplacement(outBuffer.getStringBuffer(), replacement);
                inBuffer.move(matcher.end());
                doneReplacement = true;
                replacementCount++;
            }
        }

        if (!doneReplacement) {
            // If we didn't manage to replace anything then copy the buffered text to the output.
            if (inBuffer.isEof() || inBuffer.length() < bufferSize) {
                // Copy all remaining text to the output buffer.
                copyBuffer(inBuffer.length());
            } else {
                // Copy the current buffer text up to a maximum length of buffer size.
                copyBuffer(Math.min(inBuffer.length(), bufferSize));
            }
        }

        firstPass = false;
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
            if (replacementCount == maxReplacements) {
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
        int len = Math.min(length, outBuffer.length());
        outBuffer.getChars(0, len, cbuf, offset);

        // If there was nothing left in the output buffer, we didn't return any content and we are EOF then return -1.
        if (len == 0 && inBuffer.isEof()) {
            len = -1;
        } else {
            // Move the offset in the output buffer ready for the next read operation.
            outBuffer.move(len);
        }

        return len;
    }

    @Override
    public int read() throws IOException {
        if (outBuffer.length() == 0) {
            if (replacementCount == maxReplacements) {
                // Put any remaining text from the input buffer into the output buffer.
                if (inBuffer.length() > 0) {
                    copyBuffer(inBuffer.length());

                } else {
                    // There is no more text to be replaced so pass through.
                    return super.read();
                }
            } else {
                // Read text into the input buffer, replace the first match if possible and copy replaced text to the output buffer.
                performReplacement();
            }
        }

        // Copy text from the output buffer to the supplied char array up to the requested length or length of the output buffer.
        final int len = Math.min(1, outBuffer.length());
        int result = -1;
        if (len > 0) {
            result = outBuffer.charAt(0);
            // Move the offset in the output buffer ready for the next read operation.
            outBuffer.move(len);
        }

        return result;
    }

    int getReplacementCount() {
        return replacementCount;
    }

    private void error(final String error) {
        if (errorReceiver != null) {
            errorReceiver.log(Severity.ERROR, null, elementId, error, null);
        }
    }

    private class OutBuffer {
        private StringBuffer stringBuffer = new StringBuffer();
        private int offset;

        void append(final CharSequence s, final int start, final int end) {
            stringBuffer.append(s, start, end);
        }

        void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
            stringBuffer.getChars(srcBegin + offset, srcEnd + offset, dst, dstBegin);
        }

        int charAt(final int index) {
            return stringBuffer.charAt(offset + index);
        }

        int length() {
            return stringBuffer.length() - offset;
        }

        StringBuffer getStringBuffer() {
            return stringBuffer;
        }

        void move(final int len) {
            if (len == length()) {
                stringBuffer.setLength(0);
                offset = 0;
            } else {
                offset += len;
                if (offset > 1000) {
                    stringBuffer.delete(0, offset);
                    offset = 0;
                }
            }
        }

        @Override
        public String toString() {
            return stringBuffer.substring(offset);
        }
    }

    static class Builder {
        private Reader reader;
        private String find;
        private String replacement = "";
        private int maxReplacements = -1;
        private boolean regex;
        private boolean dotAll;
        private int bufferSize;
        private ErrorReceiver errorReceiver;
        private String elementId;

        public Builder reader(final Reader reader) {
            this.reader = reader;
            return this;
        }

        public Builder find(final String find) {
            this.find = find;
            return this;
        }

        public Builder replacement(final String replacement) {
            if (replacement != null) {
                this.replacement = replacement;
            }
            return this;
        }

        public Builder maxReplacements(final int maxReplacements) {
            this.maxReplacements = maxReplacements;
            return this;
        }

        public Builder regex(final boolean regex) {
            this.regex = regex;
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

        public FindReplaceFilter build() {
            return new FindReplaceFilter(reader, find, replacement, maxReplacements, regex, dotAll, bufferSize, errorReceiver, elementId);
        }
    }
}
