package com.chaos.payment.application.query.handler;
import com.chaos.payment.application.query.GetPaymentQuery;
import com.chaos.payment.application.query.ListPaymentsQuery;
import com.chaos.payment.domain.event.DomainEvent;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.List;
import java.util.Collections;
@ApplicationScoped
public class GetPaymentQueryHandler {
    public Optional<Object> handle(GetPaymentQuery query) { return Optional.empty(); }
    public List<Object> listPayments(ListPaymentsQuery query) { return Collections.emptyList(); }
    public List<DomainEvent> getEventHistory(String transactionId) { return Collections.emptyList(); }
}