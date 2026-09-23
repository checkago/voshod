package com.company.money.entity;

import io.jmix.core.FileRef;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JmixEntity
@Table(name = "VSHD1_PAYMENT_REQUEST_FILE", indexes = {
        @Index(name = "IDX_VSHD1_PAYMENT_REQUEST_FILE_ON_PAYMENT_REQUEST", columnList = "PAYMENT_REQUEST_ID")
})
@Entity(name = "vshd1_PaymentRequestFile")
public class PaymentRequestFile {

    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @JoinColumn(name = "PAYMENT_REQUEST_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @NotNull
    private PaymentRequest paymentRequest;

    @Column(name = "CONTENT_FILE", nullable = false, length = 1024)
    @NotNull
    private FileRef contentFile;

    @Column(name = "DESCRIPTION")
    private String description;

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

    public PaymentRequest getPaymentRequest() {
        return paymentRequest;
    }

    public void setPaymentRequest(PaymentRequest paymentRequest) {
        this.paymentRequest = paymentRequest;
    }

    public FileRef getContentFile() {
        return contentFile;
    }

    public void setContentFile(FileRef contentFile) {
        this.contentFile = contentFile;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @InstanceName
    @DependsOnProperties({"description", "contentFile"})
    public String getDisplayName() {
        if (description != null && !description.isBlank()) {
            return description;
        }
        return contentFile != null ? contentFile.getFileName() : "";
    }
}
