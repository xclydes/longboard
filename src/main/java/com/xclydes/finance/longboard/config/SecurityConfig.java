package com.xclydes.finance.longboard.config;

/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain springWebFilterChain(final ServerHttpSecurity http) throws Exception {
        return http
                .cors().and()
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // Demonstrate that method security works
                // Best practice to use both for defense in depth
                .authorizeExchange(requests -> requests.anyExchange().permitAll())
                .httpBasic(withDefaults())
                .build();
    }

//    @Bean
//    @SuppressWarnings("deprecation")
//    public MapReactiveUserDetailsService userDetailsService() {
//        User.UserBuilder userBuilder = User.withDefaultPasswordEncoder();
//        UserDetails rob = userBuilder.username("rob").password("rob").roles("USER").build();
//        UserDetails admin = userBuilder.username("admin").password("admin").roles("USER", "ADMIN").build();
//        return new MapReactiveUserDetailsService(rob, admin);
//    }

}
