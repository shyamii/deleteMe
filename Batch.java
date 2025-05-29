@Bean
public JdbcPagingItemReader<Long> idReader(DataSource dataSource) {
    JdbcPagingItemReader<Long> reader = new JdbcPagingItemReader<>();
    reader.setDataSource(dataSource);
    reader.setPageSize(100);
    reader.setRowMapper((rs, rowNum) -> rs.getLong("id"));

    SqlPagingQueryProviderFactoryBean queryProvider = new SqlPagingQueryProviderFactoryBean();
    queryProvider.setDataSource(dataSource);
    queryProvider.setSelectClause("SELECT id");
    queryProvider.setFromClause("FROM your_table");
    queryProvider.setSortKey("id");

    try {
        reader.setQueryProvider(queryProvider.getObject());
    } catch (Exception e) {
        throw new RuntimeException("Error setting query provider", e);
    }

    return reader;
}


@Bean
public ItemWriter<Long> bulkDeleteWriter(ElasticsearchClient elasticsearchClient) {
    return ids -> {
        if (ids == null || ids.isEmpty()) return;

        List<BulkOperation> operations = ids.stream()
            .map(id -> BulkOperation.of(b -> b
                .delete(del -> del
                    .index("your-index-name") // replace with your index name
                    .id(String.valueOf(id))
                )
            ))
            .toList();

        BulkRequest request = BulkRequest.of(b -> b.operations(operations));

        BulkResponse response = elasticsearchClient.bulk(request);

        if (response.errors()) {
            response.items().forEach(item -> {
                if (item.error() != null) {
                    System.err.println("Failed to delete ID: " + item.delete().id()
                        + ", reason: " + item.error().reason());
                }
            });
        }
    };
}


@Bean
public Step deleteFromElasticStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   JdbcPagingItemReader<Long> idReader,
                                   ItemWriter<Long> bulkDeleteWriter) {
    return new StepBuilder("deleteFromElasticStep", jobRepository)
            .<Long, Long>chunk(100, transactionManager)
            .reader(idReader)
            .writer(bulkDeleteWriter)
            .build();
}
