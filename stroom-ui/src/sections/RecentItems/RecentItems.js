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
import { compose } from 'recompose';
import { connect } from 'react-redux';
import { Header, Icon, Grid } from 'semantic-ui-react';

import AppSearchBar from 'components/AppSearchBar';
import { RawDocRefListingEntry } from 'components/DocRefListing';
import withOpenDocRef from './withOpenDocRef';
import withSelectableItemListing from 'lib/withSelectableItemListing';

const LISTING_ID = 'recent-items';

const enhance = compose(
  withOpenDocRef,
  withSelectableItemListing(({ openDocRef }) => ({
    listingId: LISTING_ID,
    openItem: openDocRef,
  })),
  connect(
    ({ recentItems }, props) => ({
      recentItems,
    }),
    {},
  ),
);

const RecentItems = ({ recentItems, openDocRef }) => (
  <React.Fragment>
    <Grid className="content-tabs__grid">
      <Grid.Column width={16}>
        <AppSearchBar />
      </Grid.Column>
      <Grid.Column width={16}>
        <Header as="h3">
          <Icon name="file outline" />
          <Header.Content>Recent Items</Header.Content>
        </Header>
      </Grid.Column>
    </Grid>
    <div className="doc-ref-listing">
      {recentItems.map((docRef, index) => (
        <RawDocRefListingEntry
          key={docRef.uuid}
          index={index}
          listingId={LISTING_ID}
          docRef={docRef}
          includeBreadcrumb
          openDocRef={openDocRef}
        />
      ))}
    </div>
  </React.Fragment>
);

export default enhance(RecentItems);
