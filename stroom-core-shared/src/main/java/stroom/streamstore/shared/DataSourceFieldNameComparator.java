package stroom.streamstore.shared;

import stroom.datasource.api.v2.DataSourceField;

import java.util.Comparator;

public class DataSourceFieldNameComparator implements Comparator<DataSourceField> {
    private static final DataSourceFieldNameComparator INSTANCE = new DataSourceFieldNameComparator();

    public static DataSourceFieldNameComparator getInstance() { return INSTANCE; }

    private DataSourceFieldNameComparator() {

    }

    static class WordCountComparator implements Comparator<String> {
        private static final WordCountComparator INSTANCE = new WordCountComparator();
        public static WordCountComparator getInstance() {
            return INSTANCE;
        }

        private WordCountComparator() {

        }

        @Override
        public int compare(final String o1,
                           final String o2) {
            return Integer.compare(o1.split(" ").length, o2.split(" ").length);
        }
    }

    static class NameComparator implements Comparator<String> {
        private static final NameComparator INSTANCE = new NameComparator();
        public static NameComparator getInstance() {
            return INSTANCE;
        }

        private NameComparator() {}


        @Override
        public int compare(final String o1,
                           final String o2) {
            int wc = WordCountComparator.getInstance().compare(o1, o2);
            if (wc != 0) {
                return wc;
            }

            final String[] w1 = o1.split(" ");
            final String[] w2 = o2.split(" ");

            for (int x = w1.length-1; x >= 0; x--){
                int c = w1[x].compareTo(w2[x]);
                if (c != 0) {
                    return c;
                }
            }

            return 0;
        }
    }

    @Override
    public int compare(final DataSourceField o1,
                       final DataSourceField o2) {
        return NameComparator.getInstance().compare(o1.getName(), o2.getName());
    }
}
