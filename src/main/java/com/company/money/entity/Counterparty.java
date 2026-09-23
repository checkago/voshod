package com.company.money.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JmixEntity
@Table(name = "VSHD1_COUNTERPARTY", indexes = {
        @Index(name = "IDX_VSHD1_COUNTERPARTY_ON_ONE_C_REF", columnList = "ONE_C_REF", unique = true)
})
@Entity(name = "vshd1_Counterparty")
public class Counterparty {

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

    @Column(name = "CODE", length = 9)
    private String code;

    @InstanceName
    @Column(name = "NAME", nullable = false, length = 100)
    @NotNull
    private String name;

    @Column(name = "FULL_NAME", length = 1000)
    private String fullName;

    @Column(name = "INN", length = 50)
    private String inn;

    @Column(name = "KPP", length = 9)
    private String kpp;

    @Column(name = "FOLDER", nullable = false)
    @NotNull
    private Boolean folder = false;

    @Column(name = "DELETION_MARK", nullable = false)
    @NotNull
    private Boolean deletionMark = false;

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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getInn() {
        return inn;
    }

    public void setInn(String inn) {
        this.inn = inn;
    }

    public String getKpp() {
        return kpp;
    }

    public void setKpp(String kpp) {
        this.kpp = kpp;
    }

    public Boolean getFolder() {
        return folder;
    }

    public void setFolder(Boolean folder) {
        this.folder = folder;
    }

    public Boolean getDeletionMark() {
        return deletionMark;
    }

    public void setDeletionMark(Boolean deletionMark) {
        this.deletionMark = deletionMark;
    }
}
