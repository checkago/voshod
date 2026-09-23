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
@Table(name = "VSHD1_NOMENCLATURE", indexes = {
        @Index(name = "IDX_VSHD1_NOMENCLATURE_ON_ONE_C_REF", columnList = "ONE_C_REF", unique = true)
})
@Entity(name = "vshd1_Nomenclature")
public class Nomenclature {

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

    @Column(name = "CODE", length = 11)
    private String code;

    @InstanceName
    @Column(name = "NAME", nullable = false, length = 100)
    @NotNull
    private String name;

    @Column(name = "ARTICLE", length = 50)
    private String article;

    @Column(name = "SERVICE", nullable = false)
    @NotNull
    private Boolean service = false;

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

    public String getArticle() {
        return article;
    }

    public void setArticle(String article) {
        this.article = article;
    }

    public Boolean getService() {
        return service;
    }

    public void setService(Boolean service) {
        this.service = service;
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
