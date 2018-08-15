/*
 * Copyright 2018 Crown Copyright
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

import React from 'react';
import { connect } from 'react-redux';

import { iterateNodes } from 'lib/treeUtils';
import { DocRefListingWithRouter } from 'components/DocRefListing';

const enhance = connect(({ docExplorer: { documentTree } }, props) => {
  const allDocuments = [];

  iterateNodes(documentTree, (lineage, node) => {
    allDocuments.push({
      name: node.name,
      type: node.type,
      uuid: node.uuid,
      lineage,
      lineageNames: lineage.reduce((acc, curr) => `${acc} ${curr.name}`, ''),
    });
  });

  return {
    allDocuments,
  };
}, {});

const AppSearch = ({ allDocuments }) => (
  <DocRefListingWithRouter
    listingId="app-search"
    maxResults={5}
    icon="search"
    title="Search"
    allDocRefs={allDocuments}
  />
);

export default enhance(AppSearch);
