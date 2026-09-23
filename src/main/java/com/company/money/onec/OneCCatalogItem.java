package com.company.money.onec;

import java.util.UUID;

public record OneCCatalogItem(
        UUID oneCRef,
        String code,
        String name,
        String fullName,
        String inn,
        String kpp,
        String article,
        boolean service
) {
}
