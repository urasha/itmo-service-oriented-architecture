package ru.itmo.soa.contract;

import java.time.Instant;

public record ApiError(Instant timestamp, String message) {}
