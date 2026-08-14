/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.xml;

import org.xml.sax.XMLReader;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import javax.xml.parsers.SAXParserFactory;

/**
 * A pool of {@link XMLReader}s created by {@link SAXParserFactoryFactory}, i.e. with DOCTYPE declarations and
 * external entities disabled.
 * <p>
 * Readers are not thread safe but can be reused for one parse after another, so borrowing one from a pool avoids
 * the cost of creating a reader for every document where documents are parsed in a tight loop, e.g. once per search
 * result row. Where a document is only parsed once per stream or pipeline there is nothing to gain from a pool.
 * </p>
 * <p>
 * Note that readers keep the features they were created with, so like the parser factories held by other classes
 * a pool won't pick up any subsequent change to {@link SAXParserSettings}.
 * </p>
 */
public class XMLReaderPool {

    private static final int DEFAULT_MAX_SIZE = Math.max(10, Runtime.getRuntime().availableProcessors() * 2);
    private static final XMLReaderPool DEFAULT = new XMLReaderPool(DEFAULT_MAX_SIZE);

    // The factory is only configured on creation so it is safe to share it between threads.
    private final SAXParserFactory saxParserFactory = SAXParserFactoryFactory.newInstance();
    private final Queue<XMLReader> pool;

    public XMLReaderPool(final int maxSize) {
        pool = new ArrayBlockingQueue<>(maxSize);
    }

    /**
     * @return A pool shared by all callers that have no need of their own.
     */
    public static XMLReaderPool getDefault() {
        return DEFAULT;
    }

    /**
     * Borrows a reader for the duration of the call, creating one if the pool is empty. The reader is only returned
     * to the pool if the function completes normally as we don't know what state a reader that failed has been left
     * in. If the pool is full when the reader is returned then it is simply discarded, so a burst of concurrent use
     * can't make the pool grow.
     *
     * @param function The function to perform with the borrowed reader. The reader must not be retained beyond the
     *                 life of the call.
     * @return The result of the function.
     */
    public <R> R use(final XMLReaderFunction<R> function) throws Exception {
        XMLReader reader = pool.poll();
        if (reader == null) {
            reader = saxParserFactory.newSAXParser().getXMLReader();
        }
        final R result = function.apply(reader);
        pool.offer(reader);
        return result;
    }

    /**
     * @return The number of readers currently available to borrow.
     */
    public int size() {
        return pool.size();
    }

    /**
     * Discards all the readers currently in the pool.
     */
    public void clear() {
        pool.clear();
    }

    @Override
    public String toString() {
        return "XMLReaderPool{size=" + size() + "}";
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    public interface XMLReaderFunction<R> {

        R apply(XMLReader reader) throws Exception;
    }
}
