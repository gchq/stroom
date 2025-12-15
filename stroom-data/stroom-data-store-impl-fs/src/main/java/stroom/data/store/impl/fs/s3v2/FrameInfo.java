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

package stroom.data.store.impl.fs.s3v2;


/**
 * Used for recording details of compressed frames that have been written.
 *
 * @param frameIdx                 The index of the compressed frame in the file/stream. Zero based.
 *                                 I.e. a file containing 10 frames will have frameIdx values of 0-9.
 *                                 <strong>Not</strong> its byte position.
 * @param cumulativeCompressedSize The cumulative size of all compressed frames up to and including the end of
 *                                 the frame with the passed frameIdx.
 * @param uncompressedSize         The uncompressed size of the frame in bytes.
 */
public record FrameInfo(int frameIdx,
                        long cumulativeCompressedSize,
                        long uncompressedSize) {

}
