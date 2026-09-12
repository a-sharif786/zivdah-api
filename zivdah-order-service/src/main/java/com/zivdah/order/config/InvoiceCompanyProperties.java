package com.zivdah.order.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// Printed on every invoice PDF header (see InvoicePdfGenerator). Bound from invoice.company.*
// in application-*.yaml — fill in the real registered address/phone/GSTIN there; the defaults
// below are placeholders so the feature works out of the box.
@Component
@ConfigurationProperties(prefix = "invoice.company")
@Getter
@Setter
public class InvoiceCompanyProperties {
    private String name = "Zivdah Online Grocery";
    private String addressLine1 = "";
    private String addressLine2 = "";
    private String email = "support@zivdahonlinegrocery.com";
    private String phone = "";
    private String website = "https://zivdahonlinegrocery.com";
    // GST/VAT registration number — left blank until the real one is configured.
    private String taxId = "";
}
