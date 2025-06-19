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

package stroom.index.lucene553.analyser;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.Reader;

/**
 * Used by DocumentsWriter to implemented a StringReader that can be reset to a
 * new string; we use this when tokenizing the string value from a Field.
 */
final class ReusableStringReader extends Reader {

    int upto;
    int left;
    String s;

    void init(final String s) {
        this.s = s;
        left = s.length();
        this.upto = 0;
    }

    @Override
    public int read(final char[] c) {
        return read(c, 0, c.length);
    }

    @Override
    public int read(final char[] c, final int off, final int len) {
        if (left > len) {
            s.getChars(upto, upto + len, c, off);
            upto += len;
            left -= len;
            return len;
        } else if (0 == left) {
            // don't keep a reference (s could have been very large)
            s = null;
            return -1;
        } else {
            s.getChars(upto, upto + left, c, off);
            final int r = left;
            left = 0;
            upto = s.length();
            return r;
        }
    }

    @Override
    public void close() {
    }
}
