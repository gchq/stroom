import React from 'react';
import PropTypes from 'prop-types';
import { Grid } from 'semantic-ui-react';

const WithHeader = ({ docRefUuid, header, actionBarItems, content }) => (
  <React.Fragment>
    <Grid className="content-tabs__grid">
      <Grid.Column width={12}>{header}</Grid.Column>
      {actionBarItems && <Grid.Column width={4}>{actionBarItems}</Grid.Column>}
    </Grid>
    {content}
  </React.Fragment>
);

WithHeader.propTypes = {
  header: PropTypes.object,
  docRefUuid: PropTypes.string,
  content: PropTypes.object.isRequired,
};

export default WithHeader;
