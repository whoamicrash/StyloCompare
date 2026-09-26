package com.stylo.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FeatureExtractor {

    private FeatureExtractor() {}

    private static final int[] SENT_BINS = bins(40);
    private static final int[] WORD_BINS = bins(15);

    private static int[] bins(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i + 1;
        return a;
    }

    public static List<MetricResult> extract(TextDocument d1, TextDocument d2) {
        List<MetricResult> res = new ArrayList<MetricResult>();
        res.add(charNgramMetric(d1, d2, 3, 0.18, "M1"));
        res.add(charNgramMetric(d1, d2, 2, 0.12, "M2"));
        res.add(mfwCanberraMetric(d1, d2));
        res.add(wordJsdMetric(d1, d2));
        res.add(char1JsdMetric(d1, d2));
        res.add(sentenceLengthKsMetric(d1, d2));
        res.add(wordLengthJsdMetric(d1, d2));
        res.add(punctuationMetric(d1, d2));
        res.add(richnessMetric(d1, d2));
        res.add(mfwJaccardMetric(d1, d2));
        res.add(typographyMetric(d1, d2));
        res.add(cosineDeltaZMetric(d1, d2));
        res.add(unmaskingMetric(d1, d2));
        res.add(zipfMetric(d1, d2));
        res.add(paragraphMetric(d1, d2));
        res.add(compressionNcdMetric(d1, d2));
        res.add(minMaxMetric(d1, d2));
        return res;
    }

    static List<String> splitChunks(TextDocument d) {
        List<String> out = new ArrayList<String>();
        int n = d.wordCount;
        int k = n >= 1400 ? 4 : n >= 700 ? 3 : n >= 260 ? 2 : 0;
        if (k == 0) return out;
        String t = d.text;
        int len = t.length();
        int prev = 0;
        for (int i = 1; i < k; i++) {
            int target = (int) Math.round((double) len * i / k);
            int cut = nearestBreak(t, target, prev + 1);
            if (cut <= prev || cut >= len) cut = Math.max(prev + 1, Math.min(target, len - 1));
            out.add(t.substring(prev, cut));
            prev = cut;
        }
        if (prev < len) out.add(t.substring(prev));
        return out;
    }

    private static int nearestBreak(String t, int target, int minPos) {
        int span = Math.max(1, t.length() / 8);
        int lo = Math.max(minPos, target - span);
        int hi = Math.min(t.length() - 1, target + span);
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = lo; i <= hi; i++) {
            char pc = t.charAt(i - 1);
            double w;
            if (pc == '\n') w = 0.5;
            else if (pc == '.' || pc == '!' || pc == '?' || pc == '…' || pc == '。') w = 0.75;
            else if (pc == ' ' || pc == '\t') w = 1.0;
            else continue;
            double score = w * Math.abs(i - target);
            if (score < bestScore) { bestScore = score; best = i; }
        }
        return best > 0 ? best : target;
    }

    private static MetricResult cosineDeltaZMetric(TextDocument d1, TextDocument d2) {
        List<String> c1 = splitChunks(d1);
        List<String> c2 = splitChunks(d2);
        String v1, v2;
        if (c1.size() < 2 || c2.size() < 2) {
            v1 = c1.size() + " фрагм.";
            v2 = c2.size() + " фрагм.";
            return new MetricResult("M12", "Косинус-Delta (z-оценки)", v1, v2, 0.5, 0.04,
                    "Текст слишком короток для разбивки на фрагменты: метрика нейтральна", true);
        }
        List<String> pool = new ArrayList<String>();
        pool.addAll(c1); pool.addAll(c2);
        List<Map<String, Double>> rel = new ArrayList<Map<String, Double>>();
        for (String c : pool) rel.add(relFreq(countWords(TextDocument.tokenize(c.toLowerCase()))));
        Map<String, Double> pooled = new HashMap<String, Double>();
        for (Map<String, Double> r : rel) {
            for (Map.Entry<String, Double> e : r.entrySet()) {
                Double old = pooled.get(e.getKey());
                pooled.put(e.getKey(), (old == null ? 0.0 : old) + e.getValue() / rel.size());
            }
        }
        List<String> feats = topKeys(pooled, 150);
        int m = feats.size();
        double[][] z = new double[rel.size()][m];
        for (int j = 0; j < m; j++) {
            String f = feats.get(j);
            double mean = 0;
            for (Map<String, Double> r : rel) { Double v = r.get(f); mean += v == null ? 0 : v; }
            mean /= rel.size();
            double var = 0;
            for (Map<String, Double> r : rel) { Double v = r.get(f); double d0 = (v == null ? 0 : v) - mean; var += d0 * d0; }
            double sd = Math.sqrt(var / Math.max(1, rel.size() - 1));
            for (int s = 0; s < rel.size(); s++) {
                Double v = rel.get(s).get(f);
                double zz = sd > 0 ? ((v == null ? 0 : v) - mean) / sd : 0;
                z[s][j] = Math.max(-2.0, Math.min(2.0, zz));
            }
        }
        double[] cen1 = new double[m], cen2 = new double[m];
        for (int j = 0; j < m; j++) {
            for (int s = 0; s < c1.size(); s++) cen1[j] += z[s][j] / c1.size();
            for (int s = c1.size(); s < rel.size(); s++) cen2[j] += z[s][j] / c2.size();
        }
        double cos = cosArr(cen1, cen2);
        double d = Math.max(0.0, Math.min(1.0, 1.0 - cos));
        return new MetricResult("M12", "Косинус-Delta (z-оценки)",
                c1.size() + " фрагм.", c2.size() + " фрагм.", d, 0.04,
                "Канонический Cosine Delta Эверта: z-нормировка (винзоризация ±2) частот " + feats.size()
                        + " частотных слов по фрагментам, косинус центроидов текстов");
    }

    private static MetricResult unmaskingMetric(TextDocument d1, TextDocument d2) {
        Map<String, Double> g1 = relFreq(ngramCounts(d1.lettersLower(), 3));
        Map<String, Double> g2 = relFreq(ngramCounts(d2.lettersLower(), 3));
        Map<String, Double> w1 = relFreq(countWords(d1.words));
        Map<String, Double> w2 = relFreq(countWords(d2.words));
        double before = 0.5 * (1.0 - cosine(g1, g2)) + 0.5 * jsd(w1, w2);
        if (before < 0.02) {
            return new MetricResult("M13", "Устойчивость различия (unmasking)", "d0=" + fmt(before, 3), "d0=" + fmt(before, 3),
                    0.0, 0.10, "Тексты почти неотличимы: различие устойчиво мало");
        }
        if (Math.min(d1.wordCount, d2.wordCount) < 800) {
            return new MetricResult("M13", "Устойчивость различия (unmasking)", "<800 сл.", "<800 сл.",
                    0.5, 0.10, "Unmasking требует длинных текстов (≥800 слов): метрика нейтральна", true);
        }
        Set<String> dropG = topDiffKeys(g1, g2, Math.min(150, Math.max(1, (g1.size() + g2.size()) / 6)));
        Set<String> dropW = topDiffKeys(w1, w2, Math.min(120, Math.max(1, (w1.size() + w2.size()) / 6)));
        Map<String, Double> rg1 = renorm(dropKeys(g1, dropG));
        Map<String, Double> rg2 = renorm(dropKeys(g2, dropG));
        Map<String, Double> rw1 = renorm(dropKeys(w1, dropW));
        Map<String, Double> rw2 = renorm(dropKeys(w2, dropW));
        double after = 0.5 * (1.0 - cosine(rg1, rg2)) + 0.5 * jsd(rw1, rw2);
        double d = Math.max(0.0, Math.min(1.0, after / before));
        return new MetricResult("M13", "Устойчивость различия (unmasking)",
                "d0=" + fmt(before, 2) + "→" + fmt(after, 2),
                "d0=" + fmt(before, 2) + "→" + fmt(after, 2), d, 0.10,
                "Аналог unmasking Коппеля-Шлера: до 150 триграмм и 120 слов с максимальным различием удалены; "
                        + "d: доля различия, уцелевшая после удаления (мало = различие было тематическим)");
    }

    private static Set<String> topDiffKeys(Map<String, Double> p1, Map<String, Double> p2, int k) {
        Set<String> uni = new HashSet<String>(p1.keySet()); uni.addAll(p2.keySet());
        List<Map.Entry<String, Double>> diff = new ArrayList<Map.Entry<String, Double>>();
        for (String key : uni) {
            double a = p1.containsKey(key) ? p1.get(key) : 0.0;
            double b = p2.containsKey(key) ? p2.get(key) : 0.0;
            diff.add(new java.util.AbstractMap.SimpleEntry<String, Double>(key, Math.abs(a - b)));
        }
        java.util.Collections.sort(diff, new java.util.Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) { return Double.compare(b.getValue(), a.getValue()); }
        });
        Set<String> out = new HashSet<String>();
        for (int i = 0; i < diff.size() && i < k; i++) out.add(diff.get(i).getKey());
        return out;
    }

    private static Map<String, Double> dropKeys(Map<String, Double> p, Set<String> drop) {
        Map<String, Double> out = new HashMap<String, Double>();
        for (Map.Entry<String, Double> e : p.entrySet()) if (!drop.contains(e.getKey())) out.put(e.getKey(), e.getValue());
        return out;
    }

    private static Map<String, Double> renorm(Map<String, Double> p) {
        double sum = 0;
        for (Double v : p.values()) sum += v;
        if (sum <= 0) return p;
        Map<String, Double> out = new HashMap<String, Double>();
        for (Map.Entry<String, Double> e : p.entrySet()) out.put(e.getKey(), e.getValue() / sum);
        return out;
    }

    private static MetricResult zipfMetric(TextDocument d1, TextDocument d2) {
        Map<String, Integer> c1 = countWords(d1.words);
        Map<String, Integer> c2 = countWords(d2.words);
        if (c1.size() < 30 || c2.size() < 30) {
            return new MetricResult("M14", "Наклон Ципфа", "", "", 0.5, 0.04,
                    "Словарь меньше 30 слов: метрика нейтральна", true);
        }
        double s1 = zipfSlope(c1), s2 = zipfSlope(c2);
        double d = Math.max(0.0, Math.min(1.0, Math.abs(s1 - s2) / 0.30));
        return new MetricResult("M14", "Наклон Ципфа",
                "s=" + fmt(s1, 2), "s=" + fmt(s2, 2), d, 0.04,
                "Наклон ранг-частотной кривой (закон Ципфа): глобальная структура словаря, слабо зависит от темы");
    }

    private static double zipfSlope(Map<String, Integer> counts) {
        List<Integer> fs = new ArrayList<Integer>(counts.values());
        java.util.Collections.sort(fs, java.util.Collections.reverseOrder());
        int n = fs.size();
        int lo = 10, hi = Math.min(n, 1000);
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int m = 0;
        for (int r = lo; r <= hi; r++) {
            double x = Math.log(r), y = Math.log(fs.get(r - 1));
            sx += x; sy += y; sxx += x * x; sxy += x * y; m++;
        }
        double den = m * sxx - sx * sx;
        return den == 0 ? 0 : (m * sxy - sx * sy) / den;
    }

    private static MetricResult paragraphMetric(TextDocument d1, TextDocument d2) {
        List<Integer> p1 = paragraphWordCounts(d1.text);
        List<Integer> p2 = paragraphWordCounts(d2.text);
        if (p1.size() < 4 || p2.size() < 4) {
            return new MetricResult("M15", "Ритм абзацев (KS)", p1.size() + " абз.", p2.size() + " абз.", 0.5, 0.04,
                    "Менее 4 абзацев: метрика нейтральна", true);
        }
        double[] h1 = histogram(p1, 60), h2 = histogram(p2, 60);
        double d = ksStatistic(h1, h2);
        return new MetricResult("M15", "Ритм абзацев (KS)",
                p1.size() + " абз.", p2.size() + " абз.", d, 0.04,
                "Статистика Колмогорова-Смирнова по распределению длины абзацев (слов в абзаце)");
    }

    private static List<Integer> paragraphWordCounts(String text) {
        List<Integer> out = new ArrayList<Integer>();
        List<String> paras = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '\n') {
                int nl = 0;
                while (i < text.length() && text.charAt(i) == '\n') { nl++; i++; }
                if (cur.length() > 0) paras.add(cur.toString());
                cur.setLength(0);
                if (nl >= 2 && !paras.isEmpty()) { paras.add("\u0000PARA"); }
                continue;
            }
            cur.append(text.charAt(i));
            i++;
        }
        if (cur.length() > 0) paras.add(cur.toString());
        boolean blank = paras.contains("\u0000PARA");
        List<String> units = new ArrayList<String>();
        for (String p : paras) {
            if (p.equals("\u0000PARA")) continue;
            if (blank) units.add(p);
            else {
                for (String line : p.split("\\n")) units.add(line);
            }
        }
        for (String u : units) {
            int wc = TextDocument.tokenize(u.toLowerCase()).size();
            if (wc > 0) out.add(wc);
        }
        return out;
    }

    private static MetricResult compressionNcdMetric(TextDocument d1, TextDocument d2) {
        if (Math.min(d1.wordCount, d2.wordCount) < 200) {
            return new MetricResult("M16", "Кросс-сжатие со словарём (zlib)", "<200 сл.", "<200 сл.",
                    0.5, 0.10, "Компрессионный перенос требует не менее 200 слов в каждом тексте: метрика нейтральна", true);
        }
        String s1 = wordStream(d1.words), s2 = wordStream(d2.words);
        byte[] b1 = s1.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b2 = s2.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long c1 = deflateSize(b1, null), c2 = deflateSize(b2, null);
        long c12 = deflateSize(b1, b2), c21 = deflateSize(b2, b1);
        if (c1 <= 0 || c2 <= 0 || c12 <= 0 || c21 <= 0) {
            return new MetricResult("M16", "Кросс-сжатие со словарём (zlib)", "ошибка", "ошибка",
                    0.5, 0.10, "Компрессор недоступен: метрика нейтральна", true);
        }
        double r = 0.5 * (c12 / (double) c1 + c21 / (double) c2);
        double d = Math.max(0.0, Math.min(1.0, (r - 0.82) / 0.18));
        return new MetricResult("M16", "Кросс-сжатие со словарём (zlib)",
                "C=" + c1, "C=" + c2, d, 0.10,
                "Компрессионный перенос (кросс-парсинг): каждый текст сжимается со словарём другого; если словарь сокращает сжатие: тексты пишущи в одной манере (Benedetto et al., компрессионные модели верификации)");
    }

    private static String wordStream(List<String> words) {
        StringBuilder sb = new StringBuilder();
        int k = 0;
        for (String w : words) {
            sb.append(w).append(' ');
            if (++k >= 4000) break;
        }
        return sb.toString();
    }

    private static long deflateSize(byte[] data, byte[] dict) {
        try {
            java.util.zip.Deflater df = new java.util.zip.Deflater(9, false);
            if (dict != null && dict.length > 0) df.setDictionary(dict);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(data.length / 2 + 64);
            byte[] buf = new byte[8192];
            df.setInput(data);
            df.finish();
            while (!df.finished()) {
                int n = df.deflate(buf);
                if (n == 0 && df.needsInput()) break;
                bos.write(buf, 0, n);
            }
            df.end();
            return bos.size();
        } catch (Exception e) {
            return -1;
        }
    }

    private static MetricResult minMaxMetric(TextDocument d1, TextDocument d2) {
        Map<String, Double> f1 = relFreq(ngramCounts(d1.lettersLower(), 3));
        Map<String, Double> f2 = relFreq(ngramCounts(d2.lettersLower(), 3));
        if (f1.isEmpty() || f2.isEmpty()) {
            return new MetricResult("M17", "Min-max сходство 3-грамм", "", "",
                    0.5, 0.10, "Профили 3-грамм пусты: метрика нейтральна", true);
        }
        List<String> top = topCombined(f1, f2, 400);
        double sumMin = 0, sumMax = 0;
        for (String k : top) {
            Double a = f1.get(k), b = f2.get(k);
            double x = a == null ? 0.0 : a.doubleValue();
            double y = b == null ? 0.0 : b.doubleValue();
            sumMin += Math.min(x, y);
            sumMax += Math.max(x, y);
        }
        double sim = sumMax > 0 ? sumMin / sumMax : 0.0;
        double d = Math.max(0.0, Math.min(1.0, 1.0 - sim));
        return new MetricResult("M17", "Min-max сходство 3-грамм",
                "V=" + f1.size(), "V=" + f2.size(), d, 0.10,
                "Min-max по топ-400 3-граммам объединённого профиля (метод Кошера-Савуа, победитель PAN): линейная геометрия вместо косинусной, устойчива к редким гапакс-граммам");
    }

    private static MetricResult charNgramMetric(TextDocument d1, TextDocument d2, int n, double w, String key) {
        String s1 = d1.lettersLower(), s2 = d2.lettersLower();
        Map<String, Double> f1 = relFreq(ngramCounts(s1, n));
        Map<String, Double> f2 = relFreq(ngramCounts(s2, n));
        int topN = n == 3 ? 300 : 250;
        List<String> top = topCombined(f1, f2, topN);
        double d = 1.0 - cosineSubspace(f1, f2, top);
        String name = n == 3 ? "Символьные 3-граммы (косинус)"
                             : "Символьные 2-граммы (косинус)";
        return new MetricResult(key, name,
                "V=" + f1.size(),
                "V=" + f2.size(),
                d, w,
                n == 3 ? "Частоты триграмм букв (топ-" + top.size() + " по объединённому профилю): устойчивый межъязыковой признак"
                       : "Частоты биграмм букв (топ-" + top.size() + " по объединённому профилю): орфографические привычки");
    }

    private static MetricResult mfwCanberraMetric(TextDocument d1, TextDocument d2) {
        Map<String, Double> r1 = relFreq1000(countWords(d1.words));
        Map<String, Double> r2 = relFreq1000(countWords(d2.words));
        List<String> top = topCombined(r1, r2, 100);
        double eps = 0.25;
        double sum = 0; int terms = 0;
        for (String k : top) {
            double a = r1.containsKey(k) ? r1.get(k) : 0.0;
            double b = r2.containsKey(k) ? r2.get(k) : 0.0;
            sum += Math.abs(a - b) / (a + b + 2 * eps);
            terms++;
        }
        double d = terms == 0 ? 0 : sum / terms;
        return new MetricResult("M3", "MFW-Канберра (аналог Delta)",
                "топ-100",
                "топ-100",
                d, 0.12,
                "Канберра (со сглаживанием ε=0.25) по 100 словам объединённого частотного профиля: вариация Delta Бёрроуза");
    }

    private static MetricResult wordJsdMetric(TextDocument d1, TextDocument d2) {
        Map<String, Double> full1 = relFreq(countWords(d1.words));
        Map<String, Double> full2 = relFreq(countWords(d2.words));
        List<String> top = topCombined(full1, full2, 300);
        Map<String, Double> p1 = restrictAndRenorm(full1, top);
        Map<String, Double> p2 = restrictAndRenorm(full2, top);
        double d = jsd(p1, p2);
        return new MetricResult("M4", "JSD распределения слов",
                "T=" + full1.size(),
                "T=" + full2.size(),
                d, 0.08,
                "Расхождение Дженсена-Шеннона (топ-300 слов объединённого профиля); T: весь словарь текста");
    }

    private static MetricResult char1JsdMetric(TextDocument d1, TextDocument d2) {
        Map<String, Double> p1 = relFreq(ngramCounts(d1.lettersLower(), 1));
        Map<String, Double> p2 = relFreq(ngramCounts(d2.lettersLower(), 1));
        double d = jsd(p1, p2);
        return new MetricResult("M5", "JSD частот букв",
                "H=" + fmt(shannon(p1), 3),
                "H=" + fmt(shannon(p2), 3),
                d, 0.04,
                "Энтропия и распределение букв (H: энтропия Шеннона, бит/буква ≈ ln-норм.)");
    }

    private static MetricResult sentenceLengthKsMetric(TextDocument d1, TextDocument d2) {
        double[] h1 = histogram(d1.sentenceLengths, SENT_BINS.length);
        double[] h2 = histogram(d2.sentenceLengths, SENT_BINS.length);
        double ks = ksStatistic(h1, h2);
        Stats s1 = stats(d1.sentenceLengths), s2 = stats(d2.sentenceLengths);
        return new MetricResult("M6", "Длина предложений (KS)",
                "ср=" + fmt(s1.mean, 1) + "; σ=" + fmt(s1.sd, 1),
                "ср=" + fmt(s2.mean, 1) + "; σ=" + fmt(s2.sd, 1),
                ks, 0.10,
                "Статистика Колмогорова-Смирнова между гистограммами длин предложений; σ: ст. отклонение");
    }

    private static MetricResult wordLengthJsdMetric(TextDocument d1, TextDocument d2) {
        List<Integer> l1 = wordLengths(d1.words), l2 = wordLengths(d2.words);
        double[] h1 = histogram(l1, WORD_BINS.length), h2 = histogram(l2, WORD_BINS.length);
        double d = jsdHist(h1, h2);
        Stats s1 = stats(l1), s2 = stats(l2);
        return new MetricResult("M7", "Длина слов (JSD)",
                "ср=" + fmt(s1.mean, 2),
                "ср=" + fmt(s2.mean, 2),
                d, 0.06,
                "Расхождение распределений длины слов; средняя длина слова указана в буквах");
    }

    private static MetricResult punctuationMetric(TextDocument d1, TextDocument d2) {
        double[] v1 = punctVector(d1), v2 = punctVector(d2);
        double s1 = 0, s2 = 0;
        for (int i = 0; i < v1.length; i++) { s1 += v1[i]; s2 += v2[i]; }
        double d = jsdHist(smoothProps(v1), smoothProps(v2));
        return new MetricResult("M8", "Пунктуационный профиль",
                fmt(s1, 1) + "/1000",
                fmt(s2, 1) + "/1000",
                d, 0.12,
                "Расхождение Дженсена-Шеннона распределений знаков препинания (со сглаживанием, на 1000 букв)");
    }

    private static double[] smoothProps(double[] v) {
        double[] p = new double[v.length];
        double sum = 0;
        for (int i = 0; i < v.length; i++) { p[i] = v[i] + 0.5; sum += p[i]; }
        for (int i = 0; i < p.length; i++) p[i] /= sum;
        return p;
    }

    private static MetricResult richnessMetric(TextDocument d1, TextDocument d2) {
        Richness r1 = richness(d1.words), r2 = richness(d2.words);
        double dr = relDiff(r1.guiraud, r2.guiraud);
        double dh = relDiff(r1.hapaxRatio, r2.hapaxRatio);
        double dk = logRelDiff(r1.yuleK, r2.yuleK);
        double de = logRelDiff(r1.entropy, r2.entropy);
        double d = (dr + dh + dk + de) / 4.0;
        return new MetricResult("M9", "Богатство словаря",
                "R=" + fmt(r1.guiraud, 1) + "; V1/V=" + fmt(r1.hapaxRatio * 100, 0) + "%; K=" + fmt(r1.yuleK, 0) + "; H=" + fmt(r1.entropy, 2),
                "R=" + fmt(r2.guiraud, 1) + "; V1/V=" + fmt(r2.hapaxRatio * 100, 0) + "%; K=" + fmt(r2.yuleK, 0) + "; H=" + fmt(r2.entropy, 2),
                d, 0.08,
                "Среднее из 4 нормированных различий: индекс Гиро R, доля гапаксов V1/V, индекс Юла K, энтропия H");
    }

    private static MetricResult mfwJaccardMetric(TextDocument d1, TextDocument d2) {
        Map<String, Double> r1 = relFreq1000(countWords(d1.words));
        Map<String, Double> r2 = relFreq1000(countWords(d2.words));
        List<String> topCombined = topCombined(r1, r2, 50);
        Set<String> t1 = new HashSet<String>(topKeys(r1, 50));
        Set<String> t2 = new HashSet<String>(topKeys(r2, 50));
        Set<String> uni = new HashSet<String>(topCombined); uni.retainAll(union(t1, t2));
        Set<String> inter = new HashSet<String>(topCombined); inter.retainAll(t1); inter.retainAll(t2);
        double jacc = uni.isEmpty() ? 1.0 : (double) inter.size() / uni.size();
        return new MetricResult("M10", "Пересечение частотных слов",
                t1.size() + " слов",
                t2.size() + " слов",
                1.0 - jacc, 0.04,
                "Жаккар по топ-50 частотным словам каждого текста (в подпространстве объединённого профиля)");
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> u = new HashSet<String>(a); u.addAll(b); return u;
    }

    private static MetricResult typographyMetric(TextDocument d1, TextDocument d2) {
        double[] v1 = typoVector(d1), v2 = typoVector(d2);
        double d = 1.0 - cosineArr(v1, v2);
        return new MetricResult("M11", "Типографические привычки",
                fmtVectorSummary(v1),
                fmtVectorSummary(v2),
                d, 0.06,
                "Пробелы перед знаками, стили кавычек/тире/многоточий, CAPS, эмодзи (на 1000 букв)");
    }

    private static final String[] PUNCT_CHARS = { ",", ".", "!", "?", ";", ":", "«", "»", "\"", "'", "(", ")", "—", "–", "…", "-" };

    private static double[] punctVector(TextDocument doc) {
        double[] v = new double[PUNCT_CHARS.length];
        String t = doc.text;
        int i = 0;
        while (i < t.length()) {
            int cp = t.codePointAt(i);
            i += Character.charCount(cp);
            String ch = new String(Character.toChars(cp));
            for (int k = 0; k < PUNCT_CHARS.length; k++) {
                if (PUNCT_CHARS[k].equals(ch)) { v[k] += 1; break; }
            }
        }
        double norm = Math.max(1, doc.letterCount) / 1000.0;
        for (int k = 0; k < v.length; k++) v[k] /= norm;
        return v;
    }

    private static double[] typoVector(TextDocument doc) {
        String t = doc.text;
        double norm = Math.max(1, doc.letterCount) / 1000.0;
        double[] v = new double[12];
        int spaceBeforePunct = 0;
        int doubleSpace = 0;
        int ell = 0;
        int emDash = 0, hyphenDash = 0, enDash = 0, straightQ = 0, guillemets = 0, curlyQ = 0, aposInWord = 0, emoji = 0;

        int prevCp = -1;
        int i = 0;
        while (i < t.length()) {
            int cp = t.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == ' ' && prevCp == ' ') doubleSpace++;
            boolean prevWs = prevCp == ' ' || prevCp == '\t' || prevCp == '\n' || prevCp == -1;
            if ((cp == ',' || cp == '.' || cp == '!' || cp == '?' || cp == ';' || cp == ':') && prevWs) spaceBeforePunct++;
            if (cp == '…') ell++;
            if (cp == '—') emDash++;
            if (cp == '–') enDash++;
            if (cp == '"') straightQ++;
            if (cp == '«' || cp == '»') guillemets++;
            if (cp == '“' || cp == '”' || cp == '„') curlyQ++;
            if (cp == ' ' && prevCp == '-') hyphenDash++;
            if (cp == '\'' && Character.isLetter(prevCp)) {
                if (i < t.length() && Character.isLetter(t.codePointAt(i))) aposInWord++;
            }
            if (cp >= 0x1F000 || (cp >= 0x2600 && cp <= 0x27BF) || (cp >= 0x2190 && cp <= 0x21FF) || cp == 0x2764) emoji++;
            prevCp = cp;
        }
        int dots3 = 0;
        for (int k = 0; k < t.length(); ) {
            int cp = t.codePointAt(k); k += Character.charCount(cp);
            if (cp == '.') {
                int run = 1;
                while (k < t.length() && t.codePointAt(k) == '.') { k++; run++; }
                if (run >= 3) dots3++;
            }
        }

        int caps = 0;
        StringBuilder word = new StringBuilder();
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            if (Character.isLetter(c)) word.append(c);
            else {
                if (word.length() >= 3) {
                    boolean allUpper = true, anyUpper = false;
                    for (int m = 0; m < word.length(); m++) {
                        char w = word.charAt(m);
                        if (Character.isUpperCase(w)) anyUpper = true; else allUpper = false;
                    }
                    if (allUpper && anyUpper) caps++;
                }
                word.setLength(0);
            }
        }

        v[0] = spaceBeforePunct / norm;
        v[1] = doubleSpace / norm;
        v[2] = dots3 / norm;
        v[3] = ell / norm;
        v[4] = emDash / norm;
        v[5] = hyphenDash / norm;
        v[6] = enDash / norm;
        v[7] = straightQ / norm;
        v[8] = guillemets / norm;
        v[9] = curlyQ / norm;
        v[10] = (aposInWord + 0.0) / norm;
        v[11] = (emoji + caps * 3.0) / norm;
        return v;
    }

    private static String fmtVectorSummary(double[] v) {
        int best = 0;
        for (int k = 1; k < v.length; k++) if (v[k] > v[best]) best = k;
        String[] names = { "пробел-перед-зн.", "двойной пробел", "...", "…", "—", " - ", "–", "\"", "«»", "“”", "'", "эмодзи/CAPS" };
        return names[best] + "=" + fmt(v[best], 2);
    }

    public static class Richness {
        public final double guiraud;
        public final double hapaxRatio;
        public final double yuleK;
        public final double entropy;
        Richness(double g, double h, double k, double e) { guiraud = g; hapaxRatio = h; yuleK = k; entropy = e; }
    }

    public static Richness richness(List<String> words) {
        int N = words.size();
        if (N == 0) return new Richness(0, 0, 0, 0);
        Map<String, Integer> c = countWords(words);
        int V = c.size();
        int V1 = 0;
        double sumI2Vi = 0;
        double H = 0;
        for (Map.Entry<String, Integer> e : c.entrySet()) {
            int i = e.getValue();
            if (i == 1) V1++;
            sumI2Vi += (double) i * i;
            H -= ((double) i / N) * Math.log((double) i / N);
        }
        double K = N > 0 ? 10000.0 * (sumI2Vi - N) / ((double) N * N) : 0;
        return new Richness(V / Math.sqrt((double) N), V > 0 ? (double) V1 / V : 0, K, H);
    }

    public static Map<String, Integer> countWords(List<String> words) {
        Map<String, Integer> c = new HashMap<String, Integer>();
        for (String w : words) {
            Integer old = c.get(w);
            c.put(w, old == null ? 1 : old + 1);
        }
        return c;
    }

    public static Map<String, Integer> ngramCounts(String s, int n) {
        Map<String, Integer> c = new HashMap<String, Integer>();
        if (s.length() < n) return c;
        for (int i = 0; i + n <= s.length(); i++) {
            String g = s.substring(i, i + n);
            Integer old = c.get(g);
            c.put(g, old == null ? 1 : old + 1);
        }
        return c;
    }

    public static Map<String, Double> relFreq(Map<String, Integer> counts) {
        Map<String, Double> p = new HashMap<String, Double>();
        long total = 0;
        for (Integer v : counts.values()) total += v;
        if (total == 0) return p;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            p.put(e.getKey(), e.getValue() / (double) total);
        }
        return p;
    }

    public static Map<String, Double> relFreq1000(Map<String, Integer> counts) {
        Map<String, Double> p = new HashMap<String, Double>();
        long total = 0;
        for (Integer v : counts.values()) total += v;
        if (total == 0) return p;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            p.put(e.getKey(), e.getValue() * 1000.0 / total);
        }
        return p;
    }

    public static List<String> topKeys(Map<String, Double> freqs, int n) {
        List<Map.Entry<String, Double>> list = new ArrayList<Map.Entry<String, Double>>(freqs.entrySet());
        java.util.Collections.sort(list, new java.util.Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                int c = Double.compare(b.getValue(), a.getValue());
                return c != 0 ? c : a.getKey().compareTo(b.getKey());
            }
        });
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < list.size() && i < n; i++) out.add(list.get(i).getKey());
        return out;
    }

    public static List<String> topCombined(Map<String, Double> p1, Map<String, Double> p2, int n) {
        Map<String, Double> combined = new HashMap<String, Double>();
        for (Map.Entry<String, Double> e : p1.entrySet()) combined.put(e.getKey(), e.getValue() / 2.0);
        for (Map.Entry<String, Double> e : p2.entrySet()) {
            Double old = combined.get(e.getKey());
            combined.put(e.getKey(), (old == null ? 0.0 : old) + e.getValue() / 2.0);
        }
        return topKeys(combined, n);
    }

    public static Map<String, Double> restrictAndRenorm(Map<String, Double> p, List<String> keys) {
        double sum = 0;
        for (String k : keys) { Double v = p.get(k); if (v != null) sum += v; }
        Map<String, Double> out = new HashMap<String, Double>();
        if (sum <= 0) return out;
        for (String k : keys) { Double v = p.get(k); if (v != null) out.put(k, v / sum); }
        return out;
    }

    public static double cosineSubspace(Map<String, Double> a, Map<String, Double> b, List<String> keys) {
        double dot = 0, na = 0, nb = 0;
        for (String k : keys) {
            double x = a.containsKey(k) ? a.get(k) : 0.0;
            double y = b.containsKey(k) ? b.get(k) : 0.0;
            dot += x * y; na += x * x; nb += y * y;
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        double dot = 0, na = 0, nb = 0;
        for (Double v : a.values()) na += v * v;
        for (Double v : b.values()) nb += v * v;
        Map<String, Double> small = a.size() <= b.size() ? a : b;
        Map<String, Double> big = small == a ? b : a;
        for (Map.Entry<String, Double> e : small.entrySet()) {
            Double o = big.get(e.getKey());
            if (o != null) dot += e.getValue() * o;
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static double cosArr(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        if (na == 0 && nb == 0) return 1.0;
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static double cosineArr(double[] a, double[] b) { return cosArr(a, b); }

    public static double jsd(Map<String, Double> p, Map<String, Double> q) {
        if (p.isEmpty() && q.isEmpty()) return 0;
        double kl1 = 0, kl2 = 0;
        for (Map.Entry<String, Double> e : p.entrySet()) {
            double pi = e.getValue();
            double qi = q.containsKey(e.getKey()) ? q.get(e.getKey()) : 0.0;
            double mi = 0.5 * (pi + qi);
            if (pi > 0) kl1 += pi * Math.log(pi / mi);
            if (qi > 0) kl2 += qi * Math.log(qi / mi);
        }
        for (Map.Entry<String, Double> e : q.entrySet()) {
            if (p.containsKey(e.getKey())) continue;
            double qi = e.getValue();
            if (qi > 0) kl2 += qi * Math.log(qi / (0.5 * qi));
        }
        double jsd = 0.5 * kl1 + 0.5 * kl2;
        return Math.min(1.0, jsd / Math.log(2));
    }

    public static double shannon(Map<String, Double> p) {
        double h = 0;
        for (Double v : p.values()) if (v > 0) h -= v * Math.log(v);
        return h;
    }

    public static double[] histogram(List<Integer> values, int maxBin) {
        double[] h = new double[maxBin];
        for (Integer v : values) {
            int idx = Math.min(Math.max(v, 1), maxBin) - 1;
            h[idx] += 1;
        }
        double total = values.size();
        if (total > 0) for (int i = 0; i < h.length; i++) h[i] /= total;
        return h;
    }

    public static double jsdHist(double[] p, double[] q) {
        double kl1 = 0, kl2 = 0;
        for (int i = 0; i < p.length; i++) {
            double pi = p[i], qi = q[i];
            double mi = 0.5 * (pi + qi);
            if (pi > 0) kl1 += pi * Math.log(pi / mi);
            if (qi > 0) kl2 += qi * Math.log(qi / mi);
        }
        return Math.min(1.0, (0.5 * kl1 + 0.5 * kl2) / Math.log(2));
    }

    public static double ksStatistic(double[] p, double[] q) {
        double c1 = 0, c2 = 0, max = 0;
        for (int i = 0; i < p.length; i++) {
            c1 += p[i]; c2 += q[i];
            max = Math.max(max, Math.abs(c1 - c2));
        }
        return max;
    }

    public static List<Integer> wordLengths(List<String> words) {
        List<Integer> out = new ArrayList<Integer>(words.size());
        for (String w : words) out.add(w.length());
        return out;
    }

    public static class Stats {
        public final double mean, sd;
        Stats(double m, double s) { mean = m; sd = s; }
    }

    public static Stats stats(List<Integer> values) {
        if (values.isEmpty()) return new Stats(0, 0);
        double sum = 0;
        for (Integer v : values) sum += v;
        double mean = sum / values.size();
        double var = 0;
        for (Integer v : values) var += (v - mean) * (v - mean);
        var = values.size() > 1 ? var / (values.size() - 1) : 0;
        return new Stats(mean, Math.sqrt(var));
    }

    public static double relDiff(double a, double b) {
        if (a + b <= 0) return 0;
        return Math.abs(a - b) / (a + b);
    }

    public static double logRelDiff(double a, double b) {
        double la = Math.log(Math.max(a, 1e-9)), lb = Math.log(Math.max(b, 1e-9));
        if (la + lb == 0 && a + b > 0) return 0;
        return Math.abs(la - lb) / Math.max(1e-9, la + lb);
    }

    public static String fmt(double v, int digits) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "";
        return String.format(java.util.Locale.US, "%." + digits + "f", v);
    }
}
