@Override
public ResponseData getGlobalSearchData(String gsamSensitivity,
        String isGsamCheckRequired, String federalAccessStatus, String searchId,
        GlobalSearchRequest globalSearchRequest, String matchType) {

    log.info("Executing global search - searchId: {}, matchType: {}, federalAccessStatus: {}, gsamSensitivity: {}",
            searchId, matchType, federalAccessStatus, gsamSensitivity);

    long startTime = System.currentTimeMillis();

    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

    // 1. String filters
    if (globalSearchRequest.getFilters() != null) {
        Map<String, String> filters = globalSearchRequest.getFilters();
        String[] stringFields = {"fulfillmentStatus", "crStatus", "centerName", "source",
                                 "workType", "queueName", "orderActivity", "taskName", "productType"};
        for (String field : stringFields) {
            String value = filters.get(field);
            if (value != null && !value.isEmpty()) {
                log.debug("Adding string filter: {} = {}", field, value);
                boolQuery.must(QueryBuilders.termQuery(field, value));
            }
        }
    }

    // 2. Date filters
    if (globalSearchRequest.getDates() != null) {
        Map<String, DateRangeFilter> dateFilters = globalSearchRequest.getDates();
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
                log.debug("Adding date range filter on {}: {}", dateField, range);
                boolQuery.must(rangeQuery);
            }
        }
    }

    // 3. Access Control filters
    if (federalAccessStatus != null && !federalAccessStatus.isEmpty()) {
        log.debug("Adding federalAccessStatus filter: {}", federalAccessStatus);
        boolQuery.filter(QueryBuilders.termQuery("federalAccessStatus", federalAccessStatus));
    }

    if (gsamSensitivity != null && !gsamSensitivity.isEmpty()) {
        log.debug("Adding gsamSensitivityLevel filter: [1, 6, 8]");
        boolQuery.filter(QueryBuilders.termsQuery("gsamSensitivityLevel", 1, 6, 8));
    }

    // 4. Highlight setup
    HighlightBuilder highlightBuilder = new HighlightBuilder();
    String[] highlightFields = {"fulfillmentStatus", "crStatus", "centerName", "source",
                                "workType", "queueName", "orderActivity", "taskName", "productType"};
    for (String field : highlightFields) {
        highlightBuilder.field(new HighlightBuilder.Field(field));
    }
    highlightBuilder.preTags("<em>");
    highlightBuilder.postTags("</em>");

    // 5. Aggregation
    TermsAggregationBuilder aggregation = AggregationBuilders.terms("workTypeAgg")
            .field("workType.keyword");

    // 6. Final query setup
    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(boolQuery)
            .highlighter(highlightBuilder)
            .aggregation(aggregation)
            .size(1000);

    SearchRequest searchRequest = new SearchRequest("your-index-name").source(sourceBuilder);

    ResponseData responseData = new ResponseData();

    try {
        log.info("Executing Elasticsearch query...");
        SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
        List<Map<String, Object>> results = new ArrayList<>();

        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();
            Map<String, HighlightField> highlightMap = hit.getHighlightFields();
            if (!highlightMap.isEmpty()) {
                source.put("highlight", highlightMap);
            }
            results.add(source);
        }
        responseData.setResults(results);
        log.info("Search completed: {} results", results.size());

        // Aggregation processing
        Aggregations aggregations = response.getAggregations();
        if (aggregations != null) {
            Map<String, Object> aggMap = new HashMap<>();
            Terms workTypeAgg = aggregations.get("workTypeAgg");
            if (workTypeAgg != null) {
                List<Map<String, Object>> buckets = new ArrayList<>();
                for (Terms.Bucket bucket : workTypeAgg.getBuckets()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("key", bucket.getKey());
                    data.put("docCount", bucket.getDocCount());
                    buckets.add(data);
                }
                aggMap.put("workTypeAgg", buckets);
            }
            responseData.setAggregations(aggMap);
            log.debug("Aggregation result: {}", aggMap);
        }

    } catch (IOException e) {
        log.error("Failed to execute Elasticsearch query: {}", e.getMessage(), e);
        throw new RuntimeException("Error executing Elasticsearch query", e);
    }

    long elapsed = System.currentTimeMillis() - startTime;
    log.info("Global search completed in {} ms", elapsed);

    return responseData;
}
