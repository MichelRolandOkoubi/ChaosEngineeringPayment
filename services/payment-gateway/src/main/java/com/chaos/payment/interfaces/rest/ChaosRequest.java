package com.chaos.payment.interfaces.rest;
import lombok.Data;
@Data
public class ChaosRequest {
    private String scenario;
    private String target;
    private String duration;
}