import React from 'react';
import PropTypes from 'prop-types';
import { Header } from 'semantic-ui-react';
import { compose } from 'recompose';

import withPipeline from './withPipeline';

const enhance = compose(withPipeline());

const HeaderContent = ({
  pipeline: {
    pipeline: {
      docRef: { name },
      description,
    },
  },
}) => (
  <Header.Content>
    {name}
    <Header.Subheader>{description}</Header.Subheader>
  </Header.Content>
);

HeaderContent.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(HeaderContent);
