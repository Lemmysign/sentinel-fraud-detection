package com.sentinel.rulesengineservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for rules-engine-service.
 *
 * Why Lettuce over Jedis?
 * Lettuce is the modern Spring Redis client.
 * It is non-blocking and thread-safe — a single
 * connection handles all concurrent requests.
 * Jedis requires a connection pool (one connection
 * per thread). Under high concurrency Lettuce
 * is significantly more efficient.
 *
 * Why StringRedisSerializer for both key and value?
 * All our Redis data is text:
 * - Keys: "velocity:ACC-001", "devices:ACC-001"
 * - Values: counters ("42"), coordinates ("6.5,3.4,2024...")
 *
 * String serializer keeps Redis data human-readable.
 * You can inspect any key directly with redis-cli
 * without decoding binary formats.
 *
 * Using RedisStandaloneConfiguration for explicit setup:
 * More readable than relying on auto-configuration,
 * and easier to extend to Redis Sentinel or Cluster
 * in production by swapping the configuration object.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(
                        redisHost, redisPort);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, String> template =
                new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer serializer =
                new StringRedisSerializer();

        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        // Must call afterPropertiesSet when building
        // template manually outside Spring lifecycle
        template.afterPropertiesSet();

        return template;
    }
}