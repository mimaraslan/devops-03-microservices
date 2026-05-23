package com.mimaraslan.tracing;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaTracingAutoConfiguration {

    @Bean
    KafkaMessagingObservationFilter kafkaMessagingObservationFilter() {
        return new KafkaMessagingObservationFilter();
    }

    @Bean
    KafkaObservationConfigurer kafkaObservationConfigurer(
            @Value("${spring.application.name}") String applicationName) {
        return new KafkaObservationConfigurer(applicationName);
    }

    static final class KafkaObservationConfigurer implements BeanPostProcessor {

        private final ServiceKafkaListenerObservationConvention listenerConvention;
        private final ServiceKafkaTemplateObservationConvention templateConvention;

        KafkaObservationConfigurer(String applicationName) {
            this.listenerConvention = new ServiceKafkaListenerObservationConvention(applicationName);
            this.templateConvention = new ServiceKafkaTemplateObservationConvention(applicationName);
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof KafkaTemplate<?, ?> template) {
                template.setObservationEnabled(true);
                template.setObservationConvention(templateConvention);
            }
            if (bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
                factory.getContainerProperties().setObservationEnabled(true);
                factory.getContainerProperties().setObservationConvention(listenerConvention);
            }
            return bean;
        }
    }
}
