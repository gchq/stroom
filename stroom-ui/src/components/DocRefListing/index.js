import DocRefListing from './DocRefListing';
import DocRefListingEntry from './DocRefListingEntry';
import RawDocRefListingEntry from './RawDocRefListingEntry';
import withOpenDocRef from 'sections/RecentItems/withOpenDocRef';

const DocRefListingWithRouter = withOpenDocRef(DocRefListing);

export default DocRefListing;

export { DocRefListing, DocRefListingWithRouter, DocRefListingEntry, RawDocRefListingEntry };
