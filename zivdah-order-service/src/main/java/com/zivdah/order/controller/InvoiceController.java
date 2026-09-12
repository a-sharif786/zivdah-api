package com.zivdah.order.controller;

import com.zivdah.order.dto.ApiResponse;
import com.zivdah.order.dto.GenerateInvoiceRequestDto;
import com.zivdah.order.dto.InvoiceResponseDto;
import com.zivdah.order.dto.OrderResponseDto;
import com.zivdah.order.service.InvoiceService;
import com.zivdah.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/restful/v1/api/invoices")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final OrderService orderService;

    private Mono<Long> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(Authentication::getName)
                .map(Long::valueOf);
    }

    private Mono<String> currentRole() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(auth -> auth.getAuthorities().stream().findFirst()
                        .map(GrantedAuthority::getAuthority)
                        .map(a -> a.replaceFirst("^ROLE_", ""))
                        .orElse(""));
    }

    // Same ownership rule OrderController already applies for order access: the order's own
    // customer, ADMIN, or a VENDOR who owns at least one item on it.
    private Mono<OrderResponseDto> requireOrderAccess(Long orderId, Long currentUserId, String role) {
        return orderService.getOrderById(orderId)
                .flatMap(order -> {
                    boolean allowed = "ADMIN".equalsIgnoreCase(role)
                            || order.getUserId().equals(currentUserId)
                            || ("VENDOR".equalsIgnoreCase(role)
                            && order.getItems().stream().anyMatch(i -> currentUserId.equals(i.getVendorId())));
                    if (!allowed) {
                        return Mono.error(new AccessDeniedException("Not authorized to access this order's invoice"));
                    }
                    return Mono.just(order);
                });
    }

    private Mono<Void> requireInvoiceAccess(Long invoiceId, Long currentUserId, String role) {
        return invoiceService.canAccess(invoiceId, currentUserId, role)
                .flatMap(allowed -> allowed
                        ? Mono.empty()
                        : Mono.error(new AccessDeniedException("Not authorized to access this invoice")));
    }

    @PostMapping("/generate/{orderId}")
    public Mono<ResponseEntity<ApiResponse<InvoiceResponseDto>>> generate(
            @PathVariable Long orderId, @RequestBody(required = false) GenerateInvoiceRequestDto request) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> requireOrderAccess(orderId, t.getT1(), t.getT2()))
                .then(invoiceService.generateInvoice(orderId, request))
                .map(r -> ResponseEntity.ok(ApiResponse.<InvoiceResponseDto>builder()
                        .status("success").statusCode(200).message("Invoice generated").data(r).build()));
    }

    @GetMapping("/{invoiceId}")
    public Mono<ResponseEntity<ApiResponse<InvoiceResponseDto>>> getById(@PathVariable Long invoiceId) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> requireInvoiceAccess(invoiceId, t.getT1(), t.getT2()))
                .then(invoiceService.getById(invoiceId))
                .map(r -> ResponseEntity.ok(ApiResponse.<InvoiceResponseDto>builder()
                        .status("success").statusCode(200).message("Invoice retrieved").data(r).build()));
    }

    @GetMapping("/order/{orderId}")
    public Mono<ResponseEntity<ApiResponse<InvoiceResponseDto>>> getByOrderId(@PathVariable Long orderId) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> requireOrderAccess(orderId, t.getT1(), t.getT2()))
                .then(invoiceService.getByOrderId(orderId))
                .map(r -> ResponseEntity.ok(ApiResponse.<InvoiceResponseDto>builder()
                        .status("success").statusCode(200).message("Invoice retrieved").data(r).build()));
    }

    // ?mode=attachment forces a download prompt; the default (inline) lets the browser render
    // the PDF for "View Invoice". Either way this is the endpoint the frontend should call —
    // never the raw pdfUrl — since it's the one that actually checks ownership.
    @GetMapping("/{invoiceId}/download")
    public Mono<ResponseEntity<byte[]>> download(
            @PathVariable Long invoiceId, @RequestParam(defaultValue = "inline") String mode) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> requireInvoiceAccess(invoiceId, t.getT1(), t.getT2()))
                .then(Mono.zip(invoiceService.getById(invoiceId), invoiceService.getPdfBytes(invoiceId)))
                .map(t -> {
                    InvoiceResponseDto invoice = t.getT1();
                    byte[] bytes = t.getT2();
                    String filename = invoice.getInvoiceNumber() + ".pdf";
                    ContentDisposition.Builder cd = "attachment".equalsIgnoreCase(mode)
                            ? ContentDisposition.attachment() : ContentDisposition.inline();
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_PDF)
                            .header(HttpHeaders.CONTENT_DISPOSITION, cd.filename(filename).build().toString())
                            .body(bytes);
                });
    }

    @GetMapping("/customer/{customerId}")
    public Mono<ResponseEntity<ApiResponse<List<InvoiceResponseDto>>>> getByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return Mono.zip(currentUserId(), currentRole())
                .flatMap(t -> {
                    boolean allowed = "ADMIN".equalsIgnoreCase(t.getT2()) || customerId.equals(t.getT1());
                    if (!allowed) {
                        return Mono.error(new AccessDeniedException("Not authorized to view another customer's invoices"));
                    }
                    return invoiceService.getByCustomer(customerId, PageRequest.of(page, size)).collectList();
                })
                .map(list -> ResponseEntity.ok(ApiResponse.<List<InvoiceResponseDto>>builder()
                        .status("success").statusCode(200).message("Invoices retrieved").data(list).build()));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<InvoiceResponseDto>>>> getAll(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return invoiceService.getAll(PageRequest.of(page, size)).collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.<List<InvoiceResponseDto>>builder()
                        .status("success").statusCode(200).message("Invoices retrieved").data(list).build()));
    }
}
