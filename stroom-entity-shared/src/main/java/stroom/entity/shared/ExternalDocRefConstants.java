package stroom.entity.shared;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface ExternalDocRefConstants {
    String ELASTIC_INDEX = "ElasticIndex";
    String ANNOTATIONS_INDEX = "AnnotationsIndex";
    List<String> EXTERNAL_TYPES = Stream.of(ELASTIC_INDEX, ANNOTATIONS_INDEX).collect(Collectors.toList());
}
