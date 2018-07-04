// Enumerate the tab types that can be opened
const TabTypes = {
  DOC_REF: 0,
  EXPLORER_TREE: 1,
  TRACKER_DASHBOARD: 2,
  USER_ME: 3,
  AUTH_USERS: 4,
  AUTH_TOKENS: 5,
};

const TabTypeDisplayInfo = {
  [TabTypes.DOC_REF]: {
    getTitle: tabData => tabData.name,
    icon: 'file outline',
  },
  [TabTypes.EXPLORER_TREE]: {
    getTitle: () => 'Explorer',
    icon: 'eye',
  },
  [TabTypes.TRACKER_DASHBOARD]: {
    getTitle: () => 'Trackers',
    icon: 'tasks',
  },
  [TabTypes.USER_ME]: {
    getTitle: () => 'Me',
    icon: 'user',
  },
  [TabTypes.AUTH_USERS]: {
    getTitle: () => 'Users',
    icon: 'users',
  },
  [TabTypes.AUTH_TOKENS]: {
    getTitle: () => 'API Keys',
    icon: 'key',
  },
};

export { TabTypes, TabTypeDisplayInfo };

export default TabTypes;
