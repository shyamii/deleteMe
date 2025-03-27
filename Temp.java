import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

public class ElasticSearchService {
    private static final Logger log = LoggerFactory.getLogger(ElasticSearchService.class);
    private final ElasticsearchClient esClient;

    public ElasticSearchService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public ResponseData searchOrders(String searchIdd, String matchType) {
        log.info("searchId (before escaping): {}", searchIdd);
        String searchId = escapeElasticSearchSpecialChars(searchIdd);
        log.info("searchId (after escaping): {}", searchId);

        ResponseData responseData = new ResponseData();

        // Configure highlighting
        Map<String, HighlightField> highlights = new HashMap<>();
        Set<String> highLightData = EZStatusUtil.getGlobalSearchMap().keySet();
        for (String highlightField : highLightData) {
            highlights.put(highlightField + ".keyword", new HighlightField.Builder().build());
            highlights.put(highlightField, new HighlightField.Builder().build());
        }

        Highlight highlight = Highlight.of(h -> h
            .type(HighlighterType.Unified)
            .fields(highlights)
            .preTags("")
            .postTags("")
        );

        // Prepare fields for exact and wildcard queries
        List<String> exactMatchFields = new ArrayList<>();
        List<String> wildcardFields = new ArrayList<>();

        for (Map.Entry<String, String> searchData : EZStatusUtil.getGlobalSearchMap().entrySet()) {
            exactMatchFields.add(searchData.getKey() + ".keyword");
            wildcardFields.add(searchData.getKey());
        }

        SearchResponse<ElasticSearchOrderDetail> response;
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder()
            .index("order_details_alias")
            .size(1000);

        // 1. Exact Match (keyword fields)
        if ("exact".equalsIgnoreCase(matchType)) {
            boolQueryBuilder.should(QueryBuilders.multiMatch(m -> m
                .query(searchId)
                .fields(exactMatchFields)
            ));
        } 
        // 2. Wildcard Match (text fields)
        else {
            // Multi-match on exact fields
            boolQueryBuilder.should(QueryBuilders.multiMatch(m -> m
                .query(searchId)
                .fields(exactMatchFields)
            ));

            // Wildcard queries on text fields
            for (String field : wildcardFields) {
                boolQueryBuilder.should(QueryBuilders.wildcard(w -> w
                    .field(field)
                    .value(searchId + "*")
                    .caseInsensitive(true) // Case-insensitive matching
                ));
            }
        }

        // Add aggregations
        for (Map.Entry<String, String> aggregatedDataMap : EZStatusUtil.getAggregatedDataMap().entrySet()) {
            searchRequestBuilder.aggregations(aggregatedDataMap.getKey(), Aggregation.of(a -> a
                .terms(t -> t.field(aggregatedDataMap.getValue() + ".keyword").size(2000))
            );
        }

        try {
            searchRequestBuilder
                .query(q -> q.bool(boolQueryBuilder
                    .minimumShouldMatch("1") // Only 1 should clause needs to match
                    .build()
                ))
                .highlight(highlight);

            response = esClient.search(searchRequestBuilder.build(), ElasticSearchOrderDetail.class);
            responseData.setSearchResponse(response);
        } catch (Exception e) {
            log.error("Elasticsearch error: {}", e.getMessage(), e);
            if (e.getCause() != null) {
                log.error("Root cause: {}", e.getCause().getMessage());
            }
        }

        return responseData;
    }

    // Only escape Elasticsearch-reserved characters (excluding '/')
    private static String escapeElasticSearchSpecialChars(String input) {
        if (input == null) return "";
        return input.replaceAll("([+\\-!(){}\\[\\]^\"~*?:\\\\])", "\\\\$1");
    }
}
