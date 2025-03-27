public ResponseData getGlobalSearchData(String gsamSensitivity, String isGsamCheckRequired,
        String federalAccessStatus, String searchId, GlobalSearchRequest globalSearchRequest, String matchType) {
    
    // Initialize response and log input parameters
    ResponseData responseData = new ResponseData();
    log.info("Starting global search with parameters: searchId={}, matchType={}, gsamCheck={}, federalStatus={}",
            searchId, matchType, isGsamCheckRequired, federalAccessStatus);
    
    try {
        // 1. Highlight Configuration
        Map<String, HighlightField> highlights = configureHighlighting();
        
        // 2. Field Lists Preparation
        List<String> exactMatchFields = new ArrayList<>();
        List<String> partialMatchFields = new ArrayList<>();
        prepareFieldLists(exactMatchFields, partialMatchFields);
        
        // 3. Build Base Query
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder()
                .index("order_details_alias")
                .size(1000);
        
        // 4. Search Query Construction
        buildSearchQuery(boolQueryBuilder, exactMatchFields, partialMatchFields, searchId, matchType);
        
        // 5. GSAM Sensitivity Filter
        if ("true".equals(isGsamCheckRequired)) {
            applyGsamFilters(boolQueryBuilder, gsamSensitivity);
        }
        
        // 6. Federal Access Filter
        if ("No".equalsIgnoreCase(federalAccessStatus)) {
            boolQueryBuilder.mustNot(mustNot -> mustNot
                    .term(term -> term.field("federalFlag.keyword").value("FEDERAL")));
        }
        
        // 7. Additional Filters
        applyAdditionalFilters(boolQueryBuilder, globalSearchRequest);
        
        // 8. Date Range Filters
        applyDateRangeFilters(boolQueryBuilder, globalSearchRequest);
        
        // 9. Aggregations
        configureAggregations(searchRequestBuilder);
        
        // 10. Execute Search
        searchRequestBuilder
                .query(boolQueryBuilder.minimumShouldMatch("1").build()._toQuery())
                .highlight(Highlight.of(h -> h
                        .type(HighlighterType.Unified)
                        .fields(highlights)
                        .preTags("")
                        .postTags("")));
        
        log.debug("Final Elasticsearch query: {}", searchRequestBuilder.toString());
        SearchResponse<ElasticSearchOrderDetail> response = 
                esClient.search(searchRequestBuilder.build(), ElasticSearchOrderDetail.class);
        
        responseData.setSearchResponse(response);
        log.info("Successfully executed search with {} results", response.hits().total().value());
        
    } catch (Exception e) {
        log.error("Failed to execute global search for searchId: {}", searchId, e);
        responseData.setError(e.getMessage());
    }
    
    return responseData;
}

// Helper Methods

private Map<String, HighlightField> configureHighlighting() {
    Map<String, HighlightField> highlights = new HashMap<>();
    Set<String> highLightData = EZStatusUtil.getGlobalSearchMap().keySet();
    highLightData.forEach(field -> {
        highlights.put(field + ".keyword", HighlightField.of(hf -> hf));
        highlights.put(field, HighlightField.of(hf -> hf));
    });
    return highlights;
}

private void prepareFieldLists(List<String> exactMatchFields, List<String> partialMatchFields) {
    EZStatusUtil.getGlobalSearchMap().forEach((key, value) -> {
        exactMatchFields.add(key + ".keyword");
        partialMatchFields.add(key);
    });
}

private void buildSearchQuery(BoolQuery.Builder boolQueryBuilder, 
        List<String> exactMatchFields, List<String> partialMatchFields,
        String searchId, String matchType) {
    
    if ("exact".equals(matchType)) {
        boolQueryBuilder.should(QueryBuilders.multiMatch(m -> m
                .query(searchId.toUpperCase())
                .fields(exactMatchFields)));
    } else {
        // Exact match on keyword fields
        boolQueryBuilder.should(QueryBuilders.multiMatch(m -> m
                .query(searchId.toUpperCase())
                .fields(exactMatchFields)));
        
        // Partial match on text fields
        partialMatchFields.forEach(field -> {
            boolQueryBuilder.should(QueryBuilders.matchPhrasePrefix(m -> m
                    .field(field)
                    .query(searchId)
                    .maxExpansions(10)));
        });
    }
}

private void applyGsamFilters(BoolQuery.Builder boolQueryBuilder, String gsamSensitivity) {
    if (gsamSensitivity != null) {
        String[] gsamList = gsamSensitivity.split("[,|^]");
        BoolQuery.Builder gsamQuery = QueryBuilders.bool();
        
        Arrays.stream(gsamList)
              .forEach(gsamValue -> gsamQuery.should(should -> should
                      .wildcard(w -> w.field("gsamSensitivityLevel").value("*" + gsamValue + "*"))));
        
        boolQueryBuilder.should(gsamQuery.build()._toQuery());
        boolQueryBuilder.should(should -> should.bool(bool -> bool
                .mustNot(mustNot -> mustNot.exists(exists -> exists.field("gsamSensitivityLevel")))));
    }
}

private void applyAdditionalFilters(BoolQuery.Builder boolQueryBuilder, GlobalSearchRequest request) {
    if (request.getFilters() != null) {
        GlobalSearchFilters filters = request.getFilters();
        
        // Helper method to avoid repetition
        BiConsumer<String, List<String>> addFilter = (fieldName, values) -> {
            if (values != null && !values.isEmpty()) {
                BoolQuery.Builder filterQuery = QueryBuilders.bool();
                values.forEach(value -> filterQuery.should(should -> should
                        .term(t -> t.field(fieldName + ".keyword").value(value).caseInsensitive(true))));
                boolQueryBuilder.should(filterQuery.minimumShouldMatch("1").build()._toQuery());
            }
        };
        
        addFilter.accept("fulfillmentStatus", filters.getFulfillmentStatus());
        addFilter.accept("crStatus", filters.getCrStatus());
        addFilter.accept("centerName", filters.getCenterName());
        addFilter.accept("source", filters.getSource());
        addFilter.accept("workType", filters.getWorkType());
        addFilter.accept("queueName", filters.getQueueName());
        addFilter.accept("orderActivity", filters.getOrderActivity());
        addFilter.accept("taskName", filters.getTaskName());
        addFilter.accept("productType", filters.getProductType());
    }
    
    if (request.getOwner() != null && !request.getOwner().isEmpty()) {
        boolQueryBuilder.should(should -> should
                .term(t -> t.field("userName.keyword")
                           .value(request.getOwner())
                           .caseInsensitive(true)));
    }
}

private void applyDateRangeFilters(BoolQuery.Builder boolQueryBuilder, GlobalSearchRequest request) {
    if (request.getDates() != null) {
        GlobalSearchDates dates = request.getDates();
        
        // Helper method for date ranges
        BiConsumer<String, DateRange> addDateRange = (fieldName, dateRange) -> {
            if (dateRange != null && dateRange.getStartDate() != null && dateRange.getEndDate() != null) {
                boolQueryBuilder.should(should -> should.range(r -> r
                        .field(fieldName)
                        .from(dateRange.getStartDate() + " 00:00:00")
                        .to(dateRange.getEndDate() + " 00:00:00")));
            }
        };
        
        addDateRange.accept("dueDate", dates.getDueDate());
        addDateRange.accept("orderSubmitDate", dates.getConfirmDate());
        addDateRange.accept("orderCreationDate", dates.getCreationDate());
    }
}

private void configureAggregations(SearchRequest.Builder searchRequestBuilder) {
    EZStatusUtil.getAggregatedDataMap().forEach((key, value) -> {
        searchRequestBuilder.aggregations(key, Aggregation.of(a -> a
                .terms(t -> t.field(value + ".keyword").size(2000)));
    });
}
