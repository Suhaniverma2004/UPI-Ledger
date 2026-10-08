package com.upiledger.api;
import com.upiledger.payments.*; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.math.BigDecimal; import java.util.UUID;
@RestController @RequestMapping("/api/v1/payments")
public class PaymentController{
 private final PaymentService service; public PaymentController(PaymentService service){this.service=service;}
 @PostMapping public ResponseEntity<PaymentResponse> create(@RequestHeader("Idempotency-Key") @NotBlank String key,@Valid @RequestBody CreatePaymentRequest r){var tx=service.create(new PaymentService.CreatePaymentCommand(r.externalTxnId(),r.payerAccountId(),r.payeeAccountId(),r.amount(),r.currency()),key);return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(tx));}
 @GetMapping("/{id}") public PaymentResponse get(@PathVariable UUID id){return PaymentResponse.from(service.get(id));}
 @PostMapping("/{id}/authorize") public PaymentResponse authorize(@PathVariable UUID id){return PaymentResponse.from(service.authorize(id));}
 @PostMapping("/{id}/settle") public PaymentResponse settle(@PathVariable UUID id){return PaymentResponse.from(service.settle(id));}
 @PostMapping("/{id}/fail") public PaymentResponse fail(@PathVariable UUID id){return PaymentResponse.from(service.fail(id));}
 @PostMapping("/{id}/reverse") public PaymentResponse reverse(@PathVariable UUID id){return PaymentResponse.from(service.reverse(id));}
 public record CreatePaymentRequest(@NotBlank @Size(max=100) String externalTxnId,@NotNull UUID payerAccountId,@NotNull UUID payeeAccountId,@NotNull @Positive @Digits(integer=14,fraction=4) BigDecimal amount,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency){}
 public record PaymentResponse(UUID transactionId,String externalTxnId,UUID payerAccountId,UUID payeeAccountId,BigDecimal amount,String currency,TransactionStatus status){static PaymentResponse from(PaymentTransaction t){return new PaymentResponse(t.getId(),t.getExternalTxnId(),t.getPayerAccountId(),t.getPayeeAccountId(),t.getAmount(),t.getCurrency(),t.getStatus());}}
}
