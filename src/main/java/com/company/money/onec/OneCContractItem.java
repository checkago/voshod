package com.company.money.onec;

import java.util.UUID;

public record OneCContractItem(
        UUID oneCRef,
        String code,
        String name,
        UUID ownerRef,
        String contractKind,
        String vatRate,
        boolean amountIncludesVat,
        UUID currencyKey
) {
}
