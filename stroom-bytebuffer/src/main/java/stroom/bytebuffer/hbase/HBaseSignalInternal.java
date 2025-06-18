/*
 * Copy of https://github.com/apache/hbase-thirdparty/blob/master/hbase-unsafe/src/main/java/org/apache/hadoop/hbase/unsafe/HBaseSignalInternal.java
 * to avoid having to pull in all of hbase to use the util methods.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.bytebuffer.hbase;

import sun.misc.Signal;

import java.util.function.BiConsumer;

/**
 * Delegation of {@code sun.misc.Signal}.
 */
@SuppressWarnings("restriction")
public final class HBaseSignalInternal {

    private HBaseSignalInternal() {
    }

    public static void handle(final String signal, final BiConsumer<Integer, String> handler) {
        Signal.handle(new Signal(signal), s -> handler.accept(s.getNumber(), s.getName()));
    }
}
