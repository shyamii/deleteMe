@Component
public class Step1Decider implements JobExecutionDecider {

    @Value("${myapp.step1-enabled:false}")
    private boolean step1Enabled;

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        return step1Enabled ? new FlowExecutionStatus("STEP1_ENABLED")
                            : new FlowExecutionStatus("STEP1_DISABLED");
    }
}
@Bean
    public Job conditionalJob(JobBuilder jobBuilder) {
        return jobBuilder
            .repository(jobRepository)
            .start(step1Decider)
                .on("STEP1_ENABLED").to(step1()).next(step2()).next(step3()).next(step4())
            .from(step1Decider)
                .on("STEP1_DISABLED").to(step2()).next(step3()).next(step4())
            .from(step4())
                .on("*").end()
            .build();
    }
