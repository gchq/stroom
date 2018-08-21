import DocRefListing from './DocRefListing';
import DocRefListingEntry from './DocRefListingEntry';
import RawDocRefListingEntry from './RawDocRefListingEntry';
import withOpenDocRef from 'sections/RecentItems/withOpenDocRef';
import { actionCreators, reducer } from './redux';

const DocRefListingWithRouter = withOpenDocRef(DocRefListing);

export default DocRefListing;

export {
  DocRefListing,
  DocRefListingWithRouter,
  DocRefListingEntry,
  RawDocRefListingEntry,
  actionCreators,
  reducer,
};
