package stroom.query.common.v2;

public interface OpenGroups {

    OpenGroups NONE = new OpenGroups() {
        @Override
        public boolean isOpen(final Key key) {
            return false;
        }

        @Override
        public void complete(final Key key) {

        }

        @Override
        public boolean isNotEmpty() {
            return false;
        }
    };

    OpenGroups ALL = new OpenGroups() {
        @Override
        public boolean isOpen(final Key key) {
            return true;
        }

        @Override
        public void complete(final Key key) {

        }

        @Override
        public boolean isNotEmpty() {
            return true;
        }
    };

    boolean isOpen(Key key);

    void complete(Key key);

    boolean isNotEmpty();
}
