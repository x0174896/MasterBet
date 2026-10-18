package it.masterbet.odds.domain;

import java.math.BigDecimal;
import java.util.Map;

public record Market(
    String id,
    String name,
    String type,
    String period,
    BigDecimal line,
    boolean playerProp,
    Map<String, String> outcomes) {}
