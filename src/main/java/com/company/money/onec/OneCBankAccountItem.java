package com.company.money.onec;

import java.util.UUID;

public record OneCBankAccountItem(
        UUID oneCRef,
        String code,
        String name,
        String accountNumber,
        UUID ownerRef
) {
}
