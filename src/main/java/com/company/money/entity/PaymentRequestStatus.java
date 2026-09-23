package com.company.money.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.jspecify.annotations.Nullable;

public enum PaymentRequestStatus implements EnumClass<String> {
    DRAFT("DRAFT"),
    SUBMITTED("SUBMITTED"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    CANCELLED("CANCELLED"),
    DEFERRED("DEFERRED"),
    SENT_TO_1C("SENT_TO_1C"),
    PAID("PAID");

    private final String id;

    PaymentRequestStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static PaymentRequestStatus fromId(String id) {
        for (PaymentRequestStatus value : values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
