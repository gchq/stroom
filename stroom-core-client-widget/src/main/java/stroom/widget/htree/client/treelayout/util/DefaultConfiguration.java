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

/*
 * [The "BSD license"]
 * Copyright (c) 2011, abego Software GmbH, Germany (http://www.abego.org)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of the abego Software GmbH nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package stroom.widget.htree.client.treelayout.util;

import stroom.widget.htree.client.treelayout.Configuration;

import static stroom.widget.htree.client.treelayout.internal.util.Contract.checkArg;

/**
 * Specify a {@link Configuration} through configurable parameters, or falling
 * back to some frequently used defaults.
 *
 * @author Udo Borkowski (ub@abego.org)
 */
public class DefaultConfiguration<T_TREE_NODE> implements Configuration<T_TREE_NODE> {

    private final double gapBetweenLevels;
    private final double gapBetweenNodes;
    private final Location location;

    // -----------------------------------------------------------------------
    // gapBetweenLevels
    private final AlignmentInLevel alignmentInLevel;

    /**
     * Specifies the constants to be used for this Configuration.
     *
     * @param gapBetweenLevels
     * @param gapBetweenNodes
     * @param location         [default:
     *                         {@link stroom.widget.htree.client.treelayout.Configuration.Location#Top
     *                         Top}]
     * @param alignmentInLevel [default:
     *                         {@link stroom.widget.htree.client.treelayout.Configuration.AlignmentInLevel#Center
     *                         Center}]
     */
    public DefaultConfiguration(final double gapBetweenLevels, final double gapBetweenNodes, final Location location,
                                final AlignmentInLevel alignmentInLevel) {
        checkArg(gapBetweenLevels >= 0, "gapBetweenLevels must be >= 0");
        checkArg(gapBetweenNodes >= 0, "gapBetweenNodes must be >= 0");

        this.gapBetweenLevels = gapBetweenLevels;
        this.gapBetweenNodes = gapBetweenNodes;
        this.location = location;
        this.alignmentInLevel = alignmentInLevel;
    }

    // -----------------------------------------------------------------------
    // gapBetweenNodes

    /**
     * Convenience constructor, using a default for the alignmentInLevel.
     * <p>
     * see
     * {@link #DefaultConfiguration(
     *double,
     * double,
     * stroom.widget.htree.client.treelayout.Configuration.Location,
     * stroom.widget.htree.client.treelayout.Configuration.AlignmentInLevel)}
     */
    public DefaultConfiguration(final double gapBetweenLevels, final double gapBetweenNodes, final Location location) {
        this(gapBetweenLevels, gapBetweenNodes, location, AlignmentInLevel.Center);
    }

    /**
     * Convenience constructor, using a default for the rootLocation and the
     * alignmentInLevel.
     * <p>
     * see
     * {@link #DefaultConfiguration(
     *double,
     * double,
     * stroom.widget.htree.client.treelayout.Configuration.Location,
     * stroom.widget.htree.client.treelayout.Configuration.AlignmentInLevel)}
     */
    public DefaultConfiguration(final double gapBetweenLevels, final double gapBetweenNodes) {
        this(gapBetweenLevels, gapBetweenNodes, Location.Top, AlignmentInLevel.Center);
    }

    // -----------------------------------------------------------------------
    // location

    @Override
    public double getGapBetweenLevels(final int nextLevel) {
        return gapBetweenLevels;
    }

    @Override
    public double getGapBetweenNodes(final T_TREE_NODE node1, final T_TREE_NODE node2) {
        return gapBetweenNodes;
    }

    // -----------------------------------------------------------------------
    // alignmentInLevel

    @Override
    public Location getRootLocation() {
        return location;
    }

    @Override
    public AlignmentInLevel getAlignmentInLevel() {
        return alignmentInLevel;
    }

}
