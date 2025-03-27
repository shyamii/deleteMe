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
        log.info("searchId (raw): {}", searchIdd);
        String searchId = searchIdd.trim(); // No escaping for slashes!
        log.info("searchId (processed): {}", searchId);

        ResponseData responseData = new ResponseData();

        // Highlight setup (unchanged)
        Map<String, HighlightField> highlights = new HashMap<>();
        Set<String> highLightData = EZStatusUtil.getGlobalSearchMap().keySet();
        for (String highlightField : highLightData) {
            highlights.put(highlightField + ".keyword", HighlightField.of(hf -> hf));
            highlights.put(highlightField, HighlightField.of(hf -> hf));
        }
        Highlight highlight = Highlight.of(h -> h
            .type(HighlighterType.Unified)
            .fields(highlights)
            .preTags("")
            .postTags("")
        );

        // Field lists
        List<String> exactMatchFields = new ArrayList<>();
        List<String> partialMatchFields = new ArrayList<>();
        for (Map.Entry<String, String> entry : EZStatusUtil.getGlobalSearchMap().entrySet()) {
            exactMatchFields.add(entry.getKey() + ".keyword");
            partialMatchFields.add(entry.getKey());
        }

        // Query construction
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder()
            .index("order_details_alias")
            .size(1000);

        if ("exact".equalsIgnoreCase(matchType)) {
            // Exact match on .keyword fields
            for (String field : exactMatchFields) {
                boolQueryBuilder.should(QueryBuilders.term(t -> t
                    .field(field)
                    .value(searchId)
                ));
            }
        } else {
            // Partial match: combine term (exact) + phrase_prefix (partial)
            for (String field : exactMatchFields) {
                boolQueryBuilder.should(QueryBuilders.term(t -> t
                    .field(field)
                    .value(searchId)
                ));
            }
            for (String field : partialMatchFields) {
                boolQueryBuilder.should(QueryBuilders.matchPhrasePrefix(m -> m
                    .field(field)
                    .query(searchId)
                    .maxExpansions(10) // Limit prefix expansion
                ));
            }
        }

        // Aggregations (unchanged)
        for (Map.Entry<String, String> entry : EZStatusUtil.getAggregatedDataMap().entrySet()) {
            searchRequestBuilder.aggregations(entry.getKey(), Aggregation.of(a -> a
                .terms(t -> t.field(entry.getValue() + ".keyword").size(2000))
            );
        }

        try {
            searchRequestBuilder
                .query(q -> q.bool(boolQueryBuilder.minimumShouldMatch("1").build()))
                .highlight(highlight);

            SearchResponse<ElasticSearchOrderDetail> response = 
                esClient.search(searchRequestBuilder.build(), ElasticSearchOrderDetail.class);
            responseData.setSearchResponse(response);
        } catch (Exception e) {
            log.error("Elasticsearch failed for searchId: {}", searchId, e);
        }

        return responseData;
    }
}
