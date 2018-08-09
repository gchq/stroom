import React from 'react';
import PropTypes from 'prop-types';
import { compose } from 'recompose';
import { connect } from 'react-redux';
import { withRouter } from 'react-router-dom';

import { DocRefBreadcrumb } from 'components/DocRefBreadcrumb';
import openDocRef from 'prototypes/RecentItems/openDocRef';

const enhance = compose(
  withRouter,
  connect(
    (state, props) => ({}),
    { openDocRef },
  ),
);

const DocRefListing = ({
  docRefs, history, selectedItem, openDocRef,
}) => (
  <div className="doc-ref-listing">
    {docRefs.map((docRef, i, arr) => (
      <div
        key={i}
        className={`doc-ref-listing__item ${
          selectedItem === i ? 'doc-ref-listing__item--selected' : ''
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
    ))}
  </div>
);

const EnhancedDocRefListing = enhance(DocRefListing);

EnhancedDocRefListing.propTypes = {
  selectedItem: PropTypes.number,
  docRefs: PropTypes.arrayOf(PropTypes.shape({
    uuid: PropTypes.string.isRequired,
    type: PropTypes.string.isRequired,
    name: PropTypes.string.isRequired,
  })).isRequired,
};

export default EnhancedDocRefListing;
