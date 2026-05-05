package com.adyen.workshop.controllers;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.SignatureException;

@RestController
public class WebhookController {
    private final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ApplicationConfiguration applicationConfiguration;
    private final HMACValidator hmacValidator;

    @Autowired
    public WebhookController(ApplicationConfiguration applicationConfiguration, HMACValidator hmacValidator) {
        this.applicationConfiguration = applicationConfiguration;
        this.hmacValidator = hmacValidator;
    }

    @PostMapping("/webhooks")
    public ResponseEntity<String> webhooks(@RequestBody String json) throws Exception {
        var notificationRequest = NotificationRequest.fromJson(json);

        for (var item : notificationRequest.getNotificationItems()) {
            if (!hmacValidator.validateHMAC(item, applicationConfiguration.getAdyenHmacKey())) {
                log.warn("HMAC validation failed for pspReference={}", item.getPspReference());
                return ResponseEntity.badRequest().body("Invalid HMAC signature");
            }

            handleNotification(item);
        }

        return ResponseEntity.accepted().body("[accepted]");
    }

    private void handleNotification(NotificationRequestItem notification) {
        var eventCode = notification.getEventCode();
        var pspReference = notification.getPspReference();
        var success = Boolean.TRUE.equals(notification.isSuccess());
        var amount = notification.getAmount();

        log.info("Webhook: eventCode={}, pspReference={}, success={}, amount={}", eventCode, pspReference, success, amount);

        switch (eventCode) {
            case "AUTHORISATION" -> {
                if (success) {
                    log.info("Payment authorised - pspReference: {}", pspReference);
                } else {
                    log.warn("Payment authorisation failed - pspReference: {}", pspReference);
                }
            }
            case "AUTHORISATION_ADJUSTMENT" -> {
                if (success) {
                    log.info("Authorisation adjusted - pspReference: {}", pspReference);
                } else {
                    log.warn("Authorisation adjustment failed - pspReference: {}", pspReference);
                }
            }
            case "CAPTURE" -> {
                if (success) {
                    log.info("Payment captured - pspReference: {}", pspReference);
                } else {
                    log.warn("Capture failed - pspReference: {}", pspReference);
                }
            }
            case "CAPTURE_FAILED" -> log.warn("Capture failed (CAPTURE_FAILED) - pspReference: {}", pspReference);
            case "CANCELLATION" -> {
                if (success) {
                    log.info("Payment cancelled - pspReference: {}", pspReference);
                } else {
                    log.warn("Cancellation failed - pspReference: {}", pspReference);
                }
            }
            case "TECHNICAL_CANCEL" -> log.warn("Technical cancel - pspReference: {}", pspReference);
            case "REFUND" -> {
                if (success) {
                    log.info("Payment refunded - pspReference: {}", pspReference);
                } else {
                    log.warn("Refund failed - pspReference: {}", pspReference);
                }
            }
            case "REFUND_FAILED" -> log.warn("Refund failed (REFUND_FAILED) - pspReference: {}", pspReference);
            case "REFUNDED_REVERSED" -> log.info("Refund reversed - pspReference: {}", pspReference);
            default -> log.info("Unhandled event: {} - pspReference: {}", eventCode, pspReference);
        }
    }
}
