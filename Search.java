GET my_index/_search
{
  "query": {
    "script_score": {
      "script": {
        "source": "for (def entry : params._source.entrySet()) { if (entry.getValue().toString().matches('.*[^a-zA-Z0-9].*')) return 1; } return 0;"
      }
    }
  }
}





public ResponseData getGlobalSearchData(String gsamSensitivity, String isGsamCheckRequired,
        String federalAccessStatus, String searchId, GlobalSearchRequest globalSearchRequest, String matchType) {
    
    ResponseData responseData = new ResponseData();
    log.info("Starting global search for: {}", searchId);

    try {
        // 1. Highlight Configuration
        Map<String, HighlightField> highlights = new HashMap<>();
        EZStatusUtil.getGlobalSearchMap().keySet().forEach(field -> {
            highlights.put(field + ".keyword", HighlightField.of(hf -> hf));
            highlights.put(field, HighlightField.of(hf -> hf));
        });

        // 2. Initialize Query Builders
        BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();
        SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder()
                .index("order_details_alias")
                .size(1000);

        // 3. Search Query
        if (!searchId.isBlank()) {
            log.debug("Building search query for: {}", searchId);
            if ("exact".equalsIgnoreCase(matchType)) {
                // Exact match on keyword fields
                boolQueryBuilder.must(QueryBuilders.multiMatch(m -> m
                        .query(searchId.toUpperCase())
                        .fields(EZStatusUtil.getGlobalSearchMap().keySet().stream()
                                .map(f -> f + ".keyword")
                                .collect(Collectors.toList()))
                ));
            } else {
                // Partial match using wildcard on keyword fields
                BoolQuery.Builder searchBool = QueryBuilders.bool();
                EZStatusUtil.getGlobalSearchMap().keySet().forEach(field -> {
                    searchBool.should(QueryBuilders.wildcard(w -> w
                            .field(field + ".keyword")
                            .value("*" + searchId.toUpperCase() + "*")
                    );
                });
                boolQueryBuilder.must(searchBool.build()._toQuery());
            }
        }

        // 4. GSAM Filter (MUST clause)
        if ("true".equalsIgnoreCase(isGsamCheckRequired)) {
            log.debug("Applying GSAM filters");
            BoolQuery.Builder gsamQuery = QueryBuilders.bool();
            Arrays.stream(gsamSensitivity.split("[,|^]"))
                  .forEach(gsam -> gsamQuery.should(QueryBuilders.wildcard(w -> w
                          .field("gsamSensitivityLevel.keyword")
                          .value("*" + gsam + "*")
                  )));
            boolQueryBuilder.must(gsamQuery.build()._toQuery());
        }

        // 5. Federal Flag Filter (MUST clause)
        if ("No".equalsIgnoreCase(federalAccessStatus)) {
            log.debug("Applying federal filter");
            boolQueryBuilder.mustNot(QueryBuilders.term(t -> t
                    .field("federalFlag.keyword")
                    .value("FEDERAL"))
            );
        }

        // 6. Additional Filters (MUST clauses)
        if (globalSearchRequest.getFilters() != null) {
            log.debug("Applying additional filters");
            GlobalSearchFilters filters = globalSearchRequest.getFilters();

            // Helper to add filter groups
            BiConsumer<String, List<String>> addFilter = (fieldName, values) -> {
                if (values != null && !values.isEmpty()) {
                    BoolQuery.Builder filterQuery = QueryBuilders.bool();
                    values.forEach(value -> filterQuery.should(QueryBuilders.term(t -> t
                            .field(fieldName + ".keyword")
                            .value(value)
                            .caseInsensitive(true)
                    ));
                    boolQueryBuilder.must(filterQuery.build()._toQuery());
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

        // 7. Date Filters (MUST clauses)
        if (globalSearchRequest.getDates() != null) {
            log.debug("Applying date filters");
            GlobalSearchDates dates = globalSearchRequest.getDates();
            BiConsumer<String, DateRange> addDateFilter = (fieldName, dateRange) -> {
                if (dateRange != null && dateRange.getStartDate() != null && dateRange.getEndDate() != null) {
                    boolQueryBuilder.must(QueryBuilders.range(r -> r
                            .field(fieldName)
                            .from(dateRange.getStartDate() + " 00:00:00")
                            .to(dateRange.getEndDate() + " 00:00:00")
                    );
                }
            };
            addDateFilter.accept("dueDate", dates.getDueDate());
            addDateFilter.accept("orderSubmitDate", dates.getConfirmDate());
            addDateFilter.accept("orderCreationDate", dates.getCreationDate());
        }

        // 8. Aggregations
        EZStatusUtil.getAggregatedDataMap().forEach((aggName, fieldName) -> {
            searchRequestBuilder.aggregations(aggName, Aggregation.of(a -> a
                    .terms(t -> t.field(fieldName + ".keyword").size(2000)))
            );
        });

        // 9. Execute Query
        searchRequestBuilder
                .query(boolQueryBuilder.build()._toQuery())
                .highlight(Highlight.of(h -> h
                        .type(HighlighterType.Unified)
                        .fields(highlights)
                        .preTags("")
                        .postTags("")));

        log.debug("Final Query: {}", searchRequestBuilder.toString());
        SearchResponse<ElasticSearchOrderDetail> response = 
                esClient.search(searchRequestBuilder.build(), ElasticSearchOrderDetail.class);
        
        responseData.setSearchResponse(response);
        log.info("Search completed with {} hits", response.hits().hits().size());

    } catch (Exception e) {
        log.error("Search failed for ID: {}", searchId, e);
        responseData.setError("Search failed: " + e.getMessage());
    }

    return responseData;
}
