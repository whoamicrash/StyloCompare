package com.stylo.core;

import java.util.ArrayList;
import java.util.List;

public class AnalysisResult {

    public final TextDocument doc1;
    public final TextDocument doc2;
    public final List<MetricResult> metrics;
    public final double weightedDistance;
    public final double similarity;
    public final double pTopic;
    public final Double selfDistance;
    public final Double pSelf;
    public final double rawProbability;
    public final double lengthFactor;
    public final double probability;
    public final double margin;
    public final String verdict;
    public final List<String> warnings;
    public final boolean identicalTexts;

    AnalysisResult(TextDocument d1, TextDocument d2, List<MetricResult> metrics,
                   double weightedDistance, double similarity, double pTopic,
                   Double selfDistance, Double pSelf, double rawProbability,
                   double lengthFactor, double probability, double margin,
                   String verdict, List<String> warnings, boolean identicalTexts) {
        this.doc1 = d1; this.doc2 = d2; this.metrics = metrics;
        this.weightedDistance = weightedDistance;
        this.similarity = similarity;
        this.pTopic = pTopic;
        this.selfDistance = selfDistance;
        this.pSelf = pSelf;
        this.rawProbability = rawProbability;
        this.lengthFactor = lengthFactor;
        this.probability = probability;
        this.margin = margin;
        this.verdict = verdict;
        this.warnings = warnings;
        this.identicalTexts = identicalTexts;
    }
}
