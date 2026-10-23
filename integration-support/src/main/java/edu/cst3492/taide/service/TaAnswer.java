package edu.cst3492.taide.service;

import java.util.List;

public record TaAnswer(String text, List<Citation> citations, boolean grounded, String model) {}
