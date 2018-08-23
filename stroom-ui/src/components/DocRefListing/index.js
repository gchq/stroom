import DocRefListing from './DocRefListing';
import DndDocRefListingEntry from './DndDocRefListingEntry';
import DocRefListingEntry from './DocRefListingEntry';
import withOpenDocRef from 'sections/RecentItems/withOpenDocRef';

const DocRefListingWithRouter = withOpenDocRef(DocRefListing);

export default DocRefListing;

export { DocRefListing, DocRefListingWithRouter, DndDocRefListingEntry, DocRefListingEntry };
