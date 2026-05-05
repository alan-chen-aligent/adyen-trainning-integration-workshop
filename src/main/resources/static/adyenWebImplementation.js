const clientKey = document.getElementById("clientKey").innerHTML;
const { AdyenCheckout, Dropin } = window.AdyenWeb;

// Step 8 - Initialize the Drop-in and handle payment submission
async function startCheckout() {
    try {
        const type = document.getElementById("type").innerHTML.trim();
        const paymentsEndpoint = type === "preauth" ? "/api/preauthorisation" : "/api/payments";

        const paymentMethodsResponse = await fetch("/api/paymentMethods", {
            method: "POST",
            headers: { "Content-Type": "application/json" }
        });
        const paymentMethodsData = await paymentMethodsResponse.json();

        const checkout = await AdyenCheckout({
            environment: "test",
            clientKey: clientKey,
            paymentMethodsResponse: paymentMethodsData,
            onPaymentCompleted: handleOnPaymentCompleted,
            onPaymentFailed: handleOnPaymentFailed,
            onSubmit: async (state, component) => {
                if (!state.isValid) return;
                try {
                    const response = await fetch(paymentsEndpoint, {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify(state.data)
                    });
                    const data = await response.json();
                    if (data.action) {
                        component.handleAction(data.action);
                    } else {
                        handleOnPaymentCompleted(data, component);
                    }
                } catch (error) {
                    console.error("Payment submission error:", error);
                    component.setStatus("error");
                }
            },
            onAdditionalDetails: async (state, component) => {
                try {
                    const response = await fetch("/api/payments/details", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify(state.data)
                    });
                    const data = await response.json();
                    if (data.action) {
                        component.handleAction(data.action);
                    } else {
                        handleOnPaymentCompleted(data, component);
                    }
                } catch (error) {
                    console.error("Payment details error:", error);
                    component.setStatus("error");
                }
            }
        });

        new Dropin(checkout, {
            paymentMethodsConfiguration: {
                card: {
                    hasHolderName: true,
                    holderNameRequired: true
                }
            }
        }).mount("#payment");

    } catch (error) {
        console.error(error);
        alert("Error occurred. Look at console for details.");
    }
}

// Step 10 - Redirect to result page based on Adyen's resultCode
function handleOnPaymentCompleted(response, _component) {
    console.info("onPaymentCompleted", response);
    const resultCode = response.resultCode?.toLowerCase();
    if (resultCode === "authorised") {
        window.location.href = "/result/success";
    } else if (resultCode === "pending" || resultCode === "received") {
        window.location.href = "/result/pending";
    } else if (resultCode === "refused" || resultCode === "cancelled") {
        window.location.href = "/result/failed";
    } else {
        window.location.href = "/result/error";
    }
}

function handleOnPaymentFailed(response, _component) {
    console.info("onPaymentFailed", response);
    const resultCode = response.resultCode?.toLowerCase();
    if (resultCode === "refused" || resultCode === "cancelled") {
        window.location.href = "/result/failed";
    } else {
        window.location.href = "/result/error";
    }
}

startCheckout();
