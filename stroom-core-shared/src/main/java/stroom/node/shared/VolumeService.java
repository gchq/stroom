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

package stroom.node.shared;

import stroom.entity.shared.Clearable;
import stroom.entity.shared.BaseEntityService;
import stroom.entity.shared.FindService;
import stroom.entity.shared.Flushable;

import java.util.Set;

/**
 * API for handling volumes.
 */
public interface VolumeService extends BaseEntityService<Volume>, FindService<Volume, FindVolumeCriteria>, Flushable, Clearable {
	/**
	 * Given a node return back where we need to write to.
	 *
	 * @param node
	 *            The local node required if we prefer to use local volumes.
	 * @return set of volumes to write to
	 */
	Set<Volume> getStreamVolumeSet(Node node);

	/**
	 * Get a list of volumes that can support indexes. The order will always be the same.
	 *
	 * @param node
	 *            The owner node for the index shard that we are getting a volume for.
	 * @param allowedVolumes
	 *            A set of volumes that can be used to filter the set of possible returned volumes.
	 * @return list of matches
	 */
	Set<Volume> getIndexVolumeSet(Node node, Set<Volume> allowedVolumes);
}
