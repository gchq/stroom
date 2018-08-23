const SHORTCUT_NAMES = {
  UP: 'up',
  DOWN: 'down',
  OPEN: 'open',
};

const shortcutUsed = shortcut => (dispatch, getState) => {
  const state = getState();
  const {
    keyIsDown: { keyIsDown, focussedElement },
  } = state;

  console.log('Shortcut Used', { shortcut, keyIsDown, focussedElement });
};

export { SHORTCUT_NAMES, shortcutUsed };
