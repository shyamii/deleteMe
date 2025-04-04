private void buildSearchQuery(BoolQuery.Builder mainQuery, String searchTerm, String matchType) {
    if (!StringUtils.hasText(searchTerm)) return;

    if ("exact".equalsIgnoreCase(matchType)) {
        // Exact match using .keyword fields
        mainQuery.must(QueryBuilders.multiMatch(m -> m
            .query(searchTerm)
            .fields(EZStatusUtil.getGlobalSearchMap().keySet().stream()
                .map(f -> f + ".keyword")
                .collect(Collectors.toList()))
            .type(TextQueryType.BestFields)
        );
    } else {
        // Wildcard search with proper escaping
        String escapedTerm = escapeSearchTerm(searchTerm);
        
        BoolQuery.Builder searchBool = QueryBuilders.bool();
        
        // 1. Exact phrase match (boosted)
        searchBool.should(QueryBuilders.matchPhrase(m -> m
            .query(searchTerm)
            .fields(EZStatusUtil.getGlobalSearchMap().keySet())
            .boost(2.0f)
        );
        
        // 2. Wildcard search with proper escaping
        searchBool.should(QueryBuilders.queryString(q -> q
            .query(escapedTerm + "*")
            .fields(EZStatusUtil.getGlobalSearchMap().keySet())
            .escape(true)
            .defaultOperator(Operator.AND)
        ));
        
        // 3. Fuzzy match for typos
        searchBool.should(QueryBuilders.multiMatch(m -> m
            .query(searchTerm)
            .fields(EZStatusUtil.getGlobalSearchMap().keySet())
            .fuzziness("AUTO")
        ));
        
        mainQuery.must(searchBool.build()._toQuery());
    }
}

private String escapeSearchTerm(String term) {
    // Escape special characters that break query string parsing
    return term.replaceAll("([+\\-=&|!(){}\\[\\]^\"~*?:\\\\/])", "\\\\$1");
}


public ResponseData getGlobalSearchData(String gsamSensitivity, String isGsamCheckRequired,
        String federalAccessStatus, String searchId, GlobalSearchRequest globalSearchRequest, String matchType) {
    
    ResponseData responseData = new ResponseData();
    BoolQuery.Builder mainQuery = QueryBuilders.bool();

    // 1. Base Search Query ==============================================
    buildSearchQuery(mainQuery, searchId, matchType);

    // 2. Access Control Filters =========================================
    applyAccessControlFilters(mainQuery, federalAccessStatus, gsamSensitivity, isGsamCheckRequired);

    // 3. String Filters ================================================
    applyStringFilters(mainQuery, globalSearchRequest.getFilters());

    // 4. Date Filters ==================================================
    applyDateRangeFilters(mainQuery, globalSearchRequest.getDates());

    // 5. Owner Filter ==================================================
    if (StringUtils.hasText(globalSearchRequest.getOwner())) {
        mainQuery.must(QueryBuilders.term(t -> t
            .field("userName.keyword")
            .value(globalSearchRequest.getOwner())));
    }

    // 6. Highlight Configuration =======================================
    Highlight highlight = buildHighlightConfig();

    // 7. Aggregation Configuration =====================================
    Map<String, Aggregation> aggregations = buildAggregations();

    // 8. Execute Search ================================================
    SearchRequest searchRequest = new SearchRequest.Builder()
        .index("order_details_alias")
        .query(mainQuery.build()._toQuery())
        .size(1000)
        .highlight(highlight)
        .aggregations(aggregations)
        .build();

    try {
        SearchResponse<ElasticSearchOrderDetail> response = esClient.search(searchRequest, ElasticSearchOrderDetail.class);
        mapResultsToResponse(responseData, response);
    } catch (IOException e) {
        log.error("Elasticsearch query failed: {}", e.getMessage(), e);
        responseData.setError(e.getMessage());
    }

    return responseData;
}

// HELPER METHODS =======================================================

private void buildSearchQuery(BoolQuery.Builder mainQuery, String searchTerm, String matchType) {
    if (!StringUtils.hasText(searchTerm)) return;

    BoolQuery.Builder searchBool = QueryBuilders.bool();
    
    if ("exact".equalsIgnoreCase(matchType)) {
        // Exact match using .keyword fields
        searchBool.must(QueryBuilders.multiMatch(m -> m
            .query(searchTerm)
            .fields(EZStatusUtil.getGlobalSearchMap().keySet().stream()
                .map(f -> f + ".keyword")
                .collect(Collectors.toList()))
        );
    } else {
        // Wildcard search with boosted exact matches
        searchBool.should(QueryBuilders.multiMatch(m -> m
            .query(searchTerm)
            .fields(EZStatusUtil.getGlobalSearchMap().keySet())
            .type(TextQueryType.BoolPrefix)
        );
        
        searchBool.should(QueryBuilders.queryString(q -> q
            .query(searchTerm + "*")
            .fields(EZStatusUtil.getGlobalSearchMap().keySet())
        ));
    }
    
    mainQuery.must(searchBool.build()._toQuery());
}

private void applyAccessControlFilters(BoolQuery.Builder mainQuery, String federalAccess, 
        String gsamLevels, String gsamCheckRequired) {
    
    // Federal access filter
    if ("No".equalsIgnoreCase(federalAccess)) {
        mainQuery.mustNot(QueryBuilders.term(t -> t
            .field("federalFlag.keyword")
            .value("FEDERAL")));
    }

    // GSAM sensitivity filter
    if ("true".equalsIgnoreCase(gsamCheckRequired)) {
        BoolQuery.Builder gsamQuery = QueryBuilders.bool();
        
        // Include allowed levels or missing field
        Arrays.stream(gsamLevels.split(","))
            .forEach(level -> gsamQuery.should(QueryBuilders.term(t -> t
                .field("gsamSensitivityLevel.keyword")
                .value(level))));
        
        gsamQuery.should(QueryBuilders.bool(b -> b
            .mustNot(QueryBuilders.exists(e -> e.field("gsamSensitivityLevel"))));
        
        mainQuery.must(gsamQuery.build()._toQuery());
    }
}

private void applyStringFilters(BoolQuery.Builder mainQuery, GlobalSearchRequest.Filters filters) {
    if (filters == null) return;

    Stream.of(
        Map.entry(filters.getFulfillmentStatus(), "fulfillmentStatus.keyword"),
        Map.entry(filters.getCrStatus(), "crStatus.keyword"),
        Map.entry(filters.getCenterName(), "centerName.keyword"),
        Map.entry(filters.getSource(), "source.keyword"),
        Map.entry(filters.getWorkType(), "workType.keyword"),
        Map.entry(filters.getQueueName(), "queueName.keyword"),
        Map.entry(filters.getOrderActivity(), "orderActivity.keyword"),
        Map.entry(filters.getTaskName(), "taskName.keyword"),
        Map.entry(filters.getProductType(), "productType.keyword")
    ).forEach(entry -> {
        List<String> values = entry.getKey();
        String field = entry.getValue();
        
        if (values != null && !values.isEmpty()) {
            mainQuery.must(QueryBuilders.terms(t -> t
                .field(field)
                .terms(terms -> terms.value(values.stream()
                    .map(FieldValue::of)
                    .collect(Collectors.toList())))
            ));
        }
    });
}

private void applyDateRangeFilters(BoolQuery.Builder mainQuery, GlobalSearchRequest.Dates dates) {
    if (dates == null) return;

    applySingleDateFilter(mainQuery, dates.getDueDate(), "dueDate");
    applySingleDateFilter(mainQuery, dates.getConfirmDate(), "orderSubmitDate");
    applySingleDateFilter(mainQuery, dates.getCreationDate(), "orderCreationDate");
}

private void applySingleDateFilter(BoolQuery.Builder mainQuery, DateRange range, String field) {
    if (range == null || !range.isValid()) return;

    mainQuery.must(QueryBuilders.range(r -> r
        .field(field)
        .gte(JsonData.of(range.getStartDate()))
        .lte(JsonData.of(range.getEndDate()))
    ));
}

private Highlight buildHighlightConfig() {
    return Highlight.of(h -> h
        .type(HighlighterType.Unified)
        .fields(EZStatusUtil.getGlobalSearchMap().keySet().stream()
            .collect(Collectors.toMap(
                Function.identity(),
                f -> HighlightField.of(hf -> hf)
            ))
        .preTags("<strong>")
        .postTags("</strong>")
    );
}

private Map<String, Aggregation> buildAggregations() {
    return EZStatusUtil.getAggregatedDataMap().entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> Aggregation.of(a -> a
                .terms(t -> t
                    .field(entry.getValue() + ".keyword")
                    .size(100)
                )
        ));
}

private void mapResultsToResponse(ResponseData responseData, SearchResponse<ElasticSearchOrderDetail> response) {
    // Map hits
    responseData.setRecords(response.hits().hits().stream()
        .map(SearchHit::source)
        .collect(Collectors.toList()));

    // Map highlights
    responseData.setHighlights(response.hits().hits().stream()
        .map(hit -> hit.highlight() != null ? hit.highlight() : Map.of())
        .collect(Collectors.toList()));

    // Map aggregations
    if (response.aggregations() != null) {
        Map<String, Map<String, Long>> aggregations = new HashMap<>();
        
        response.aggregations().forEach((name, agg) -> {
            if (agg.isTerms()) {
                aggregations.put(name, agg.terms().buckets().array().stream()
                    .collect(Collectors.toMap(
                        b -> b.key().toString(),
                        TermsBucket::docCount
                    )));
            }
        });
        
        responseData.setAggregationResults(aggregations);
    }
}
