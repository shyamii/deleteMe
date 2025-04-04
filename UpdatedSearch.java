package com.yourpackage.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch._types.Highlight;
import co.elastic.clients.elasticsearch._types.HighlightField;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.yourpackage.model.GlobalSearchRequest;
import com.yourpackage.model.GlobalSearchReqFilter;
import com.yourpackage.model.DateRangeFilter;
import com.yourpackage.model.ResponseData;

import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Repository
public class GlobalSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(GlobalSearchRepository.class);

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    public ResponseData getGlobalSearchData(String gsamSensitivity, String isGsamCheckRequired,
                                            String federalAccessStatus, String searchId,
                                            GlobalSearchRequest globalSearchRequest, String matchType) {
        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            // 1. String filters (from GlobalSearchReqFilter)
            GlobalSearchReqFilter filters = globalSearchRequest.getFilters();
            if (filters != null) {
                applyListFilter(boolQuery, "fulfillmentStatus", filters.getFulfillmentStatus());
                applyListFilter(boolQuery, "crStatus", filters.getCrStatus());
                applyListFilter(boolQuery, "centerName", filters.getCenterName());
                applyListFilter(boolQuery, "source", filters.getSource());
                applyListFilter(boolQuery, "workType", filters.getWorkType());
                applyListFilter(boolQuery, "queueName", filters.getQueueName());
                applyListFilter(boolQuery, "orderActivity", filters.getOrderActivity());
                applyListFilter(boolQuery, "taskName", filters.getTaskName());
                applyListFilter(boolQuery, "productType", filters.getProductType());
            }

            // 2. Date filters
            List<String> dateFields = Arrays.asList("dueDate", "orderSubmitDate", "orderCreationDate");
            globalSearchRequest.getDates().forEach((field, range) -> {
                if (dateFields.contains(field) && range != null) {
                    RangeQuery.Builder rangeQuery = new RangeQuery.Builder().field(field);
                    if (range.getStartDate() != null) {
                        rangeQuery.gte(range.getStartDate());
                    }
                    if (range.getEndDate() != null) {
                        rangeQuery.lte(range.getEndDate());
                    }
                    boolQuery.filter(q -> q.range(rangeQuery.build()));
                }
            });

            // 3. Access Control Logic
            if ("RESTRICTED".equalsIgnoreCase(federalAccessStatus)) {
                boolQuery.must(q -> q.term(t -> t.field("federalAccess").value("true")));
            }
            if ("true".equalsIgnoreCase(isGsamCheckRequired)) {
                boolQuery.filter(q -> q.terms(t -> t.field("gsamSensitivity")
                        .terms(tqf -> tqf.value(v -> v.stringValues(Arrays.asList("1", "6", "8"))))));
            }

            // 4. Highlighting setup
            Highlight highlight = Highlight.of(h -> h.fields("taskName",
                    HighlightField.of(f -> f.preTags("<em>").postTags("</em>").numberOfFragments(0))));

            // 5. Aggregations
            Map<String, Aggregation> aggregations = new HashMap<>();
            aggregations.put("by_workType", AggregationBuilders.terms().field("workType.keyword").build());

            // 6. Construct Search Request
            SearchRequest searchRequest = new SearchRequest.Builder()
                    .index("your-index-name")
                    .query(q -> q.bool(boolQuery.build()))
                    .highlight(highlight)
                    .aggregations(aggregations)
                    .size(1000)
                    .build();

            log.info("Executing global search with query: {}", searchRequest.query());

            // 7. Execute Search
            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            List<Map<String, Object>> searchResults = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> result = hit.source();
                if (hit.highlight() != null && !hit.highlight().isEmpty()) {
                    result.put("_highlight", hit.highlight());
                }
                searchResults.add(result);
            }

            // 8. Handle Aggregations
            Map<String, Object> aggResults = new HashMap<>();
            if (response.aggregations() != null && response.aggregations().containsKey("by_workType")) {
                TermsAggregation termsAgg = response.aggregations().get("by_workType").terms();
                List<Map<String, Object>> buckets = new ArrayList<>();
                termsAgg.buckets().array().forEach(bucket -> {
                    Map<String, Object> b = new HashMap<>();
                    b.put("key", bucket.key());
                    b.put("doc_count", bucket.docCount());
                    buckets.add(b);
                });
                aggResults.put("workType", buckets);
            }

            // 9. Return Response
            ResponseData responseData = new ResponseData();
            responseData.setResults(searchResults);
            responseData.setAggregations(aggResults);

            return responseData;

        } catch (IOException e) {
            log.error("Error while performing global search", e);
            return new ResponseData();
        }
    }

    private void applyListFilter(BoolQuery.Builder boolQuery, String fieldName, List<String> values) {
    if (values != null && !values.isEmpty()) {
        log.debug("Applying filter on field: {} with values: {}", fieldName, values);
        boolQuery.filter(f -> f.terms(t -> t
            .field(fieldName)
            .terms(tf -> tf.value(
                values.stream()
                    .map(FieldValue::of)
                    .collect(Collectors.toList())
            ))
        ));
    }
}
    private void applyDateRangeFilter(BoolQuery.Builder boolQuery, String fieldName, CustomDate customDate) {
    if (customDate != null && (customDate.getStartDate() != null || customDate.getEndDate() != null)) {
        RangeQuery.Builder rangeQuery = new RangeQuery.Builder().field(fieldName);
        if (customDate.getStartDate() != null) {
            rangeQuery.gte(customDate.getStartDate());
        }
        if (customDate.getEndDate() != null) {
            rangeQuery.lte(customDate.getEndDate());
        }
        boolQuery.filter(q -> q.range(rangeQuery.build()));
        log.debug("Applied range filter for {} from {} to {}", fieldName, customDate.getStartDate(), customDate.getEndDate());
    }
}


}
