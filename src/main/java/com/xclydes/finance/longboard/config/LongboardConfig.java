package com.xclydes.finance.longboard.config;

import com.xclydes.finance.longboard.component.TokenResolver;
import com.xclydes.finance.longboard.graphql.DateCoercing;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.util.DatesUtil;
import graphql.schema.GraphQLScalarType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.web.WebInterceptor;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.SchedulingTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

@Configuration
@Slf4j
public class LongboardConfig {

    @Bean({"longboardExecutor", "longboardTaskExecutor"})
    public SchedulingTaskExecutor longboardTaskExecutor(@Value("${longboard.executor.size}") final int size,
                                                        @Value("${longboard.executor.prefix}") final String prefix) {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(size);
        executor.setThreadNamePrefix(prefix);
        executor.initialize();
        return executor;
    }

    @Bean({"longboardScheduler", "longboardTaskScheduler"})
    public Scheduler longboardScheduler(@Qualifier("longboardExecutor") final SchedulingTaskExecutor executor) {
        return Schedulers.fromExecutor(executor);
    }
}
