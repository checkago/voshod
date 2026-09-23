package com.company.money.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.annotation.PostConstruct;
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
@Table(name = "VSHD1_COUNTERPARTY_BANK_ACCOUNT", indexes = {
        @Index(name = "IDX_VSHD1_COUNTERPARTY_BANK_ACCOUNT_ON_ONE_C_REF", columnList = "ONE_C_REF", unique = true),
        @Index(name = "IDX_VSHD1_COUNTERPARTY_BANK_ACCOUNT_ON_COUNTERPARTY", columnList = "COUNTERPARTY_ID")
})
@Entity(name = "vshd1_CounterpartyBankAccount")
public class CounterpartyBankAccount {

    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @Column(name = "ONE_C_REF", nullable = false)
    @NotNull
    private UUID oneCRef;

    @JoinColumn(name = "COUNTERPARTY_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @NotNull
    private Counterparty counterparty;

    @Column(name = "CODE", length = 9)
    private String code;

    @Column(name = "NAME", nullable = false, length = 100)
    @NotNull
    private String name;

    @Column(name = "ACCOUNT_NUMBER", length = 70)
    private String accountNumber;

    @PostConstruct
    public void postConstruct() {
        if (oneCRef == null) {
            oneCRef = UUID.randomUUID();
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

    public UUID getOneCRef() {
        return oneCRef;
    }

    public void setOneCRef(UUID oneCRef) {
        this.oneCRef = oneCRef;
    }

    public Counterparty getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(Counterparty counterparty) {
        this.counterparty = counterparty;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    @InstanceName
    @DependsOnProperties({"name", "accountNumber"})
    public String getDisplayName() {
        if (accountNumber != null && !accountNumber.isBlank()) {
            return accountNumber + " " + name;
        }
        return name;
    }
}
