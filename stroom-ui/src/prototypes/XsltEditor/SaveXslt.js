import React from 'react';
import PropTypes from 'prop-types';
import { compose } from 'recompose';

import withXslt from './withXslt';
import ActionBarItem from 'sections/AppChrome/ActionBarItem';
import { saveXslt } from './xsltResourceClient';

const enhance = compose(withXslt({ saveXslt }));

const SaveXslt = ({ xslt: { isSaving, isDirty }, saveXslt, xsltId }) => (
  <ActionBarItem
    buttonProps={{ icon: 'save', color: isDirty ? 'blue' : undefined, loading: isSaving }}
    content={isDirty ? 'Save changes' : 'Changes saved'}
    onClick={() => {
      if (isDirty) saveXslt(xsltId);
    }}
  />
);

SaveXslt.propTypes = {
  xsltId: PropTypes.string.isRequired,
};

export default enhance(SaveXslt);
