import React from 'react';
import PropTypes from 'prop-types';
import { compose, branch, renderNothing } from 'recompose';
import { connect } from 'react-redux';
import { withRouter } from 'react-router-dom';
import { Popup, Button, Checkbox } from 'semantic-ui-react';

import { findItem } from 'lib/treeUtils';
import { DocRefBreadcrumb } from 'components/DocRefBreadcrumb';
import openDocRef from 'prototypes/RecentItems/openDocRef';
import ActionBarItemsPropType from './ActionBarItemsPropType';
import { actionCreators } from './redux';

const { docRefCheckToggled } = actionCreators;

const enhance = compose(
  withRouter,
  connect(
    (
      {
        docExplorer: {
          explorerTree: { documentTree },
        },
        docRefListing,
      },
      { listingId, docRefUuid },
    ) => ({
      docRefListing: docRefListing[listingId],
      docRefWithLineage: findItem(documentTree, docRefUuid),
    }),
    {
      openDocRef,
      docRefCheckToggled,
    },
  ),
  branch(({ docRefWithLineage: { node } }) => !node, renderNothing),
);

const DocRefListingEntry = ({
  docRefWithLineage: { node },
  history,
  listingId,
  docRefListing: { selectedDocRef, checkedDocRefUuids, inMultiSelectMode },
  openDocRef,
  actionBarItems,
  includeBreadcrumb,
  docRefCheckToggled,
}) => (
  <div
    key={node.uuid}
    className={`doc-ref-listing__item ${
      selectedDocRef && selectedDocRef.uuid === node.uuid ? 'doc-ref-listing__item--selected' : ''
    }`}
  >
    <div>
      {inMultiSelectMode && (
        <Checkbox
          checked={checkedDocRefUuids.includes(node.uuid)}
          onChange={() => docRefCheckToggled(listingId, node.uuid)}
        />
      )}
      <img
        className="doc-ref__icon-large"
        alt="X"
        src={require(`../../images/docRefTypes/${node.type}.svg`)}
      />
      <span
        className="doc-ref-listing__name"
        onClick={() => {
          openDocRef(history, node);
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

    {includeBreadcrumb && <DocRefBreadcrumb docRefUuid={node.uuid} />}
  </div>
);

const EnhancedDocRefListingEntry = enhance(DocRefListingEntry);

EnhancedDocRefListingEntry.propTypes = {
  listingId: PropTypes.string.isRequired,
  docRefUuid: PropTypes.string.isRequired,
  actionBarItems: ActionBarItemsPropType.isRequired,
  includeBreadcrumb: PropTypes.bool.isRequired,
};

EnhancedDocRefListingEntry.defaultProps = {
  actionBarItems: [],
  checkedDocRefs: [],
  includeBreadcrumb: true,
};

export default EnhancedDocRefListingEntry;
