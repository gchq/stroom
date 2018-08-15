import React from 'react';
import PropTypes from 'prop-types';
import { compose, branch, renderNothing } from 'recompose';
import { connect } from 'react-redux';
import { Popup, Button, Icon } from 'semantic-ui-react/dist/commonjs';
import { DragSource } from 'react-dnd';

import { findItem } from 'lib/treeUtils';
import { DocRefBreadcrumb } from 'components/DocRefBreadcrumb';
import ActionBarItemsPropType from './ActionBarItemsPropType';
import { actionCreators } from './redux';
import ItemTypes from './dragDropTypes';

const { docRefSelectionToggled } = actionCreators;

const dragSource = {
  canDrag(props) {
    return true;
  },
  beginDrag({docRefUuid, docRefListing: { selectedDocRefUuids, filteredDocRefs}, keyIsDown:{Control, Meta}}) {
    let docRefUuids = [docRefUuid];

    // If we are dragging one of the items in a selection, bring across the entire selection
    if (selectedDocRefUuids.includes(docRefUuid)) {
      docRefUuids = selectedDocRefUuids;
    }

    return {
      docRefs: filteredDocRefs.filter(d => docRefUuids.includes(d.uuid)),
      isCopy: !!(Control || Meta),
    };
  },
};

function dragCollect(connect, monitor) {
  return {
    connectDragSource: connect.dragSource(), 
    isDragging: monitor.isDragging(),
  };
}

const enhance = compose(
  connect(
    ({ docExplorer: { documentTree }, docRefListing, keyIsDown }, { listingId, docRefUuid }) => ({
      docRefListing: docRefListing[listingId],
      docRefWithLineage: findItem(documentTree, docRefUuid),
      keyIsDown,
    }),
    {
      docRefSelectionToggled,
    },
  ),
  branch(({ docRefWithLineage: { node } }) => !node, renderNothing),
  DragSource(ItemTypes.DOC_REF_UUIDS, dragSource, dragCollect), 
);

const DocRefListingEntry = ({
  docRefWithLineage: { node },
  listingId,
  docRefListing: { selectedDocRefUuids, inMultiSelectMode },
  onNameClick,
  actionBarItems,
  includeBreadcrumb,
  docRefSelectionToggled,
  openDocRef,
  keyIsDown,
  connectDragSource,
}) => connectDragSource(<div
  key={node.uuid}
  className={`doc-ref-listing__item ${
        selectedDocRefUuids.includes(node.uuid) ? 'doc-ref-listing__item--selected' : ''
      }`}
  onClick={(e) => {
        docRefSelectionToggled(listingId, node.uuid, keyIsDown);
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
            onNameClick(node);
            e.stopPropagation();
            e.preventDefault();
          }}
    >
      {node.name}
    </span>
    <span className="doc-ref-listing-entry__action-bar">
      {!inMultiSelectMode &&
            actionBarItems.map(({
 onClick, icon, tooltip, disabled,
}, i) => (
  <Popup
    key={i}
    trigger={
      <Button
        className="action-bar__button"
        circular
        onClick={() => onClick(node)}
        icon={icon}
        disabled={disabled}
      />
                }
    content={tooltip}
  />
            ))}
      {inMultiSelectMode && (
      <Button className="action-bar__button" circular icon="dont" disabled />
          )}
    </span>
  </div>

  {includeBreadcrumb && <DocRefBreadcrumb docRefUuid={node.uuid} openDocRef={openDocRef} />}
</div>);

const EnhancedDocRefListingEntry = enhance(DocRefListingEntry);

EnhancedDocRefListingEntry.propTypes = {
  listingId: PropTypes.string.isRequired,
  docRefUuid: PropTypes.string.isRequired,
  actionBarItems: ActionBarItemsPropType.isRequired,
  includeBreadcrumb: PropTypes.bool.isRequired,
  onNameClick: PropTypes.func.isRequired,
  openDocRef: PropTypes.func.isRequired,
};

EnhancedDocRefListingEntry.defaultProps = {
  actionBarItems: [],
  checkedDocRefs: [],
  includeBreadcrumb: true,
};

export default EnhancedDocRefListingEntry;
