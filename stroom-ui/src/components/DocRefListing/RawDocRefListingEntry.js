import React from 'react';
import PropTypes from 'prop-types';

import DocRefPropType from 'lib/DocRefPropType';
import { DocRefBreadcrumb } from 'components/DocRefBreadcrumb';

const RawDocRefListingEntry = ({
  className,
  node,
  openDocRef,
  onRowClick,
  onNameClick,
  includeBreadcrumb,
}) => (
  <div
    key={node.uuid}
    className="doc-ref-listing__item"
    onClick={(e) => {
      onRowClick();
      e.preventDefault();
    }}
  >
    <div>
      <img
        className="stroom-icon--large"
        alt="X"
        src={require(`../../images/docRefTypes/${node.type}.svg`)}
      />
      <span
        className="doc-ref-listing__name"
        onClick={(e) => {
          onNameClick();
          e.stopPropagation();
          e.preventDefault();
        }}
      >
        {node.name}
      </span>
    </div>

    {includeBreadcrumb && <DocRefBreadcrumb docRefUuid={node.uuid} openDocRef={openDocRef} />}
  </div>
);

RawDocRefListingEntry.propTypes = {
  className: PropTypes.string.isRequired,
  node: DocRefPropType,
  openDocRef: PropTypes.func.isRequired,
  onRowClick: PropTypes.func.isRequired,
  onNameClick: PropTypes.func.isRequired,
};

RawDocRefListingEntry.defaultProps = {
  className: 'doc-ref-listing__item',
  includeBreadcrumb: true,
};

export default RawDocRefListingEntry;
