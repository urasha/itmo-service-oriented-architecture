package ru.itmo.soa.contract;

import java.io.Serializable;

public record Coordinates(float x, double y) implements Serializable {}
