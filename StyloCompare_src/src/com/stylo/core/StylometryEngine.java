package com.stylo.core;

import java.util.ArrayList;
import java.util.List;

public final class StylometryEngine {

    private StylometryEngine() {}

    public static final int SHORT_WORDS_THRESHOLD = 500;
    public static final int CRITICAL_WORDS_THRESHOLD = 150;
    public static final double LOGISTIC_K = 120.0;
    public static final double LOGISTIC_CENTER = 0.66;
    public static final double SELF_K = 22.0;
    public static final double SELF_CENTER = 0.03;
    public static final double SELF_MIX = 0.4;
    private static final double CALIBRATION_NORM = 2000.0;

    public static AnalysisResult analyze(TextDocument d1, TextDocument d2) {
        List<MetricResult> metrics = FeatureExtractor.extract(d1, d2);

        boolean identical = d1.text.equals(d2.text) && !d1.text.isEmpty();

        double wSum = 0, wd = 0;
        for (MetricResult m : metrics) {
            if (m.neutral) continue;
            wd += m.weight * m.distance; wSum += m.weight;
        }
        double D = wSum > 0 ? wd / wSum : 0;
        if (identical) D = 0;
        double S = 1.0 - D;

        double pTopic = logistic(LOGISTIC_K, S - LOGISTIC_CENTER);

        Double selfDistance = null;
        Double pSelf = null;
        Double pSelfUsed = null;
        if (!identical) {
            selfDistance = selfBaseline(d1, d2);
            if (selfDistance != null) {
                pSelf = logistic(SELF_K, (selfDistance - D) - SELF_CENTER);
                if (pSelf > 0.5) pSelfUsed = pSelf;
            }
        }

        double P0 = pTopic;
        if (pSelfUsed != null) {
            double mixed = (1.0 - SELF_MIX) * pTopic + SELF_MIX * pSelfUsed;
            if (mixed > P0) P0 = mixed;
        }

        int n1 = d1.wordCount, n2 = d2.wordCount;
        int nMin = Math.min(n1, n2);
        double f = Math.min(1.0, Math.log10(1.0 + nMin) / Math.log10(1.0 + CALIBRATION_NORM));

        List<String> warnings = new ArrayList<String>();

        if (n1 < SHORT_WORDS_THRESHOLD) warnings.add("Файл 1: " + n1 + " слов (< " + SHORT_WORDS_THRESHOLD + "), точность низкая");
        if (n2 < SHORT_WORDS_THRESHOLD) warnings.add("Файл 2: " + n2 + " слов (< " + SHORT_WORDS_THRESHOLD + "), точность низкая");
        if (n1 < CRITICAL_WORDS_THRESHOLD || n2 < CRITICAL_WORDS_THRESHOLD) {
            warnings.add("Текст короче " + CRITICAL_WORDS_THRESHOLD + " слов, оценка почти случайная");
            f *= 0.8;
        }
        boolean cross = TextDocument.scriptsFundamentallyDiffer(d1, d2);
        if (cross) {
            warnings.add("Разные письменности (" + d1.dominantScript + " и " + d2.dominantScript + "), сравнение между языками");
            f *= 0.7;
        }
        if (pSelfUsed != null && pTopic < 0.35) {
            warnings.add("Фрагменты внутри текстов ближе друг к другу, чем тексты между собой, итог неустойчив");
            f *= 0.9;
        }
        if (identical) {
            warnings.add("Тексты побитово идентичны: вероятность принята максимальной");
            f = 1.0;
        }
        f = Math.min(1.0, f);

        double P = 0.5 + (P0 - 0.5) * f;
        if (identical) P = Math.max(P, 0.99);
        P = Math.min(0.999, Math.max(0.001, P));

        double margin = 0.03 + 0.25 * (1.0 - f);

        String verdict = verdictFor(P);
        return new AnalysisResult(d1, d2, metrics, D, S, pTopic, selfDistance, pSelf, P0, f, P, margin, verdict, warnings, identical);
    }

    private static Double selfBaseline(TextDocument d1, TextDocument d2) {
        List<String> c1 = FeatureExtractor.splitChunks(d1);
        List<String> c2 = FeatureExtractor.splitChunks(d2);
        if (c1.size() < 2 || c2.size() < 2) return null;
        if (!enoughWords(c1) || !enoughWords(c2)) return null;
        double s1 = meanPairDistance(c1);
        double s2 = meanPairDistance(c2);
        return 0.5 * (s1 + s2);
    }

    private static boolean enoughWords(List<String> chunks) {
        for (String c : chunks) {
            if (TextDocument.tokenize(c.toLowerCase()).size() < 100) return false;
        }
        return true;
    }

    private static double meanPairDistance(List<String> chunks) {
        List<TextDocument> docs = new ArrayList<TextDocument>();
        for (int i = 0; i < chunks.size(); i++) {
            docs.add(TextDocument.fromString("chunk" + i, chunks.get(i)));
        }
        double sum = 0;
        int n = 0;
        for (int i = 0; i < docs.size(); i++) {
            for (int j = i + 1; j < docs.size(); j++) {
                sum += distance(docs.get(i), docs.get(j));
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    public static double distance(TextDocument a, TextDocument b) {
        double ws = 0, wd = 0;
        for (MetricResult m : FeatureExtractor.extract(a, b)) {
            if (m.neutral) continue;
            wd += m.weight * m.distance; ws += m.weight;
        }
        return ws > 0 ? wd / ws : 0;
    }

    private static double logistic(double k, double x) {
        return 1.0 / (1.0 + Math.exp(-k * x));
    }

    public static String verdictFor(double p) {
        if (p >= 0.85) return "ВЫСОКАЯ ВЕРОЯТНОСТЬ ОДНОГО АВТОРА";
        if (p >= 0.65) return "СРЕДНЯЯ ВЕРОЯТНОСТЬ ОДНОГО АВТОРА";
        if (p > 0.35)  return "НЕОПРЕДЕЛЁННЫЙ РЕЗУЛЬТАТ (inconclusive)";
        if (p > 0.15)  return "СРЕДНЯЯ ВЕРОЯТНОСТЬ РАЗНЫХ АВТОРОВ";
        return "ВЫСОКАЯ ВЕРОЯТНОСТЬ РАЗНЫХ АВТОРОВ";
    }
}
