import React from 'react';
import PropTypes from 'prop-types';
import { compose } from 'recompose';
import { connect } from 'react-redux';

import IconButton from 'components/IconButton';
import { actionCreators as appSearchBarActionCreators, SEARCH_MODE } from './redux';

const { switchMode } = appSearchBarActionCreators;

const enhance = compose(connect(undefined, {
  switchMode,
}));

const MODE_OPTIONS = [
  {
    mode: SEARCH_MODE.GLOBAL_SEARCH,
    icon: 'search',
    position: 'left',
  },
  {
    mode: SEARCH_MODE.NAVIGATION,
    icon: 'folder',
    position: 'middle',
  },
  {
    mode: SEARCH_MODE.RECENT_ITEMS,
    icon: 'history',
    position: 'right',
  },
];

const ModeOptionButtons = ({ switchMode, pickerId }) => (
  <React.Fragment>
    {MODE_OPTIONS.map(modeOption => (
      <IconButton
        key={modeOption.mode}
        icon={modeOption.icon}
        groupPosition={modeOption.position}
        onClick={e => switchMode(pickerId, modeOption.mode)}
        onKeyDown={(e) => {
          if (e.key === ' ') {
            switchMode(pickerId, modeOption.mode);
          }
          e.stopPropagation();
        }}
      />
    ))}
  </React.Fragment>
);

ModeOptionButtons.propTypes = {
  pickerId: PropTypes.string.isRequired,
};

export default enhance(ModeOptionButtons);
