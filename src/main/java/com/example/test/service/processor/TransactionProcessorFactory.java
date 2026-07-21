package com.example.test.service.processor;

import com.example.test.model.enums.TransactionType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class TransactionProcessorFactory {

    private final Map<TransactionType, TransactionProcessor> processors = new EnumMap<>(TransactionType.class);

    public TransactionProcessorFactory(List<TransactionProcessor> available) {
        for (TransactionProcessor processor : available) {
            TransactionProcessor existing = processors.putIfAbsent(processor.supports(), processor);
            if (existing != null) {
                throw new IllegalStateException(
                        "Two processors registered for " + processor.supports());
            }
        }
    }

    public TransactionProcessor forType(TransactionType type) {
        TransactionProcessor processor = processors.get(type);
        if (processor == null) {
            throw new IllegalStateException("No processor registered for transaction type " + type);
        }
        return processor;
    }
}
