import React from 'react';
import PropTypes from 'prop-types';

import SaveXslt from './SaveXslt';

const ActionBarItems = ({ xsltId }) => (
  <React.Fragment>
    <SaveXslt xsltId={xsltId} />
  </React.Fragment>
);

ActionBarItems.propTypes = {
  xsltId: PropTypes.string.isRequired
}

export default ActionBarItems;