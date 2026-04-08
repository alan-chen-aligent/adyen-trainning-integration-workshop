package com.adyen.workshop.controllers;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import org.apache.coyote.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.SignatureException;
import java.security.SignatureException;

/**
 * REST controller for receiving Adyen webhook notifications
 */
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

    // Step 16 - Validate the HMAC signature using the ADYEN_HMAC_KEY
    @PostMapping("/webhooks")
    public ResponseEntity<String> webhooks(@RequestBody String json) throws Exception {
        log.info("Received: {}", json);
        var notificationRequest = NotificationRequest.fromJson(json);
        var notificationRequestItem = notificationRequest.getNotificationItems().stream().findFirst();

        try {
            NotificationRequestItem item = notificationRequestItem.get();

            // Step 16 - Validate the HMAC signature using the ADYEN_HMAC_KEY
            if (!hmacValidator.validateHMAC(item, this.applicationConfiguration.getAdyenHmacKey())) {
                log.warn("Could not validate HMAC signature for incoming webhook message: {}", item);
                return ResponseEntity.unprocessableEntity().build();
            }

            // Success, log it for now
            log.info("Received webhook with event {}", item.toString());

            switch (item.getEventCode()) {
                case "RECURRING_CONTRACT" -> {
                    // Tokenization: extract recurringDetailReference
                    var additionalData = item.getAdditionalData();
                    String recurringDetailReference = additionalData != null
                            ? additionalData.get("recurring.recurringDetailReference")
                            : null;
                    log.info("RECURRING_CONTRACT received - recurringDetailReference (token): {}", recurringDetailReference);
                }
                case "AUTHORISATION" -> {
                    log.info("AUTHORISATION received - pspReference: {} success: {}",
                            item.getPspReference(), item.isSuccess());
                }
                case "AUTHORISATION_ADJUSTMENT" -> {
                    log.info("AUTHORISATION_ADJUSTMENT received - pspReference: {} originalReference: {} success: {}",
                            item.getPspReference(), item.getOriginalReference(), item.isSuccess());
                }
                case "CAPTURE" -> {
                    log.info("CAPTURE received - pspReference: {} originalReference: {} success: {}",
                            item.getPspReference(), item.getOriginalReference(), item.isSuccess());
                }
                case "CAPTURE_FAILED" -> {
                    log.warn("CAPTURE_FAILED received - pspReference: {} originalReference: {} reason: {}",
                            item.getPspReference(), item.getOriginalReference(), item.getReason());
                }
                case "CANCELLATION" -> {
                    log.info("CANCELLATION received - pspReference: {} originalReference: {} success: {}",
                            item.getPspReference(), item.getOriginalReference(), item.isSuccess());
                }
                case "TECHNICAL_CANCEL" -> {
                    log.warn("TECHNICAL_CANCEL received - pspReference: {} originalReference: {}",
                            item.getPspReference(), item.getOriginalReference());
                }
                case "REFUND" -> {
                    log.info("REFUND received - pspReference: {} originalReference: {} success: {}",
                            item.getPspReference(), item.getOriginalReference(), item.isSuccess());
                }
                case "REFUND_FAILED" -> {
                    log.warn("REFUND_FAILED received - pspReference: {} originalReference: {} reason: {}",
                            item.getPspReference(), item.getOriginalReference(), item.getReason());
                }
                case "REFUNDED_REVERSED" -> {
                    log.warn("REFUNDED_REVERSED received - pspReference: {} originalReference: {}",
                            item.getPspReference(), item.getOriginalReference());
                }
                default -> log.info("Unhandled event code: {}", item.getEventCode());
            }

            return ResponseEntity.accepted().build();
        } catch (SignatureException e) {
            // Handle invalid signature
            return ResponseEntity.unprocessableEntity().build();
        } catch (Exception e) {
            // Handle all other errors
            return ResponseEntity.status(500).build();
        }
    }
}