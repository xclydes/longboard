package com.xclydes.finance.longboard.config;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WaveConfig {

    @Bean("longboardWaveCacheKeyGen")
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            final Object[] compoundArgs = new Object[params.length + 3];
            compoundArgs[0] = "wave";
            compoundArgs[1] = method.getDeclaringClass().getName();
            compoundArgs[2] = method.getName();
            System.arraycopy(params, 0, compoundArgs, 3, params.length);
            return new SimpleKey(compoundArgs);
        };
    }


}
