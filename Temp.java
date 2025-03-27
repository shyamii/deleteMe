import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.HighlighterType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ElasticSearchQueryHandler {

    public ResponseData searchElasticsearch(String searchIdd, String matchType, ElasticSearchClient esClient) {
        log.info("Search ID (Before escaping): {}", searchIdd);
        String searchId = escapeElasticSearchSpecialChars(searchIdd);
        log.info("Search ID (After escaping): {}", searchId);

        int minimumShouldMatch = 0;
        ResponseData responseData = new ResponseData();

        // Highlight configuration
        Map<String, HighlightField> highlights = EZStatusUtil.getGlobalSearchMap()
                .keySet()
                .stream()
                .flatMap(field -> Arrays.stream(new String[]{field, field + ".keyword"}))
                .collect(Collectors.toMap(field -> field, field -> new HighlightField.Builder().build()));

        Highlight highlight = Highlight.of(h -> h.type(HighlighterType.Unified).fields(highlights).preTags("").postTags(""));

        // Fields for filtering
        List<String> filterList = EZStatusUtil.getGlobalSearchMap().keySet()
                .stream()
                .map(field -> field + ".keyword")
                .collect(Collectors.toList());

        List<String> wildcardfilterList = new ArrayList<>(EZStatusUtil.getGlobalSearchMap().keySet());

        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder()
                .index("order_details_alias")
                .size(1000);

        if ("exact".equals(matchType)) {
            boolQueryBuilder.should(QueryBuilders
                    .multiMatch(m -> m.query(searchId.toUpperCase()).fields(filterList)));
            minimumShouldMatch++;
        } else {
            boolQueryBuilder.should(QueryBuilders
                    .multiMatch(m -> m.query(searchId.toUpperCase()).fields(filterList)))
                    .should(QueryBuilders.queryString(q -> q.fields(wildcardfilterList).query(searchId + "*").defaultOperator(Operator.And)));
            minimumShouldMatch++;
        }

        // Aggregations
        EZStatusUtil.getAggregatedDataMap().forEach((key, value) ->
                searchRequestBuilder.aggregations(key, Aggregation.of(b -> b.terms(t -> t.field(value + ".keyword").size(2000))))
        );

        // Build final search request
        searchRequestBuilder.query(boolQueryBuilder.minimumShouldMatch(String.valueOf(minimumShouldMatch)).build()._toQuery()).highlight(highlight);

        // Log full JSON query before execution
        logQuery(searchRequestBuilder);

        // Execute search
        try {
            SearchResponse<ElasticSearchOrderDetail> response = esClient.search(searchRequestBuilder.build(), ElasticSearchOrderDetail.class);
            responseData.setSearchResponse(response);
        } catch (Exception e) {
            log.error("Error while searching in Elasticsearch: {}", e.getMessage(), e);
        }

        return responseData;
    }

    private void logQuery(SearchRequest.Builder searchRequestBuilder) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonQuery = objectMapper.writeValueAsString(searchRequestBuilder.build());
            log.info("Generated Elasticsearch Query: {}", jsonQuery);
        } catch (JsonProcessingException e) {
            log.error("Error converting query to JSON", e);
        }
    }

    private static String escapeElasticSearchSpecialChars(String input) {
        return input.replaceAll("(?<!\\\\)/", "\\\\/"); // Escapes `/` only once
    }

        private static String escapeElasticSearchSpecialChars(String input) {
        if (input == null) return "";
        return input.replaceAll("([+\\-!(){}\\[\\]^\"~*?:\\\\/])", "\\\\$1");
    }
}
