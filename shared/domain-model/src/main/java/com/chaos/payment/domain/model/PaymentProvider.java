package com.chaos.payment.domain.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Payment Provider Value Object
 * Supports all African and International payment providers
 */
@RegisterForReflection
public enum PaymentProvider {

    // African Mobile Money Providers
    ORANGE("ORANGE", "Orange Money", "XOF,XAF,GNF,SEN", true, "mobile"),
    MOOV("MOOV", "Moov Money", "XOF,XAF", true, "mobile"),
    MTN("MTN", "MTN Mobile Money", "GHS,NGN,UGX,RWF,ZMW,XOF,XAF", true, "mobile"),
    WAVE("WAVE", "Wave Mobile Money", "XOF,GNF", true, "mobile"),
    AIRTEL("AIRTEL", "Airtel Money", "KES,TZS,UGX,ZMW,MWK,RWF,NGN", true, "mobile"),
    MPESA("MPESA", "M-Pesa", "KES,TZS,GHS,MOZ,EGP,UGX", true, "mobile"),

    // International Card Providers
    VISA("VISA", "Visa", "USD,EUR,GBP,XOF,XAF", false, "card"),
    MASTERCARD("MASTERCARD", "Mastercard", "USD,EUR,GBP,XOF,XAF", false, "card"),

    // Crypto
    BTC("BTC", "Bitcoin", "BTC,USD", false, "crypto"),

    // BCEAO Interbank System
    PI_SPI_BCEAO("PI_SPI_BCEAO", "PI/SPI BCEAO Interbank", "XOF", false, "interbank");

    private final String code;
    private final String name;
    private final String supportedCurrencies;
    private final boolean isMobileMoney;
    private final String category;

    PaymentProvider(String code, String name, String supportedCurrencies,
            boolean isMobileMoney, String category) {
        this.code = code;
        this.name = name;
        this.supportedCurrencies = supportedCurrencies;
        this.isMobileMoney = isMobileMoney;
        this.category = category;
    }

    public static PaymentProvider fromCode(String code) {
        for (PaymentProvider provider : values()) {
            if (provider.code.equalsIgnoreCase(code)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown payment provider: " + code);
    }

    public boolean supportsCurrency(String currencyCode) {
        return supportedCurrencies.contains(currencyCode);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSupportedCurrencies() {
        return supportedCurrencies;
    }

    public boolean isMobileMoney() {
        return isMobileMoney;
    }

    public String getCategory() {
        return category;
    }
}