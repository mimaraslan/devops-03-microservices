package com.mimaraslan.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.kafka.support.micrometer.KafkaRecordReceiverContext;
import org.springframework.kafka.support.micrometer.KafkaRecordSenderContext;

import java.util.Map;

/**
 * Kafka span'lerinde broker (Apache Kafka: redpanda...) yerine karsi mikroservisi gosterir.
 */
public class KafkaMessagingObservationFilter implements ObservationFilter {

    /** Mesaji tuketen servis (producer span remote endpoint). */
    private static final Map<String, String> TOPIC_TO_CONSUMER_SERVICE = Map.of(
            "fraud-check-events", "fraud-service-application",
            "fraud-result-events", "ledger-service-application",
            "notification-transfer-events", "notification-service-application"
    );

    /** Mesaji ureten servis (consumer span remote endpoint). */
    private static final Map<String, String> TOPIC_TO_PRODUCER_SERVICE = Map.of(
            "fraud-check-events", "ledger-service-application",
            "fraud-result-events", "fraud-service-application",
            "notification-transfer-events", "ledger-service-application"
    );

    @Override
    public Observation.Context map(Observation.Context context) {
        if (context instanceof KafkaRecordSenderContext sender) {
            String peer = TOPIC_TO_CONSUMER_SERVICE.get(sender.getDestination());
            if (peer != null) {
                sender.setRemoteServiceName(peer);
            }
        } else if (context instanceof KafkaRecordReceiverContext receiver) {
            String peer = TOPIC_TO_PRODUCER_SERVICE.get(receiver.getSource());
            if (peer != null) {
                receiver.setRemoteServiceName(peer);
            }
        }
        return context;
    }
}
