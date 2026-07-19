package com.hana.omniconnect.tax.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class TaxRefundBackofficeRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void bindsEstimatedRefundAsNumericValue() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        TaxRefundBackofficeRepository repository = new TaxRefundBackofficeRepository(jdbcTemplate, new ObjectMapper());
        Instant now = Instant.parse("2026-07-12T15:26:35Z");

        repository.upsert(new TaxRefundBackofficeCase(
                "TAX-CASE123456789", "ACC-ABC123456789", "USR-ABC123456789", 2026, "US", "120.50",
                true, true, List.of(), List.of(), "SYNCED_WITH_HANA", now, now, "NOT_SUBMITTED", null,
                "NOT_PREPARED", null, null, null));

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), arguments.capture());
        assertThat(arguments.getValue()[4]).isEqualTo(new BigDecimal("120.50"));
    }
}
