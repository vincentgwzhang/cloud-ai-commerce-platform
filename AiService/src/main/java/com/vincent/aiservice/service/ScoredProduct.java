package com.vincent.aiservice.service;

/**
 * A product code with an aggregated relevance score, produced by the behaviour-signal layer
 * (collaborative-filtering co-occurrence or global popularity).
 */
public record ScoredProduct(String productCode, double score) {
}
