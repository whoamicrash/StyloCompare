package com.stylo.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public final class ReportBuilder {

    private ReportBuilder() {}

    public static String build(AnalysisResult r) {
        StringBuilder sb = new StringBuilder(4096);
        TextDocument d1 = r.doc1, d2 = r.doc2;
        int nMin = Math.min(d1.wordCount, d2.wordCount);

        line(sb, "===============================================================================");
        line(sb, "  СТИЛОМЕТРИЯ            "
                + new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date()));
        line(sb, "===============================================================================");
        line(sb, "ВЕРДИКТ: " + r.verdict);
        line(sb, String.format(java.util.Locale.US, "Один автор: %.1f %%  (± %.1f п.п.)",
                r.probability * 100, r.margin * 100));
        line(sb, "");

        line(sb, "ТЕКСТЫ");
        line(sb, "  1: " + shortName(d1.title));
        line(sb, "     " + d1.wordCount + " слов, " + d1.scriptDescription() + ", " + d1.charsetUsed);
        line(sb, "  2: " + shortName(d2.title));
        line(sb, "     " + d2.wordCount + " слов, " + d2.scriptDescription() + ", " + d2.charsetUsed);
        line(sb, "");

        line(sb, "МЕТРИКИ (d = расстояние [0..1], w = вес; меньше d = больше сходства)");
        for (MetricResult m : r.metrics) {
            line(sb, String.format(java.util.Locale.US, "  %-4s %-36s d=%-7.4f w=%.2f%s",
                    m.key, trim(m.name, 36), m.distance, m.weight, m.neutral ? "  (нейтр.)" : ""));
        }
        line(sb, String.format(java.util.Locale.US, "  Итог D = %.4f, сходство S = %.4f",
                r.weightedDistance, r.similarity));
        line(sb, "");

        line(sb, "РАСЧЁТ");
        line(sb, String.format(java.util.Locale.US,
                "  P(тема) = logistic(k=%.0f, c=%.2f) = %.3f",
                StylometryEngine.LOGISTIC_K, StylometryEngine.LOGISTIC_CENTER, r.pTopic));
        if (r.pSelf != null && r.selfDistance != null) {
            boolean used = r.pSelf > 0.5 && r.rawProbability > r.pTopic + 1e-9;
            line(sb, String.format(java.util.Locale.US,
                    "  P(стиль) = %.3f (Dself = %.3f)%s",
                    r.pSelf, r.selfDistance, used ? "" : " (не понижает)"));
            if (used) {
                line(sb, String.format(java.util.Locale.US,
                        "  P0 = %.1f*P(тема) + %.1f*P(стиль) = %.3f",
                        1.0 - StylometryEngine.SELF_MIX, StylometryEngine.SELF_MIX, r.rawProbability));
            } else {
                line(sb, String.format(java.util.Locale.US, "  P0 = P(тема) = %.3f", r.rawProbability));
            }
        } else {
            line(sb, "  Самопроверка недоступна (короткий текст), P0 = P(тема)");
        }
        if (r.identicalTexts) {
            line(sb, "  Тексты идентичны, P принята максимальной");
        } else {
            line(sb, String.format(java.util.Locale.US,
                    "  f(объём, Nmin=%d) = %.3f, P = 0.5 + (P0-0.5)*f = %.3f", nMin, r.lengthFactor, r.probability));
        }
        line(sb, "");

        line(sb, "ОБОСНОВАНИЕ");
        if (r.probability >= 0.65) {
            line(sb, "  Совпадают сильнее всего:");
            topMetrics(sb, r, true, 3);
        } else if (r.probability <= 0.35) {
            line(sb, "  Различаются сильнее всего:");
            topMetrics(sb, r, false, 3);
        } else {
            line(sb, "  Метрики расходятся, уверенного ответа нет.");
            line(sb, "  Совпадают:"); topMetrics(sb, r, true, 2);
            line(sb, "  Различаются:"); topMetrics(sb, r, false, 2);
        }
        line(sb, "");

        if (!r.warnings.isEmpty()) {
            line(sb, "ПРЕДУПРЕЖДЕНИЯ");
            for (String w : r.warnings) line(sb, "  ! " + w);
            line(sb, "");
        }

        line(sb, "ОГРАНИЧЕНИЯ");
        line(sb, "  Оценка вероятностная. Тема, жанр, цитаты и машинная генерация");
        line(sb, "  смещают вердикт. Меньше 500 слов: точность падает.");
        line(sb, "===============================================================================");
        return sb.toString();
    }

    private static void topMetrics(StringBuilder sb, AnalysisResult r, boolean lowest, int count) {
        List<MetricResult> ms = new java.util.ArrayList<MetricResult>(r.metrics);
        java.util.Collections.sort(ms, new java.util.Comparator<MetricResult>() {
            public int compare(MetricResult a, MetricResult b) { return Double.compare(a.distance, b.distance); }
        });
        int printed = 0;
        for (int i = 0; i < ms.size() && printed < count; i++) {
            MetricResult m = ms.get(i);
            if (m.neutral) continue;
            if (lowest && m.distance > 0.5) continue;
            if (!lowest && m.distance < 0.5) continue;
            line(sb, String.format(java.util.Locale.US, "    - %s: d = %.3f", m.name, m.distance));
            printed++;
        }
        if (printed == 0) {
            for (int i = 0; i < ms.size() && printed < count; i++) {
                MetricResult m = lowest ? ms.get(ms.size() - 1 - i) : ms.get(i);
                if (m.neutral) continue;
                line(sb, String.format(java.util.Locale.US, "    - %s: d = %.3f", m.name, m.distance));
                printed++;
            }
        }
    }

    private static String shortName(String s) {
        if (s == null) return "";
        String one = s.replace("\n", " ");
        return one.length() <= 70 ? one : one.substring(0, 69) + "…";
    }

    private static String trim(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    private static void line(StringBuilder sb, String s) {
        sb.append(s).append('\n');
    }
}
