package com.xclydes.finance.longboard.config;

import com.xclydes.finance.longboard.component.TokenResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;

import java.lang.reflect.Field;
import java.util.List;

@Configuration
@Slf4j
public class GraphQLConfig {

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
            lst.add(0, tokenResolver);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return tokenResolver;
    }
}
