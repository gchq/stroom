import React from 'react';
import PropTypes from 'prop-types';
import { Grid } from 'semantic-ui-react';

const WithHeader = ({ header, actionBarItems, content }) => (
  <React.Fragment>
    <Grid className="content-tabs__grid">
      <Grid.Column width={8}>{header}</Grid.Column>
      {actionBarItems && <Grid.Column width={8}>{actionBarItems}</Grid.Column>}
    </Grid>
    {content}
  </React.Fragment>
);

WithHeader.propTypes = {
  header: PropTypes.object.isRequired,
  content: PropTypes.object.isRequired,
};

export default WithHeader;
