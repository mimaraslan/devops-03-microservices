package com.mimaraslan.kafka.consumer;

import com.mimaraslan.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "notification-transfer-events",
            groupId = "notification-service-group")
    @RetryableTopic(
            attempts = "5",  // maksimum 5 deneme
            backoff = @Backoff(
                         delay = 2000,  // ilk deneme gecikmesi 2 saniye
                         multiplier = 2) // her retry’de gecikmeyi 2 kat artır
    )
    public void consume(String message) {
        System.out.println("NotificationService received: " + message);
        notificationService.sendNotification(message);
    }
}


