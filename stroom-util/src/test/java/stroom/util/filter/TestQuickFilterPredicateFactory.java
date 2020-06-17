package stroom.util.filter;

import stroom.docref.DocRef;
import stroom.util.shared.filter.FilterFieldDefinition;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Predicate;

class TestQuickFilterPredicateFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestQuickFilterPredicateFactory.class);

    private static final Map<String, FilterFieldMapper<Pojo>> FIELD_MAPPERS = FilterFieldMapper.mappedByQualifier(
            FilterFieldMapper.of(FilterFieldDefinition.qualifiedField("Status"), Pojo::getStatus),
            FilterFieldMapper.of(FilterFieldDefinition.defaultField("SimpleStr"), Pojo::getName),
            FilterFieldMapper.of(FilterFieldDefinition.qualifiedField("DocRef Type"), Pojo::getDocRef, DocRef::getType),
            FilterFieldMapper.of(FilterFieldDefinition.qualifiedField("DocRef Name"), Pojo::getDocRef, DocRef::getName)
    );

    @Test
    void test() {

        final Predicate<Pojo> predicate = QuickFilterPredicateFactory.createPredicate(
                "status:ok \"my name\" type:^mytype$",
                FIELD_MAPPERS);

        Pojo pojo1 = new Pojo(
                "OK",
                "MY NAME",
                new DocRef("MyType", "123", "DocRefName"));

        Assertions.assertThat(predicate.test(pojo1))
                .isTrue();
    }

    @Test
    void test2() {

        final Predicate<Pojo> predicate = QuickFilterPredicateFactory.createPredicate(
                "myname",
                FIELD_MAPPERS);

        Pojo pojo1 = new Pojo(
                "OK",
                "MY_NAME",
                new DocRef("MyType", "123", "DocRefName"));

        Assertions.assertThat(predicate.test(pojo1))
                .isTrue();
    }

    @Test
    void test3() {

        final Predicate<Pojo> predicate = QuickFilterPredicateFactory.createPredicate(
                " simplestr:myname ",
                FIELD_MAPPERS);

        Pojo pojo1 = new Pojo(
                "OK",
                "MY_NAME",
                new DocRef("MyType", "123", "DocRefName"));

        Assertions.assertThat(predicate.test(pojo1))
                .isTrue();
    }

    private static class Pojo {
        private final String status;
        private final String name;
        private final DocRef docRef;

        public Pojo(final String status, final String name, final DocRef docRef) {
            this.status = status;
            this.name = name;
            this.docRef = docRef;
        }

        public String getName() {
            return name;
        }

        public String getStatus() {
            return status;
        }

        public DocRef getDocRef() {
            return docRef;
        }
    }
}