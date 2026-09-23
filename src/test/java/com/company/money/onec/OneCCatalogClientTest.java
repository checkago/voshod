package com.company.money.onec;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OneCCatalogClientTest {

    @Test
    void pagesBankAccountsUntilOwnerMatches() {
        UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID other = UUID.fromString("22222222-2222-2222-2222-222222222222");
        OneCOdataClient odataClient = mock(OneCOdataClient.class);
        when(odataClient.get(eq(OneCCatalogClient.BANK_ACCOUNTS), anyString(), eq(0), eq(200),
                anyString(), anyString()))
                .thenReturn(page(other, 200));
        when(odataClient.get(eq(OneCCatalogClient.BANK_ACCOUNTS), anyString(), eq(200), eq(200),
                anyString(), anyString()))
                .thenReturn(page(owner, 1));

        var items = new OneCCatalogClient(odataClient).searchBankAccounts(owner, "", 0, 20);

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().ownerRef()).isEqualTo(owner);
    }

    private static String page(UUID owner, int count) {
        StringBuilder json = new StringBuilder("{\"value\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"Ref_Key\":\"")
                    .append(UUID.randomUUID())
                    .append("\",\"Description\":\"Счёт ")
                    .append(i)
                    .append("\",\"Owner\":\"")
                    .append(owner)
                    .append("\",\"DeletionMark\":false}");
        }
        json.append("]}");
        return json.toString();
    }
}
