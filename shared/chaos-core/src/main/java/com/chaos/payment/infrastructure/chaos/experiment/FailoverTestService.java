package com.chaos.payment.infrastructure.chaos.experiment;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FailoverTestService {
    public Uni<FailoverResult> testRegionFailover() {
        return Uni.createFrom().item(new FailoverResult(true, 5000));
    }
}
