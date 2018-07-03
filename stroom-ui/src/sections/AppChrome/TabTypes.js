// Enumerate the tab types that can be opened
const TabTypes = {
  DOC_REF: 0,
  EXPLORER_TREE: 1,
  TRACKER_DASHBOARD: 2,
  USER_ME: 3,
  AUTH_USERS: 4,
  AUTH_TOKENS: 5,
};

const getTabTitle = (tabData) => {
  let title;

  switch (tabData.type) {
    case TabTypes.DOC_REF:
      const docRef = tabData.data;
      title = docRef.name;
      break;
    case TabTypes.EXPLORER_TREE:
      title = 'Explorer';
      break;
    case TabTypes.TRACKER_DASHBOARD:
      title = 'Trackers';
      break;
    case TabTypes.USER_ME:
      title = 'Me';
      break;
    case TabTypes.AUTH_USERS:
      title = 'Users';
      break;
    case TabTypes.AUTH_TOKENS:
      title = 'API Keys';
      break;
    default:
      // sad times
      title = 'UNKNOWN';
      break;
  }

  return title;
};

export { TabTypes, getTabTitle };

export default TabTypes;
