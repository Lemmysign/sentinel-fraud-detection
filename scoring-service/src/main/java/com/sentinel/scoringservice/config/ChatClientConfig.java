package com.sentinel.scoringservice.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient configuration.
 *
 * ChatClient is Spring AI's fluent interface for
 * calling AI models. It abstracts over the raw
 * HTTP call to Groq's API.
 *
 * The ChatClient.Builder is auto-configured by
 * Spring AI when spring-ai-starter-model-openai
 * is on the classpath and the following properties
 * are set:
 *
 * spring.ai.openai.base-url=https://api.groq.com/openai
 * spring.ai.openai.api-key=${GROQ_API_KEY}
 * spring.ai.openai.chat.options.model=llama-3.3-70b-versatile
 *
 * Spring AI detects these and wires a ChatClient.Builder
 * pre-configured for Groq. We just build the client here.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}