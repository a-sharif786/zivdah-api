package com.zivdah.payment.serviceImpl;

import com.zivdah.payment.client.AuthServiceClient;
import com.zivdah.payment.client.dto.VendorBankDetailsDto;
import com.zivdah.payment.entity.VendorPayout;
import com.zivdah.payment.enums.PayoutInitiator;
import com.zivdah.payment.enums.PayoutMode;
import com.zivdah.payment.enums.VendorPayoutStatus;
import com.zivdah.payment.gateway.ecomworldpay.EcomWorldPayClient;
import com.zivdah.payment.gateway.ecomworldpay.dto.EcomWorldPayPayoutResponse;
import com.zivdah.payment.repository.VendorPayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VendorPayoutServiceImplTest {

    @Mock private VendorPayoutRepository vendorPayoutRepository;
    @Mock private AuthServiceClient authServiceClient;
    @Mock private EcomWorldPayClient ecomWorldPayClient;

    private VendorPayoutServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VendorPayoutServiceImpl(vendorPayoutRepository, authServiceClient, ecomWorldPayClient);
    }

    // --- fixtures ------------------------------------------------------------------------------

    private VendorBankDetailsDto vendorWithUpi() {
        return VendorBankDetailsDto.builder()
                .id(7L).name("Jane Vendor").mobile("9876543210")
                .upiVpa("jane@ybl").role("VENDOR")
                .build();
    }

    private VendorBankDetailsDto vendorWithBankAccount() {
        return VendorBankDetailsDto.builder()
                .id(7L).name("Jane Vendor").mobile("9876543210")
                .bankAccountNumber("1234567890").bankIfscCode("HDFC0001234").role("VENDOR")
                .build();
    }

    private VendorBankDetailsDto vendorWithNoPayoutDetails() {
        return VendorBankDetailsDto.builder().id(7L).name("Jane Vendor").role("VENDOR").build();
    }

    private VendorBankDetailsDto adminTargetVendorWithBoth() {
        return VendorBankDetailsDto.builder()
                .id(9L).name("Sam Vendor").mobile("9998887776")
                .upiVpa("sam@ybl")
                .bankAccountNumber("55501112223").bankIfscCode("ICIC0009999")
                .role("VENDOR")
                .build();
    }

    private VendorBankDetailsDto adminTargetVendorWithVpaOnly() {
        return VendorBankDetailsDto.builder().id(9L).name("Sam Vendor").upiVpa("sam@ybl").role("VENDOR").build();
    }

    private VendorBankDetailsDto adminTargetVendorWithBankOnly() {
        return VendorBankDetailsDto.builder()
                .id(9L).name("Sam Vendor")
                .bankAccountNumber("55501112223").bankIfscCode("ICIC0009999").role("VENDOR")
                .build();
    }

    private EcomWorldPayPayoutResponse wrap(EcomWorldPayPayoutResponse.Data data) {
        EcomWorldPayPayoutResponse response = new EcomWorldPayPayoutResponse();
        response.setData(data);
        return response;
    }

    private void mockSave() {
        when(vendorPayoutRepository.save(any(VendorPayout.class))).thenAnswer(inv -> {
            VendorPayout p = inv.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return Mono.just(p);
        });
    }

    // --- vendor path regression ------------------------------------------------------------------

    @Test
    void requestPayout_autoDetectsUpi_whenVpaOnFile() {
        when(authServiceClient.getVendorBankDetails(7L)).thenReturn(Mono.just(vendorWithUpi()));
        when(vendorPayoutRepository.existsByVendorIdAndStatusIn(eq(7L), any())).thenReturn(Mono.just(false));
        mockSave();

        StepVerifier.create(service.requestPayout(7L, new BigDecimal("500")))
                .expectNextMatches(dto -> "UPI".equals(dto.getPayoutMode())
                        && "jane@ybl".equals(dto.getPayeeVpa())
                        && dto.getAccountNo() == null
                        && dto.getIfscBankCode() == null
                        && dto.getInitiatedByRole() == PayoutInitiator.VENDOR
                        && Long.valueOf(7L).equals(dto.getInitiatedByUserId())
                        && dto.getStatus() == VendorPayoutStatus.REQUESTED)
                .verifyComplete();
    }

    @Test
    void requestPayout_autoDetectsImps_whenOnlyBankAccountOnFile() {
        when(authServiceClient.getVendorBankDetails(7L)).thenReturn(Mono.just(vendorWithBankAccount()));
        when(vendorPayoutRepository.existsByVendorIdAndStatusIn(eq(7L), any())).thenReturn(Mono.just(false));
        mockSave();

        StepVerifier.create(service.requestPayout(7L, new BigDecimal("500")))
                .expectNextMatches(dto -> "IMPS".equals(dto.getPayoutMode())
                        && "1234567890".equals(dto.getAccountNo())
                        && "HDFC0001234".equals(dto.getIfscBankCode())
                        && dto.getPayeeVpa() == null)
                .verifyComplete();
    }

    @Test
    void requestPayout_rejects_whenNoBankDetailsOnFile() {
        when(authServiceClient.getVendorBankDetails(7L)).thenReturn(Mono.just(vendorWithNoPayoutDetails()));

        StepVerifier.create(service.requestPayout(7L, new BigDecimal("500")))
                .expectErrorMatches(ex -> ex.getMessage().equals(
                        "No bank account or UPI VPA on file — update your profile before requesting a payout"))
                .verify();

        verifyNoInteractions(vendorPayoutRepository);
    }

    @Test
    void requestPayout_rejects_whenVendorAlreadyHasAnOpenPayout() {
        when(authServiceClient.getVendorBankDetails(7L)).thenReturn(Mono.just(vendorWithUpi()));
        when(vendorPayoutRepository.existsByVendorIdAndStatusIn(eq(7L), any())).thenReturn(Mono.just(true));

        StepVerifier.create(service.requestPayout(7L, new BigDecimal("500")))
                .expectErrorMatches(ex -> ex.getMessage().equals(
                        "A payout request is already pending for this vendor — wait for it to be resolved before requesting another."))
                .verify();

        verify(vendorPayoutRepository, never()).save(any());
    }

    // --- admin path --------------------------------------------------------------------------

    private void assertAdminPayoutSucceeds(PayoutMode mode) {
        when(authServiceClient.getVendorBankDetails(9L)).thenReturn(Mono.just(adminTargetVendorWithBoth()));
        when(vendorPayoutRepository.existsByVendorIdAndStatusIn(eq(9L), any())).thenReturn(Mono.just(false));
        mockSave();

        boolean useUpi = mode == PayoutMode.UPI;
        StepVerifier.create(service.initiateAdminPayout(1L, 9L, new BigDecimal("2500"), mode))
                .expectNextMatches(dto -> mode.name().equals(dto.getPayoutMode())
                        && dto.getInitiatedByRole() == PayoutInitiator.ADMIN
                        && Long.valueOf(1L).equals(dto.getInitiatedByUserId())
                        && dto.getStatus() == VendorPayoutStatus.REQUESTED
                        && (useUpi
                                ? "sam@ybl".equals(dto.getPayeeVpa()) && dto.getAccountNo() == null && dto.getIfscBankCode() == null
                                : "55501112223".equals(dto.getAccountNo()) && "ICIC0009999".equals(dto.getIfscBankCode())
                                        && dto.getPayeeVpa() == null))
                .verifyComplete();
    }

    @Test
    void initiateAdminPayout_succeeds_forUpi() {
        assertAdminPayoutSucceeds(PayoutMode.UPI);
    }

    @Test
    void initiateAdminPayout_succeeds_forImps() {
        assertAdminPayoutSucceeds(PayoutMode.IMPS);
    }

    @Test
    void initiateAdminPayout_succeeds_forNeft() {
        assertAdminPayoutSucceeds(PayoutMode.NEFT);
    }

    @Test
    void initiateAdminPayout_succeeds_forRtgs() {
        assertAdminPayoutSucceeds(PayoutMode.RTGS);
    }

    @Test
    void initiateAdminPayout_rejects_whenNeftRequestedButVendorOnlyHasVpa() {
        when(authServiceClient.getVendorBankDetails(9L)).thenReturn(Mono.just(adminTargetVendorWithVpaOnly()));

        StepVerifier.create(service.initiateAdminPayout(1L, 9L, new BigDecimal("2500"), PayoutMode.NEFT))
                .expectErrorMatches(ex -> ex.getMessage().equals("This vendor has no bank account number/IFSC on file"))
                .verify();
    }

    @Test
    void initiateAdminPayout_rejects_whenUpiRequestedButVendorOnlyHasBankAccount() {
        when(authServiceClient.getVendorBankDetails(9L)).thenReturn(Mono.just(adminTargetVendorWithBankOnly()));

        StepVerifier.create(service.initiateAdminPayout(1L, 9L, new BigDecimal("2500"), PayoutMode.UPI))
                .expectErrorMatches(ex -> ex.getMessage().equals("This vendor has no UPI VPA on file"))
                .verify();
    }

    @Test
    void initiateAdminPayout_rejects_whenTargetIsNotAVendor() {
        VendorBankDetailsDto plainUser = VendorBankDetailsDto.builder()
                .id(20L).name("Random User").upiVpa("random@ybl").role("USER").build();
        when(authServiceClient.getVendorBankDetails(20L)).thenReturn(Mono.just(plainUser));

        StepVerifier.create(service.initiateAdminPayout(1L, 20L, new BigDecimal("100"), PayoutMode.UPI))
                .expectErrorMatches(ex -> ex.getMessage().equals("Selected user is not a vendor"))
                .verify();
    }

    @Test
    void initiateAdminPayout_rejects_withCleanMessage_whenVendorLookup404s() {
        WebClientResponseException notFound = WebClientResponseException.create(
                404, "Not Found", HttpHeaders.EMPTY,
                "404 Not Found from GET http://auth-service-internal:8081/restful/v1/api/auth/internal/users/999"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        when(authServiceClient.getVendorBankDetails(999L)).thenReturn(Mono.error(notFound));

        StepVerifier.create(service.initiateAdminPayout(1L, 999L, new BigDecimal("100"), PayoutMode.UPI))
                .expectErrorMatches(ex -> ex.getMessage().equals("Vendor not found")
                        && !ex.getMessage().contains("http")
                        && !ex.getMessage().contains("auth-service"))
                .verify();
    }

    @Test
    void initiateAdminPayout_rejects_whenVendorAlreadyHasAnOpenPayout() {
        when(authServiceClient.getVendorBankDetails(9L)).thenReturn(Mono.just(adminTargetVendorWithBoth()));
        when(vendorPayoutRepository.existsByVendorIdAndStatusIn(eq(9L), any())).thenReturn(Mono.just(true));

        StepVerifier.create(service.initiateAdminPayout(1L, 9L, new BigDecimal("500"), PayoutMode.UPI))
                .expectErrorMatches(ex -> ex.getMessage().equals(
                        "A payout request is already pending for this vendor — wait for it to be resolved before requesting another."))
                .verify();
    }

    // --- approvePayout -------------------------------------------------------------------------

    @Test
    void approvePayout_happyPath_marksProcessingViaCasThenCallsGateway() {
        VendorPayout payout = VendorPayout.builder()
                .id(5L).vendorId(7L).amount(new BigDecimal("500")).payoutMode("UPI")
                .payeeVpa("jane@ybl").status(VendorPayoutStatus.PROCESSING)
                .invoiceNumber("PO123").requestedAt(LocalDateTime.now())
                .build();
        when(vendorPayoutRepository.markProcessingIfRequested(eq(5L), any())).thenReturn(Mono.just(1));
        when(vendorPayoutRepository.findById(5L)).thenReturn(Mono.just(payout));
        when(authServiceClient.getVendorBankDetails(7L)).thenReturn(Mono.just(vendorWithUpi()));

        EcomWorldPayPayoutResponse.Data data = new EcomWorldPayPayoutResponse.Data();
        data.setStatus("Processing");
        data.setReferenceId("REF123");
        data.setDescription("Your payment status is accepted.!");
        when(ecomWorldPayClient.createPayout(any())).thenReturn(Mono.just(wrap(data)));
        mockSave();

        StepVerifier.create(service.approvePayout(5L, "1.2.3.4"))
                .expectNextMatches(dto -> dto.getStatus() == VendorPayoutStatus.PROCESSING
                        && "Processing".equals(dto.getGatewayStatus())
                        && "REF123".equals(payout.getGatewayReferenceId()))
                .verifyComplete();

        InOrder inOrder = inOrder(vendorPayoutRepository, ecomWorldPayClient);
        inOrder.verify(vendorPayoutRepository).markProcessingIfRequested(eq(5L), any());
        inOrder.verify(ecomWorldPayClient).createPayout(any());
    }

    @Test
    void approvePayout_rejects_withPayoutNotFound_whenNoRowMatches() {
        when(vendorPayoutRepository.markProcessingIfRequested(eq(99L), any())).thenReturn(Mono.just(0));
        when(vendorPayoutRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(service.approvePayout(99L, "1.2.3.4"))
                .expectErrorMatches(ex -> ex.getMessage().equals("Payout not found"))
                .verify();

        verifyNoInteractions(ecomWorldPayClient);
    }

    @Test
    void approvePayout_rejects_withOnlyRequestedCanBeApproved_whenRowExistsButWrongStatus() {
        VendorPayout alreadyProcessing = VendorPayout.builder()
                .id(5L).vendorId(7L).status(VendorPayoutStatus.PROCESSING).build();
        when(vendorPayoutRepository.markProcessingIfRequested(eq(5L), any())).thenReturn(Mono.just(0));
        when(vendorPayoutRepository.findById(5L)).thenReturn(Mono.just(alreadyProcessing));

        StepVerifier.create(service.approvePayout(5L, "1.2.3.4"))
                .expectErrorMatches(ex -> ex.getMessage().equals("Only requested payouts can be approved"))
                .verify();

        verifyNoInteractions(ecomWorldPayClient);
    }

    @Test
    void approvePayout_sanitizesGatewayError_toGenericSafeMessage() {
        VendorPayout payout = VendorPayout.builder()
                .id(5L).vendorId(7L).amount(new BigDecimal("500")).payoutMode("UPI")
                .payeeVpa("jane@ybl").status(VendorPayoutStatus.PROCESSING)
                .invoiceNumber("PO123").requestedAt(LocalDateTime.now())
                .build();
        when(vendorPayoutRepository.markProcessingIfRequested(eq(5L), any())).thenReturn(Mono.just(1));
        when(vendorPayoutRepository.findById(5L)).thenReturn(Mono.just(payout));
        when(authServiceClient.getVendorBankDetails(7L)).thenReturn(Mono.just(vendorWithUpi()));
        when(ecomWorldPayClient.createPayout(any())).thenReturn(
                Mono.error(new RuntimeException("Connection reset by peer at 10.0.4.17:8443 - secretKey=abc123")));

        StepVerifier.create(service.approvePayout(5L, "1.2.3.4"))
                .expectErrorMatches(ex -> ex.getMessage().equals(
                        "Payout could not be processed by the payment gateway right now. Please try again later or contact support.")
                        && !ex.getMessage().contains("10.0.4.17")
                        && !ex.getMessage().contains("secretKey"))
                .verify();
    }

    // --- rejectPayout --------------------------------------------------------------------------

    @Test
    void rejectPayout_marksRejected_andStoresReason_withoutCallingGateway() {
        VendorPayout payout = VendorPayout.builder().id(5L).vendorId(7L).status(VendorPayoutStatus.REQUESTED).build();
        when(vendorPayoutRepository.findById(5L)).thenReturn(Mono.just(payout));
        mockSave();

        StepVerifier.create(service.rejectPayout(5L, "Suspicious account details"))
                .expectNextMatches(dto -> dto.getStatus() == VendorPayoutStatus.REJECTED
                        && "Suspicious account details".equals(dto.getRejectionReason()))
                .verifyComplete();

        verifyNoInteractions(ecomWorldPayClient);
    }

    @Test
    void rejectPayout_rejects_whenNotInRequestedStatus() {
        VendorPayout payout = VendorPayout.builder().id(5L).status(VendorPayoutStatus.PROCESSING).build();
        when(vendorPayoutRepository.findById(5L)).thenReturn(Mono.just(payout));

        StepVerifier.create(service.rejectPayout(5L, "reason"))
                .expectErrorMatches(ex -> ex.getMessage().equals("Only requested payouts can be rejected"))
                .verify();
    }

    // --- refreshPayoutStatus ---------------------------------------------------------------------

    @Test
    void refreshPayoutStatus_updatesToSuccess_whenGatewayReportsSettlementCompleted() {
        VendorPayout payout = VendorPayout.builder()
                .id(5L).vendorId(7L).status(VendorPayoutStatus.PROCESSING).gatewayReferenceId("REF123").build();
        when(vendorPayoutRepository.findById(5L)).thenReturn(Mono.just(payout));
        EcomWorldPayPayoutResponse.Data data = new EcomWorldPayPayoutResponse.Data();
        data.setStatus("SettlementCompleted");
        data.setUtrNumber("UTR999");
        when(ecomWorldPayClient.checkPayoutStatus("REF123")).thenReturn(Mono.just(wrap(data)));
        mockSave();

        StepVerifier.create(service.refreshPayoutStatus(5L))
                .expectNextMatches(dto -> dto.getStatus() == VendorPayoutStatus.SUCCESS
                        && "UTR999".equals(dto.getUtrNumber()))
                .verifyComplete();
    }

    @Test
    void refreshPayoutStatus_sanitizesGatewayError_toGenericSafeMessage() {
        VendorPayout payout = VendorPayout.builder()
                .id(5L).vendorId(7L).status(VendorPayoutStatus.PROCESSING).gatewayReferenceId("REF123").build();
        when(vendorPayoutRepository.findById(5L)).thenReturn(Mono.just(payout));
        when(ecomWorldPayClient.checkPayoutStatus("REF123")).thenReturn(
                Mono.error(new RuntimeException("500 from POST http://api.ecomworldpay.net/api/payout/transStatus")));

        StepVerifier.create(service.refreshPayoutStatus(5L))
                .expectErrorMatches(ex -> ex.getMessage().equals(
                        "Payout could not be processed by the payment gateway right now. Please try again later or contact support."))
                .verify();
    }

    // --- handlePayoutCallback --------------------------------------------------------------------

    @Test
    void handlePayoutCallback_updatesMatchingPayout_toFailed_whenGatewayDeclines() {
        VendorPayout payout = VendorPayout.builder()
                .id(5L).vendorId(7L).status(VendorPayoutStatus.PROCESSING).gatewayReferenceId("REF123").build();
        when(vendorPayoutRepository.findByGatewayReferenceId("REF123")).thenReturn(Mono.just(payout));
        mockSave();

        EcomWorldPayPayoutResponse.Data data = new EcomWorldPayPayoutResponse.Data();
        data.setStatus("Declined");
        data.setTransactionId("REF123");
        data.setRemark("Transaction Failed");

        StepVerifier.create(service.handlePayoutCallback(wrap(data))).verifyComplete();

        verify(vendorPayoutRepository).save(argThat(p ->
                p.getStatus() == VendorPayoutStatus.FAILED && "Transaction Failed".equals(p.getGatewayDescription())));
    }

    @Test
    void handlePayoutCallback_noOps_whenTransactionIdUnknown() {
        EcomWorldPayPayoutResponse.Data data = new EcomWorldPayPayoutResponse.Data();
        data.setStatus("SettlementCompleted");
        data.setTransactionId("UNKNOWN-REF");
        when(vendorPayoutRepository.findByGatewayReferenceId("UNKNOWN-REF")).thenReturn(Mono.empty());

        StepVerifier.create(service.handlePayoutCallback(wrap(data))).verifyComplete();

        verify(vendorPayoutRepository, never()).save(any());
    }
}
