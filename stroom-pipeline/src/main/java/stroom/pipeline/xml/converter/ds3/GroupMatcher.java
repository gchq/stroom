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

import java.util.regex.MatchResult;

public class GroupMatcher implements MatchResult {
    private CharSequence cs;
    private boolean reset;

    public void reset(final CharSequence cs) {
        this.cs = cs;
        reset = true;
    }

    public boolean find() {
        if (reset) {
            reset = false;
            return true;
        }

        return false;
    }

    @Override
    public int end() {
        return cs.length();
    }

    @Override
    public int end(final int group) {
        return cs.length();
    }

    @Override
    public String group() {
        return cs.toString();
    }

    @Override
    public String group(final int group) {
        return cs.toString();
    }

    @Override
    public int groupCount() {
        return 0;
    }

    @Override
    public int start() {
        return 0;
    }

    @Override
    public int start(final int group) {
        return 0;
    }
}
