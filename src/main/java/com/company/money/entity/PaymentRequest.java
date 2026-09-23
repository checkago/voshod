package com.company.money.entity;

import com.company.money.service.PaymentRequestException;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.Composition;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@JmixEntity
@Table(name = "VSHD1_PAYMENT_REQUEST", indexes = {
        @Index(name = "IDX_VSHD1_PAYMENT_REQUEST_ON_NUMBER", columnList = "REQUEST_NUMBER", unique = true),
        @Index(name = "IDX_VSHD1_PAYMENT_REQUEST_ON_APPLICANT", columnList = "APPLICANT_ID"),
        @Index(name = "IDX_VSHD1_PAYMENT_REQUEST_ON_COUNTERPARTY", columnList = "COUNTERPARTY_ID"),
        @Index(name = "IDX_VSHD1_PAYMENT_REQUEST_ON_NOMENCLATURE", columnList = "NOMENCLATURE_ID"),
        @Index(name = "IDX_VSHD1_PAYMENT_REQUEST_ON_BANK_ACCOUNT", columnList = "COUNTERPARTY_BANK_ACCOUNT_ID"),
        @Index(name = "IDX_VSHD1_PAYMENT_REQUEST_ON_CONTRACT", columnList = "CONTRACT_ID"),
        @Index(name = "IDX_VSHD1_PAYMENT_REQUEST_ON_LAST_CHANGED_BY", columnList = "LAST_CHANGED_BY_ID")
})
@Entity(name = "vshd1_PaymentRequest")
public class PaymentRequest {

    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @InstanceName
    @Column(name = "REQUEST_NUMBER", nullable = false, length = 30)
    @NotNull
    private String requestNumber;

    @Column(name = "REQUEST_DATE", nullable = false)
    @NotNull
    private LocalDate requestDate;

    @Column(name = "STATUS", nullable = false, length = 50)
    @NotNull
    private String status = PaymentRequestStatus.DRAFT.getId();

    @JoinColumn(name = "APPLICANT_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @NotNull
    private User applicant;

    @JoinColumn(name = "LAST_CHANGED_BY_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private User lastChangedBy;

    @JoinColumn(name = "COUNTERPARTY_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @NotNull
    private Counterparty counterparty;

    @JoinColumn(name = "NOMENCLATURE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Nomenclature nomenclature;

    @JoinColumn(name = "COUNTERPARTY_BANK_ACCOUNT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private CounterpartyBankAccount counterpartyBankAccount;

    @JoinColumn(name = "CONTRACT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private CounterpartyContract contract;

    @Column(name = "MISSING_IN_ONE_C", nullable = false)
    @NotNull
    private Boolean missingInOneC = false;

    @Column(name = "AMOUNT", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    @Column(name = "PURPOSE", nullable = false, length = 210)
    @NotNull
    private String purpose;

    @Column(name = "REQUESTED_PAYMENT_DATE")
    private LocalDate requestedPaymentDate;

    @Column(name = "APPROVED_PAYMENT_DATE")
    private LocalDate approvedPaymentDate;

    @Column(name = "DEFERRED_UNTIL")
    private LocalDate deferredUntil;

    @Column(name = "CFO_COMMENT", length = 500)
    private String cfoComment;

    @Column(name = "ONE_C_DOCUMENT_REF", length = 36)
    private String oneCDocumentRef;

    @Column(name = "ONE_C_DOCUMENT_NUMBER", length = 30)
    private String oneCDocumentNumber;

    @Column(name = "ONE_C_DOCUMENT_DATE")
    private LocalDate oneCDocumentDate;

    @Lob
    @Column(name = "ONE_C_EXPORT_PAYLOAD")
    private String oneCExportPayload;

    @Composition
    @OneToMany(mappedBy = "paymentRequest")
    private List<PaymentRequestFile> attachments;

    @PostConstruct
    public void postConstruct() {
        requestDate = LocalDate.now();
        if (status == null) {
            status = PaymentRequestStatus.DRAFT.getId();
        }
        if (requestNumber == null) {
            requestNumber = "TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
    }

    @PreRemove
    public void forbidDeleteWhenPaid() {
        if (PaymentRequestStatus.PAID.getId().equals(status)) {
            throw new PaymentRequestException("A paid request cannot be deleted");
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getRequestNumber() {
        return requestNumber;
    }

    public void setRequestNumber(String requestNumber) {
        this.requestNumber = requestNumber;
    }

    public LocalDate getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(LocalDate requestDate) {
        this.requestDate = requestDate;
    }

    public PaymentRequestStatus getStatus() {
        return status == null ? null : PaymentRequestStatus.fromId(status);
    }

    public void setStatus(PaymentRequestStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public User getApplicant() {
        return applicant;
    }

    public void setApplicant(User applicant) {
        this.applicant = applicant;
    }

    public User getLastChangedBy() {
        return lastChangedBy;
    }

    public void setLastChangedBy(User lastChangedBy) {
        this.lastChangedBy = lastChangedBy;
    }

    public Counterparty getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(Counterparty counterparty) {
        this.counterparty = counterparty;
    }

    public Nomenclature getNomenclature() {
        return nomenclature;
    }

    public void setNomenclature(Nomenclature nomenclature) {
        this.nomenclature = nomenclature;
    }

    public CounterpartyBankAccount getCounterpartyBankAccount() {
        return counterpartyBankAccount;
    }

    public void setCounterpartyBankAccount(CounterpartyBankAccount counterpartyBankAccount) {
        this.counterpartyBankAccount = counterpartyBankAccount;
    }

    public CounterpartyContract getContract() {
        return contract;
    }

    public void setContract(CounterpartyContract contract) {
        this.contract = contract;
    }

    public Boolean getMissingInOneC() {
        return missingInOneC;
    }

    public void setMissingInOneC(Boolean missingInOneC) {
        this.missingInOneC = missingInOneC;
    }

    public boolean isMissingInOneC() {
        return Boolean.TRUE.equals(missingInOneC);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public LocalDate getRequestedPaymentDate() {
        return requestedPaymentDate;
    }

    public void setRequestedPaymentDate(LocalDate requestedPaymentDate) {
        this.requestedPaymentDate = requestedPaymentDate;
    }

    public LocalDate getApprovedPaymentDate() {
        return approvedPaymentDate;
    }

    public void setApprovedPaymentDate(LocalDate approvedPaymentDate) {
        this.approvedPaymentDate = approvedPaymentDate;
    }

    public LocalDate getDeferredUntil() {
        return deferredUntil;
    }

    public void setDeferredUntil(LocalDate deferredUntil) {
        this.deferredUntil = deferredUntil;
    }

    public String getCfoComment() {
        return cfoComment;
    }

    public void setCfoComment(String cfoComment) {
        this.cfoComment = cfoComment;
    }

    public String getOneCDocumentRef() {
        return oneCDocumentRef;
    }

    public void setOneCDocumentRef(String oneCDocumentRef) {
        this.oneCDocumentRef = oneCDocumentRef;
    }

    public String getOneCDocumentNumber() {
        return oneCDocumentNumber;
    }

    public void setOneCDocumentNumber(String oneCDocumentNumber) {
        this.oneCDocumentNumber = oneCDocumentNumber;
    }

    public LocalDate getOneCDocumentDate() {
        return oneCDocumentDate;
    }

    public void setOneCDocumentDate(LocalDate oneCDocumentDate) {
        this.oneCDocumentDate = oneCDocumentDate;
    }

    @JmixProperty
    @Transient
    @DependsOnProperties({"oneCDocumentNumber", "oneCDocumentDate", "oneCDocumentRef"})
    public String getOneCDocumentTitle() {
        if (oneCDocumentNumber != null && !oneCDocumentNumber.isBlank()) {
            if (oneCDocumentDate != null) {
                return "ПП №" + oneCDocumentNumber + " от "
                        + oneCDocumentDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            }
            return "ПП №" + oneCDocumentNumber;
        }
        return oneCDocumentRef;
    }

    public String getOneCExportPayload() {
        return oneCExportPayload;
    }

    public void setOneCExportPayload(String oneCExportPayload) {
        this.oneCExportPayload = oneCExportPayload;
    }

    public List<PaymentRequestFile> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<PaymentRequestFile> attachments) {
        this.attachments = attachments;
    }
}
