@Bean
public JdbcCursorItemReader<Long> idReader(DataSource dataSource) {
    JdbcCursorItemReader<Long> reader = new JdbcCursorItemReader<>();
    reader.setDataSource(dataSource);
    reader.setSql("""
        SELECT a.id
        FROM table_a a
        JOIN table_b b ON a.some_id = b.some_id
        WHERE a.status = 'inactive' AND b.deleted = 'N'
    """); // your full custom query here

    reader.setRowMapper((rs, rowNum) -> rs.getLong("id"));
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
