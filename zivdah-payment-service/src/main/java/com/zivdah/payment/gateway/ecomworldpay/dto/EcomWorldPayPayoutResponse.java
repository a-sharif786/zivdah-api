package com.zivdah.payment.gateway.ecomworldpay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Shared shape for every Payout API response body EcomWorldPay sends us: the create-payout
// response (Status/ReferenceId/Description), the Payout Payment Status API response
// (Status/TransactionId/UTRNumber/TransactionDateTime), and the Payout Callback webhook body
// (Status/TransactionId/UTRNumber/Remark/TransactionDateTime) — all wrapped in the same
// top-level {"Data": {...}}. Fields not present in a given response are simply left null;
// reusing one class avoids three near-identical DTOs for a gateway whose docs already
// describe the fields inconsistently across endpoints.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EcomWorldPayPayoutResponse {

    @JsonProperty("Data")
    private Data data;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Data {
        @JsonProperty("Status")
        private String status;
        @JsonProperty("ReferenceId")
        private String referenceId;
        @JsonProperty("Description")
        private String description;
        @JsonProperty("TransactionId")
        private String transactionId;
        @JsonProperty("UTRNumber")
        private String utrNumber;
        @JsonProperty("Remark")
        private String remark;
        @JsonProperty("TransactionDateTime")
        private String transactionDateTime;
    }
}
