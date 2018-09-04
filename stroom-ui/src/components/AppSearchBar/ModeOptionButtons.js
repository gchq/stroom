import React from 'react';
import PropTypes from 'prop-types';
import { compose } from 'recompose';
import { connect } from 'react-redux';
import { Button } from 'semantic-ui-react';

import { actionCreators as appSearchBarActionCreators, SEARCH_MODE } from './redux';

const { switchMode } = appSearchBarActionCreators;

const enhance = compose(connect(undefined, {
  switchMode,
}));

const MODE_OPTIONS = [
  {
    mode: SEARCH_MODE.GLOBAL_SEARCH,
    icon: 'search',
  },
  {
    mode: SEARCH_MODE.NAVIGATION,
    icon: 'folder',
  },
  {
    mode: SEARCH_MODE.RECENT_ITEMS,
    icon: 'history',
  },
];

const ModeOptionButtons = ({ switchMode, pickerId }) => (
  <Button.Group floated="right">
    {MODE_OPTIONS.map(modeOption => (
      <Button
        key={modeOption.mode}
        icon={modeOption.icon}
        circular
        className="icon-button"
        onClick={e => switchMode(pickerId, modeOption.mode)}
        onKeyDown={(e) => {
          if (e.key === ' ') {
            switchMode(pickerId, modeOption.mode);
          }
        }}
      />
    ))}
  </Button.Group>
);

ModeOptionButtons.propTypes = {
  pickerId: PropTypes.string.isRequired,
};

export default enhance(ModeOptionButtons);
