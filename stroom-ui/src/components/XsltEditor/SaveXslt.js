import React from 'react';
import PropTypes from 'prop-types';
import { Button, Popup } from 'semantic-ui-react';

const SaveXslt = ({ xslt: { isSaving, isDirty }, saveXslt, xsltId }) => (
  <Popup
    trigger={
      <Button
        floated="right"
        circular
        icon="save"
        color={isDirty ? 'blue' : undefined}
        loading={isSaving}
        onClick={() => {
          if (xsltId) saveXslt(xsltId);
        }}
      />
    }
    content={isDirty ? 'Save changes' : 'Changes saved'}
  />
);

SaveXslt.propTypes = {
  xsltId: PropTypes.string.isRequired,
};

export default SaveXslt;
