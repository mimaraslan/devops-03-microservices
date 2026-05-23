package com.mimaraslan.tracing;

import io.micrometer.common.KeyValues;
import org.springframework.kafka.support.micrometer.KafkaListenerObservation;
import org.springframework.kafka.support.micrometer.KafkaListenerObservationConvention;
import org.springframework.kafka.support.micrometer.KafkaRecordReceiverContext;

/**
 * Span adlarinda Kafka topic gosterilmez; servis adi kullanilir.
 */
public class ServiceKafkaListenerObservationConvention implements KafkaListenerObservationConvention {

    private final String applicationName;
    private final KafkaListenerObservationConvention delegate =
            KafkaListenerObservation.DefaultKafkaListenerObservationConvention.INSTANCE;

    public ServiceKafkaListenerObservationConvention(String applicationName) {
        this.applicationName = applicationName;
    }

    @Override
    public String getName() {
        return applicationName;
    }

    @Override
    public String getContextualName(KafkaRecordReceiverContext context) {
        return applicationName + " receive";
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(KafkaRecordReceiverContext context) {
        return delegate.getLowCardinalityKeyValues(context);
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(KafkaRecordReceiverContext context) {
        return KeyValues.empty();
    }
}
