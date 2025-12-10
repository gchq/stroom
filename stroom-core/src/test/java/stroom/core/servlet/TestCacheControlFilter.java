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

package stroom.core.servlet;


import org.junit.jupiter.api.Test;

class TestCacheControlFilter {

    @Test
    void testExpires() {
        // Add an expiry time, e.g. Expires: Wed, 21 Oct 2015 07:28:00 GMT
        final CacheControlFilter filter = new CacheControlFilter();
        System.out.println(filter.getExpires(600));
    }
}
