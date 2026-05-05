package com.adyen.workshop.controllers;

import com.adyen.model.checkout.*;
import com.adyen.service.checkout.ModificationsApi;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.UUID;

@RestController
public class ApiController {
    private final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final ApplicationConfiguration applicationConfiguration;
    private final PaymentsApi paymentsApi;
    private final ModificationsApi modificationsApi;

    public ApiController(ApplicationConfiguration applicationConfiguration, PaymentsApi paymentsApi, ModificationsApi modificationsApi) {
        this.applicationConfiguration = applicationConfiguration;
        this.paymentsApi = paymentsApi;
        this.modificationsApi = modificationsApi;
    }

    // Step 0
    @GetMapping("/hello-world")
    public ResponseEntity<String> helloWorld() throws Exception {
        return ResponseEntity.ok().body("This is the 'Hello World' from the workshop - You've successfully finished step 0!");
    }

    // Step 7 - Retrieve available payment methods
    @PostMapping("/api/paymentMethods")
    public ResponseEntity<PaymentMethodsResponse> paymentMethods() throws IOException, ApiException {
        var request = new PaymentMethodsRequest()
                .merchantAccount(applicationConfiguration.getAdyenMerchantAccount())
                .countryCode("NL")
                .shopperLocale("nl-NL")
                .amount(new Amount().currency("EUR").value(9998L))
                .channel(PaymentMethodsRequest.ChannelEnum.WEB);

        var response = paymentsApi.paymentMethods(request);
        return ResponseEntity.ok().body(response);
    }

    // Step 9 - Initiate a payment
    @PostMapping("/api/payments")
    public ResponseEntity<PaymentResponse> payments(@RequestBody PaymentRequest body) throws IOException, ApiException {
        body.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        body.setAmount(new Amount().currency("EUR").value(9998L));
        body.setReference("order-" + UUID.randomUUID());
        body.setReturnUrl("http://localhost:" + applicationConfiguration.getServerPort() + "/handleShopperRedirect");
        body.setCountryCode("NL");

        log.info("Payments request: merchantAccount={}, amount={}", body.getMerchantAccount(), body.getAmount());
        var response = paymentsApi.payments(body);
        return ResponseEntity.ok().body(response);
    }

    // Step 13 - Handle additional details (Native 3DS2 flow)
    @PostMapping("/api/payments/details")
    public ResponseEntity<PaymentDetailsResponse> paymentsDetails(@RequestBody PaymentDetailsRequest detailsRequest) throws IOException, ApiException {
        var response = paymentsApi.paymentsDetails(detailsRequest);
        return ResponseEntity.ok().body(response);
    }

    // Step 14 - Handle redirect back from 3DS2
    @GetMapping("/handleShopperRedirect")
    public RedirectView redirect(
            @RequestParam(required = false) String payload,
            @RequestParam(required = false) String redirectResult) throws IOException, ApiException {

        var completionDetails = new PaymentCompletionDetails();
        if (redirectResult != null && !redirectResult.isEmpty()) {
            completionDetails.redirectResult(redirectResult);
        } else if (payload != null && !payload.isEmpty()) {
            completionDetails.payload(payload);
        }

        var detailsRequest = new PaymentDetailsRequest().details(completionDetails);
        var response = paymentsApi.paymentsDetails(detailsRequest);
        return getRedirectView(response.getResultCode());
    }

    // PreAuth Step 1 - Create a payment with manual capture (captureDelayHours=0)
    @PostMapping("/api/preauthorisation")
    public ResponseEntity<PaymentResponse> preAuthorisation(@RequestBody PaymentRequest body) throws IOException, ApiException {
        body.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        body.setAmount(new Amount().currency("USD").value(1000L));
        body.setReference("preauth-" + UUID.randomUUID());
        body.setReturnUrl("http://localhost:" + applicationConfiguration.getServerPort() + "/handleShopperRedirect");
        body.setCountryCode("US");
        body.setCaptureDelayHours(0);

        log.info("PreAuth request: amount={}", body.getAmount());
        var response = paymentsApi.payments(body);
        return ResponseEntity.ok().body(response);
    }

    // PreAuth Step 2 - Adjust the authorised amount
    @PostMapping("/api/modify-amount")
    public ResponseEntity<PaymentAmountUpdateResponse> modifyAmount(
            @RequestParam String paymentPspReference,
            @RequestBody Amount amount) throws IOException, ApiException {

        var updateRequest = new PaymentAmountUpdateRequest()
                .merchantAccount(applicationConfiguration.getAdyenMerchantAccount())
                .amount(amount)
                .industryUsage(PaymentAmountUpdateRequest.IndustryUsageEnum.DELAYEDCHARGE);

        log.info("AdjustAuthorisation: pspReference={}, amount={}", paymentPspReference, amount);
        var response = modificationsApi.updateAuthorisedAmount(paymentPspReference, updateRequest);
        return ResponseEntity.ok().body(response);
    }

    // PreAuth Step 3 - Capture the (adjusted) authorised amount
    @PostMapping("/api/capture")
    public ResponseEntity<PaymentCaptureResponse> capture(
            @RequestParam String paymentPspReference,
            @RequestBody Amount amount) throws IOException, ApiException {

        var captureRequest = new PaymentCaptureRequest()
                .merchantAccount(applicationConfiguration.getAdyenMerchantAccount())
                .amount(amount);

        log.info("Capture: pspReference={}, amount={}", paymentPspReference, amount);
        var response = modificationsApi.captureAuthorisedPayment(paymentPspReference, captureRequest);
        return ResponseEntity.ok().body(response);
    }

    // PreAuth Step 4 - Cancel an authorised payment (pre-capture)
    @PostMapping("/api/cancel")
    public ResponseEntity<PaymentCancelResponse> cancel(
            @RequestParam String paymentPspReference) throws IOException, ApiException {

        var cancelRequest = new PaymentCancelRequest()
                .merchantAccount(applicationConfiguration.getAdyenMerchantAccount())
                .reference("cancel-" + UUID.randomUUID());

        log.info("Cancel: pspReference={}", paymentPspReference);
        var response = modificationsApi.cancelAuthorisedPaymentByPspReference(paymentPspReference, cancelRequest);
        return ResponseEntity.ok().body(response);
    }

    // PreAuth Step 5 - Refund a captured payment
    @PostMapping("/api/refund")
    public ResponseEntity<PaymentRefundResponse> refund(
            @RequestParam String paymentPspReference,
            @RequestBody Amount amount) throws IOException, ApiException {

        var refundRequest = new PaymentRefundRequest()
                .merchantAccount(applicationConfiguration.getAdyenMerchantAccount())
                .amount(amount)
                .reference("refund-" + UUID.randomUUID());

        log.info("Refund: pspReference={}, amount={}", paymentPspReference, amount);
        var response = modificationsApi.refundCapturedPayment(paymentPspReference, refundRequest);
        return ResponseEntity.ok().body(response);
    }

    // Alternative to cancel - works for both pre- and post-capture
    @PostMapping("/api/reversal")
    public ResponseEntity<PaymentReversalResponse> reversal(
            @RequestParam String paymentPspReference) throws IOException, ApiException {

        var reversalRequest = new PaymentReversalRequest()
                .merchantAccount(applicationConfiguration.getAdyenMerchantAccount())
                .reference("reversal-" + UUID.randomUUID());

        log.info("Reversal: pspReference={}", paymentPspReference);
        var response = modificationsApi.reverseAuthorisedPayment(paymentPspReference, reversalRequest);
        return ResponseEntity.ok().body(response);
    }

    private RedirectView getRedirectView(PaymentDetailsResponse.ResultCodeEnum resultCode) {
        if (resultCode == null) return new RedirectView("/result/error");
        return switch (resultCode) {
            case AUTHORISED -> new RedirectView("/result/success");
            case PENDING, RECEIVED -> new RedirectView("/result/pending");
            case REFUSED, CANCELLED -> new RedirectView("/result/failed");
            default -> new RedirectView("/result/error");
        };
    }
}
