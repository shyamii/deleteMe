@Override
public ResponseData getGlobalSearchData(String gsamSensitivity,
        String isGsamCheckRequired, String federalAccessStatus, String searchId,
        GlobalSearchRequest globalSearchRequest, String matchType) {

    // 1. Build the dynamic bool query
    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

    // -- String filters from globalSearchRequest.getFilters()
    if (globalSearchRequest.getFilters() != null) {
        Map<String, String> filters = globalSearchRequest.getFilters();
        // List of fields to filter using termsQuery
        String[] stringFields = {"fulfillmentStatus", "crStatus", "centerName", "source",
                                   "workType", "queueName", "orderActivity", "taskName", "productType"};
        for (String field : stringFields) {
            String value = filters.get(field);
            if (value != null && !value.isEmpty()) {
                // Use must clause to require exact match on each field
                boolQuery.must(QueryBuilders.termQuery(field, value));
            }
        }
    }

    // -- Date filters from globalSearchRequest.getDates()
    if (globalSearchRequest.getDates() != null) {
        Map<String, DateRangeFilter> dateFilters = globalSearchRequest.getDates();
        // Example date fields: dueDate, orderSubmitDate, orderCreationDate
        String[] dateFields = {"dueDate", "orderSubmitDate", "orderCreationDate"};
        for (String dateField : dateFields) {
            DateRangeFilter range = dateFilters.get(dateField);
            if (range != null) {
                RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery(dateField);
                if (range.getStartDate() != null) {
                    rangeQuery.gte(range.getStartDate());
                }
                if (range.getEndDate() != null) {
                    rangeQuery.lte(range.getEndDate());
                }
                boolQuery.must(rangeQuery);
            }
        }
    }

    // 3. Apply Access Control filters

    // Filter based on federalAccessStatus if provided
    if (federalAccessStatus != null && !federalAccessStatus.isEmpty()) {
        boolQuery.filter(QueryBuilders.termQuery("federalAccessStatus", federalAccessStatus));
    }

    // Filter based on gsamSensitivityLevel – assuming user has access to levels 1, 8, 6
    if (gsamSensitivity != null && !gsamSensitivity.isEmpty()) {
        // Alternatively, if gsamSensitivity determines a specific filtering strategy, adjust accordingly.
        boolQuery.filter(QueryBuilders.termsQuery("gsamSensitivityLevel", 1, 8, 6));
    }

    // Additional clauses (mustNot or should) can be added here if needed,
    // for example: conditionally apply matchType related queries.

    // 4. Define highlighting options
    HighlightBuilder highlightBuilder = new HighlightBuilder();
    // Highlight fields as appropriate (adjust field names as needed)
    String[] highlightFields = {"fulfillmentStatus", "crStatus", "centerName", "source",
                                "workType", "queueName", "orderActivity", "taskName", "productType"};
    for (String field : highlightFields) {
        highlightBuilder.field(new HighlightBuilder.Field(field));
    }
    highlightBuilder.preTags("<em>");
    highlightBuilder.postTags("</em>");

    // 5. Aggregations: add one or more aggregations if required.
    // For example, to aggregate on workType:
    TermsAggregationBuilder aggregation = AggregationBuilders.terms("workTypeAgg")
            .field("workType.keyword");
    
    // 6. Build the search request
    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
    sourceBuilder.query(boolQuery);
    sourceBuilder.highlighter(highlightBuilder);
    sourceBuilder.aggregation(aggregation);
    // Limit results to 1,000 records per request
    sourceBuilder.size(1000);

    // Create and configure the search request (replace "your-index-name" with your actual index)
    SearchRequest searchRequest = new SearchRequest("your-index-name");
    searchRequest.source(sourceBuilder);

    // 7. Execute the search query using the Elasticsearch client (or ElasticsearchRestTemplate)
    ResponseData responseData = new ResponseData();
    try {
        SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
        List<Map<String, Object>> results = new ArrayList<>();

        // Process search hits
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> sourceAsMap = hit.getSourceAsMap();
            // Attach highlighting details if available
            Map<String, HighlightField> highlightFieldsMap = hit.getHighlightFields();
            if (highlightFieldsMap != null && !highlightFieldsMap.isEmpty()) {
                sourceAsMap.put("highlight", highlightFieldsMap);
            }
            results.add(sourceAsMap);
        }
        responseData.setResults(results);

        // Process aggregations if applicable
        Aggregations aggregations = response.getAggregations();
        if (aggregations != null) {
            Map<String, Object> aggMap = new HashMap<>();
            Terms workTypeAgg = aggregations.get("workTypeAgg");
            if (workTypeAgg != null) {
                List<? extends Terms.Bucket> buckets = workTypeAgg.getBuckets();
                List<Map<String, Object>> aggBuckets = new ArrayList<>();
                for (Terms.Bucket bucket : buckets) {
                    Map<String, Object> bucketData = new HashMap<>();
                    bucketData.put("key", bucket.getKey());
                    bucketData.put("docCount", bucket.getDocCount());
                    aggBuckets.add(bucketData);
                }
                aggMap.put("workTypeAgg", aggBuckets);
            }
            responseData.setAggregations(aggMap);
        }
    } catch (IOException e) {
        // Handle exceptions appropriately
        throw new RuntimeException("Error executing Elasticsearch query", e);
    }

    return responseData;
}
