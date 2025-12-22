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

package stroom.data.zip;

class StroomZipNameException extends RuntimeException {

    private StroomZipNameException(final String msg) {
        super(msg);
    }

    static StroomZipNameException createDuplicateFileNameException(final String fileName) {
        return new StroomZipNameException("Duplicate File " + fileName);
    }

    static StroomZipNameException createOutOfOrderException(final String fileName) {
        return new StroomZipNameException("File Name is out of order " + fileName);
    }
}
