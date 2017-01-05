package stroom.query.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import stroom.entity.shared.DocRef;

import javax.xml.bind.JAXBException;

import java.io.IOException;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class SerialisationTest {

    @Test
    public void testJsonDeserialisation() throws IOException, JAXBException {
        // Given
        SearchRequest searchRequest = getSearchRequest();
        ObjectMapper mapper = new ObjectMapper();
        // Enabling default typing adds type information where it would otherwise be ambiguous, i.e. for abstract classes
        mapper.enableDefaultTyping();

        // When
        String serialisedSearchRequest = mapper.writeValueAsString(searchRequest);
        SearchRequest deserialisedSearchRequest = mapper.readValue(serialisedSearchRequest, SearchRequest.class);

        // Then
        assertThat(deserialisedSearchRequest, equalTo(searchRequest));
        assertThat(deserialisedSearchRequest.toString(), equalTo(searchRequest.toString()));
    }

    private static SearchRequest getSearchRequest(){
        DocRef docRef = new DocRef();
        docRef.setId(1l);
        docRef.setName("docRefName");
        docRef.setType("docRefType");
        docRef.setUuid("docRefUuid");

        ExpressionOperator expressionOperator = new ExpressionOperator();
        List<ExpressionItem> expression = new ArrayList<>();
        expression.add(new ExpressionTerm("field1", Condition.EQUALS, "value1"));
        expression.add(new ExpressionOperator(ExpressionOperator.Op.AND));
        expression.add(new ExpressionTerm("field2", Condition.BETWEEN, "value2"));
        expressionOperator.setChildren(expression);

        TableSettings tableSettings = new TableSettings();
        tableSettings.setQueryId("someQueryId");
        tableSettings.setFields(Arrays.asList(
                new Field("name1", "expression1", new Sort(1, Sort.SortDirection.ASCENDING),
                        new Filter("include1", "exclude1"), new Format(Format.Type.GENERAL,
                        new NumberFormatSettings(1, false), false), 1, 2, false),
                new Field("name2", "expression2", new Sort(2, Sort.SortDirection.DESCENDING),
                        new Filter("include2", "exclude2"), new Format(Format.Type.DATE_TIME,
                        new NumberFormatSettings(2, true), true), 2, 3, true)));
        tableSettings.setExtractValues(false);
        tableSettings.setExtractionPipeline(new DocRef("docRefType2", "docRefUuid2", "docRefName2"));
        tableSettings.setMaxResults(new int[]{1, 2});
        tableSettings.setShowDetail(false);

        Map<String, ComponentSettings> componentSettingsMap = new HashMap<>();
        componentSettingsMap.put("componentSettingsMapKey", tableSettings);


        Search search = new Search(docRef, expressionOperator, componentSettingsMap);

        Map<String, ComponentResultRequest> componentResultRequests = new HashMap<>();
        componentResultRequests.put("componentResult", new TableResultRequest());


        SearchRequest searchRequest = new SearchRequest(search, componentResultRequests, "en-gb");

        return searchRequest;
    }
}

