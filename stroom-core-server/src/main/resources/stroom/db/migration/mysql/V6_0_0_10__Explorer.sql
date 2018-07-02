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

CREATE TABLE explorerTreeNode (
  id 				int(11) NOT NULL AUTO_INCREMENT,
  type 				varchar(255) NOT NULL,
  uuid 				varchar(255) NOT NULL,
  name				varchar(255) NOT NULL,
  tags				varchar(255) DEFAULT NULL,
  PRIMARY KEY       (id),
  UNIQUE 			(type, uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE explorerTreePath (
  ancestor 			int(11) NOT NULL,
  descendant 		int(11) NOT NULL,
  depth 			int(11) NOT NULL,
  orderIndex		int(11) NOT NULL,
  PRIMARY KEY       (ancestor, descendant)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;