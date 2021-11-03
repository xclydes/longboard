package com.xclydes.finance.longboard.config;

import com.xclydes.finance.longboard.component.TokenResolver;
import com.xclydes.finance.longboard.graphql.DateCoercing;
import com.xclydes.finance.longboard.util.DatesUtil;
import graphql.schema.GraphQLScalarType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.scheduling.SchedulingTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Field;
import java.util.List;

@Configuration
@Slf4j
public class GraphQLConfig {

    @Bean
    public RuntimeWiringConfigurer longboardScalarConfigurer() {
        final GraphQLScalarType dateType = GraphQLScalarType.newScalar()
                .coercing(new DateCoercing(DatesUtil.formatterSQL(), DateCoercing.SupplyNow))
                .name("Date")
                .description("Support for dates in the yyyy-MM-dd format. Assumes today when an empty non-null string.")
                .build();
        // Provide these via a builder
        return (wiringBuilder) -> wiringBuilder
                .scalar(dateType)
                ;
    }

    @Bean
    public TokenResolver graphQLTokenResolver(final AnnotatedControllerConfigurer controllerConfigurer) {
        final TokenResolver tokenResolver = new TokenResolver();
        // Sometimes in life we have to do things we shouldn't because people didn't do what they should have
        // Spring-Graphql doesn't behave like a proper spring implementation and use HandlerMethodArgumentResolver components
        // So........ Forcefully add our resolver to the list
        try {
            final Field argumentResolversCompFld = AnnotatedControllerConfigurer.class.getDeclaredField("argumentResolvers");
            argumentResolversCompFld.setAccessible(true);
            // Get the current value
            final HandlerMethodArgumentResolverComposite composite = (HandlerMethodArgumentResolverComposite) argumentResolversCompFld.get(controllerConfigurer);
            // Get the underlying list
            final Field argumentResolversFld = HandlerMethodArgumentResolverComposite.class.getDeclaredField("argumentResolvers");
            argumentResolversFld.setAccessible(true);
            // Get the actual list
            final List<HandlerMethodArgumentResolver> lst = (List<HandlerMethodArgumentResolver>) argumentResolversFld.get(composite);
            // Add our resolver at the beginning of the list
            lst.add(4, tokenResolver);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return tokenResolver;
    }

    @Bean({"longboardExecutor", "longboardTaskExecutor"})
    public SchedulingTaskExecutor longboardTaskExecutor(@Value("${longboard.executor.size}") final int size,
                                                        @Value("${longboard.executor.prefix}") final String prefix) {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(size);
        executor.setThreadNamePrefix(prefix);
        executor.initialize();
        return executor;
    }
}
