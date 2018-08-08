import React from 'react';
import { Grid } from 'semantic-ui-react';

const WithHeader = ({ header, actionBarItems, content }) => (
  <React.Fragment>
    <Grid className="content-tabs__grid">
      <Grid.Column width={8}>{header}</Grid.Column>
      <Grid.Column width={8}>{actionBarItems}</Grid.Column>
    </Grid>
    {content}
  </React.Fragment>
);

export default WithHeader;
