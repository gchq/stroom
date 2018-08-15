import React from 'react';
import PropTypes from 'prop-types';
import { compose, branch, renderNothing } from 'recompose';
import { connect } from 'react-redux';
import { Popup, Button } from 'semantic-ui-react/dist/commonjs';

import { findItem } from 'lib/treeUtils';
import { DocRefBreadcrumb } from 'components/DocRefBreadcrumb';
import ActionBarItemsPropType from './ActionBarItemsPropType';
import { actionCreators } from './redux';

const { docRefSelectionToggled } = actionCreators;

const enhance = compose(
  connect(
    (
      {
        docExplorer: { documentTree },
        docRefListing,
      },
      { listingId, docRefUuid },
    ) => ({
      docRefListing: docRefListing[listingId],
      docRefWithLineage: findItem(documentTree, docRefUuid),
    }),
    {
      docRefSelectionToggled,
    },
  ),
  branch(({ docRefWithLineage: { node } }) => !node, renderNothing),
);

const DocRefListingEntry = ({
  docRefWithLineage: { node },
  listingId,
  docRefListing: { selectedDocRefUuids, inMultiSelectMode },
  onNameClick,
  actionBarItems,
  includeBreadcrumb,
  docRefSelectionToggled,
  openDocRef
}) => (
  <div
    key={node.uuid}
    className={`doc-ref-listing__item ${
      selectedDocRefUuids.includes(node.uuid) ? 'doc-ref-listing__item--selected' : ''
    }`}
    onClick={() => docRefSelectionToggled(listingId, node.uuid)}
  >
    <div>
      <img
        className="stroom-icon--large"
        alt="X"
        src={require(`../../images/docRefTypes/${node.type}.svg`)}
      />
      <span
        className="doc-ref-listing__name"
        onClick={e => {
          onNameClick(node);
          e.stopPropagation();
        }}
      >
        {node.name}
      </span>
      <span className="doc-ref-listing-entry__action-bar">
        {!inMultiSelectMode &&
          actionBarItems.map(({ onClick, icon, tooltip, disabled }, i) => (
            <Popup
              key={i}
              trigger={<Button className='action-bar__button' circular onClick={() => onClick(node)} icon={icon} disabled={disabled} />}
              content={tooltip}
            />
          ))}
        {inMultiSelectMode && <Button className='action-bar__button' circular icon="dont" disabled />}
      </span>
    </div>

    {includeBreadcrumb && <DocRefBreadcrumb docRefUuid={node.uuid} openDocRef={openDocRef} />}
  </div>
);

const EnhancedDocRefListingEntry = enhance(DocRefListingEntry);

EnhancedDocRefListingEntry.propTypes = {
  listingId: PropTypes.string.isRequired,
  docRefUuid: PropTypes.string.isRequired,
  actionBarItems: ActionBarItemsPropType.isRequired,
  includeBreadcrumb: PropTypes.bool.isRequired,
  onNameClick: PropTypes.func.isRequired,
  openDocRef: PropTypes.func.isRequired
};

EnhancedDocRefListingEntry.defaultProps = {
  actionBarItems: [],
  checkedDocRefs: [],
  includeBreadcrumb: true,
};

export default EnhancedDocRefListingEntry;
