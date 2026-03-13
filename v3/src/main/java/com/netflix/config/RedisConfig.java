package com.netflix.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configures how Spring talks to Redis.
 *
 * We serialize values as JSON (not Java binary serialization).
 * This means you can inspect cached data with:
 *   redis-cli GET netflix:top-directors:10
 * and see readable JSON — useful for debugging and demos.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Keys are plain strings like "netflix:top-directors:10"
        template.setKeySerializer(new StringRedisSerializer());

        // Values are JSON so we can inspect them in redis-cli
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

        return template;
    }
}
