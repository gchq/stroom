import React from 'react';
import { compose } from 'recompose';
import { connect } from 'react-redux';
import { withRouter } from 'react-router-dom';

import DocRefPropType from 'lib/DocRefPropType';
import { DocRefBreadcrumb } from 'components/DocRefBreadcrumb';
import openDocRef from 'prototypes/RecentItems/openDocRef';

const enhance = compose(withRouter, connect((state, props) => ({}), { openDocRef }));

const DocRefListingEntry = ({
  docRef, history, selectedDocRef, openDocRef,
}) => (
  <div
    key={docRef.uuid}
    className={`doc-ref-listing__item ${
      selectedDocRef && selectedDocRef.uuid === docRef.uuid ? 'doc-ref-listing__item--selected' : ''
    }`}
  >
    <div>
      <img
        className="doc-ref__icon-large"
        alt="X"
        src={require(`../../images/docRefTypes/${docRef.type}.svg`)}
      />
      <span
        className="doc-ref-listing__name"
        onClick={() => {
          openDocRef(history, docRef);
        }}
      >
        {docRef.name}
      </span>
    </div>

    <DocRefBreadcrumb docRefUuid={docRef.uuid} />
  </div>
);

const EnhancedDocRefListingEntry = enhance(DocRefListingEntry);

EnhancedDocRefListingEntry.propTypes = {
  selectedDocRef: DocRefPropType,
  docRef: DocRefPropType.isRequired,
};

export default EnhancedDocRefListingEntry;
