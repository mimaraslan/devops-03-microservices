package com.mimaraslan.tracing;

import io.micrometer.common.KeyValues;
import org.springframework.kafka.support.micrometer.KafkaRecordSenderContext;
import org.springframework.kafka.support.micrometer.KafkaTemplateObservation;
import org.springframework.kafka.support.micrometer.KafkaTemplateObservationConvention;

/**
 * Span adlarinda Kafka topic gosterilmez; servis adi kullanilir.
 */
public class ServiceKafkaTemplateObservationConvention implements KafkaTemplateObservationConvention {

    private final String applicationName;
    private final KafkaTemplateObservationConvention delegate =
            KafkaTemplateObservation.DefaultKafkaTemplateObservationConvention.INSTANCE;

    public ServiceKafkaTemplateObservationConvention(String applicationName) {
        this.applicationName = applicationName;
    }

    @Override
    public String getName() {
        return applicationName;
    }

    @Override
    public String getContextualName(KafkaRecordSenderContext context) {
        return applicationName + " send";
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(KafkaRecordSenderContext context) {
        return delegate.getLowCardinalityKeyValues(context);
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(KafkaRecordSenderContext context) {
        return KeyValues.empty();
    }
}
