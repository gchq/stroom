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

package stroom.pipeline.xml.converter.ds3;

import stroom.pipeline.xml.converter.ds3.NodeFactory.NodeType;
import stroom.pipeline.xml.converter.ds3.ref.VarMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Regex extends Expression implements ExecutionProfiler, Match {

    private static final Logger LOGGER = LoggerFactory.getLogger(Regex.class);

    private final Pattern pattern;
    private Matcher matcher;

    private long totalExecutionTime = 0;
    private long totalExecutionCount = 0;
    private long execStartTime = -1;

    Regex(final VarMap varMap, final RegexFactory factory) {
        super(varMap, factory);
        pattern = factory.getPattern();
    }

    @Override
    public void setInput(final CharSequence cs) {
        if (LOGGER.isDebugEnabled()) {
            final long startTime = System.currentTimeMillis();
            matcher = pattern.matcher(cs);
            totalExecutionTime += System.currentTimeMillis() - startTime;
        } else {
            matcher = pattern.matcher(cs);
        }
    }

    @Override
    public Match match() {
        if (LOGGER.isDebugEnabled()) {
            totalExecutionCount++;
            execStartTime = System.currentTimeMillis();
            if (findNextMatch()) {
                totalExecutionTime += System.currentTimeMillis() - execStartTime;
                execStartTime = -1;
                return this;
            } else {
                totalExecutionTime += System.currentTimeMillis() - execStartTime;
                execStartTime = -1;
            }
        } else {
            if (findNextMatch()) {
                return this;
            }
        }

        return null;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.REGEX;
    }

    @Override
    public String getExecutionString() {
        return getNodeType().getName() + "'" + pattern.pattern() + "'";
    }

    @Override
    public long getTotalExecutionCount() {
        return totalExecutionCount;
    }

    @Override
    public long getTotalExecutionTime() {
        // If we are still mid execution then add the current execution time on.
        if (execStartTime != -1) {
            return totalExecutionTime + (System.currentTimeMillis() - execStartTime);
        }

        return totalExecutionTime;
    }

    @Override
    public int start() {
        return matcher.start();
    }

    @Override
    public int start(final int group) {
        return matcher.start(group);
    }

    @Override
    public int end() {
        return matcher.end();
    }

    @Override
    public int end(final int group) {
        return matcher.end(group);
    }

    @Override
    public Buffer filter(final Buffer buffer, final int group) {
        int start = matcher.start(group);
        int end = matcher.end(group);
        int len = end - start;

        // Sometimes a match gives us -1 as the start and end. This happens if
        // we have a group that is part of an optional statement, i.e. uses a |
        // between expressions that hasn't been matched as the other option has
        // been used.
        if (start < 0 || end < 0) {
            start = 0;
            end = 0;
            len = 0;
        }

        if (start == 0 && len == buffer.length()) {
            return buffer.unsafeCopy();
        } else {
            return buffer.subSequence(start, len);
        }
    }

    private boolean findNextMatch() {
        try {
            return matcher.find(0);
        } catch (final StackOverflowError soe) {
            throw new ComplexRegexException(matcher);
        }
    }
}
