package com.example.test.service;

import com.example.test.model.enums.TransactionType;
import com.example.test.service.processor.LedgerLeg;
import com.example.test.service.processor.TransactionContext;
import com.example.test.service.processor.TransactionProcessor;
import com.example.test.service.processor.TransactionProcessorFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionProcessorFactoryTest {

    private static TransactionProcessor stub(TransactionType type) {
        return new TransactionProcessor() {
            @Override
            public TransactionType supports() {
                return type;
            }

            @Override
            public void validate(TransactionContext context) {
            }

            @Override
            public List<LedgerLeg> buildLegs(TransactionContext context) {
                return List.of();
            }
        };
    }

    @Test
    void resolvesTheProcessorRegisteredForEachType() {
        TransactionProcessor transfer = stub(TransactionType.TRANSFER);
        TransactionProcessor funding = stub(TransactionType.FUNDING);
        TransactionProcessorFactory factory = new TransactionProcessorFactory(List.of(transfer, funding));

        assertThat(factory.forType(TransactionType.TRANSFER)).isSameAs(transfer);
        assertThat(factory.forType(TransactionType.FUNDING)).isSameAs(funding);
    }

    @Test
    void rejectsTwoProcessorsClaimingTheSameType() {
        List<TransactionProcessor> duplicates =
                List.of(stub(TransactionType.TRANSFER), stub(TransactionType.TRANSFER));

        assertThatThrownBy(() -> new TransactionProcessorFactory(duplicates))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRANSFER");
    }

    @Test
    void failsLoudlyWhenNoProcessorIsRegistered() {
        TransactionProcessorFactory factory =
                new TransactionProcessorFactory(List.of(stub(TransactionType.TRANSFER)));

        assertThatThrownBy(() -> factory.forType(TransactionType.FUNDING))
                .isInstanceOf(IllegalStateException.class);
    }
}
