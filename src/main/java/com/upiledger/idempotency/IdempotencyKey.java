package com.upiledger.idempotency;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="idempotency_keys",uniqueConstraints=@UniqueConstraint(name="uk_idempotency_key",columnNames="idempotency_key"))
public class IdempotencyKey{
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(name="idempotency_key",nullable=false,length=200) private String idempotencyKey; @Column(name="request_hash",nullable=false,length=128) private String requestHash; @Column(name="response_status") private Integer responseStatus; @Column(name="response_body",columnDefinition="jsonb") private String responseBody; @Column(name="resource_id") private UUID resourceId; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; @Column(name="expires_at",nullable=false) private Instant expiresAt;
 protected IdempotencyKey(){} public IdempotencyKey(String key,String hash,Instant expiresAt){this.idempotencyKey=key;this.requestHash=hash;this.expiresAt=expiresAt;this.createdAt=Instant.now();}
 public String getIdempotencyKey(){return idempotencyKey;} public String getRequestHash(){return requestHash;} public UUID getResourceId(){return resourceId;} public Integer getResponseStatus(){return responseStatus;} public String getResponseBody(){return responseBody;}
 public void complete(UUID resourceId,int status,String body){this.resourceId=resourceId;this.responseStatus=status;this.responseBody=body;}
}
