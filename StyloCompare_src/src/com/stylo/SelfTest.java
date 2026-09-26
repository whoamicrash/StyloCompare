package com.stylo;

import com.stylo.core.AnalysisResult;
import com.stylo.core.StylometryEngine;
import com.stylo.core.TextDocument;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SelfTest {

    private SelfTest() {}

    public static void main(String[] args) { System.exit(run()); }

    public static int run() {
        PrintStream o = System.out;
        try { o = new PrintStream(System.out, true, "UTF-8"); } catch (Exception ignore) {}

        o.println("=== StyloCompare SelfTest ===");
        o.println("Генерация синтетических авторов с различными стилевыми профилями...");

        String[] dictShort = {"и", "в", "не", "на", "я", "он", "с", "как", "а", "то", "все", "так",
                "но", "да", "бы", "же", "ли", "за", "из", "о", "у", "по", "до", "мы", "ты", "ну"};
        String[] dictMid = {"время", "человек", "дом", "день", "жизнь", "слово", "дело", "город",
                "друг", "мысль", "путь", "вода", "свет", "ночь", "рука", "глаз", "мир", "книга"};
        String[] dictLong = {"необходимость", "действительность", "предположительно", "интересующийся",
                "удовлетворение", "обстоятельство", "впечатлительность", "сформированный",
                "интеллектуальный", "продолжительный", "университетский", "исследовательский"};

        String[] tailsA = {".", ", ", ".", " и ", ".", ", ", " — ", "."};
        String[] tailsB = {"!", "...", "?", "!", ", ", "!!", "..."};
        String[] tailsC = {".", "; ", ".", ", ", ". ", ": ", "."};

        Author A = new Author("Автор A (длинные предложения, сдержанная пунктуация)",
                16, 5, dictShort, dictMid, dictLong, 6, 1, 3, 0.30, tailsA, "«", "»", "—");
        Author B = new Author("Автор B (короткие эмоциональные фразы, многоточия)",
                6, 2, dictShort, dictShort, dictMid, 14, 8, 1, 0.05, tailsB, "\"", "\"", "-");
        Author C = new Author("Автор C (канцелярит, средние предложения, двоеточия)",
                11, 4, dictMid, dictMid, dictLong, 2, 0, 6, 0.15, tailsC, "„", "“", "–");

        int pass = 0, fail = 0;
        List<String> failures = new ArrayList<String>();

        String same = generate(A, 40, 12345L);
        TextDocument t1 = TextDocument.fromString("same1.txt", same);
        TextDocument t2 = TextDocument.fromString("same2.txt", same);
        AnalysisResult r = StylometryEngine.analyze(t1, t2);
        o.println(String.format("[ПРОВЕРКА 1] идентичные тексты: P=%.4f (ожидается >= 0.90)", r.probability));
        if (r.probability >= 0.90) pass++; else { fail++; failures.add("identical: P=" + r.probability); }

        o.println();
        o.println(String.format("%-6s %-9s | %-9s %-9s %-9s | %s", "сид", "P(A1,A2)", "P(A1,B1)", "P(A1,C1)", "P(B1,C1)", "статус"));
        for (int seed = 1; seed <= 15; seed++) {
            TextDocument a1 = doc(A, 45, seed * 100L + 1);
            TextDocument a2 = doc(A, 45, seed * 100L + 2);
            TextDocument b1 = doc(B, 45, seed * 100L + 3);
            TextDocument c1 = doc(C, 45, seed * 100L + 4);
            double pAA = StylometryEngine.analyze(a1, a2).probability;
            double pAB = StylometryEngine.analyze(a1, b1).probability;
            double pAC = StylometryEngine.analyze(a1, c1).probability;
            double pBC = StylometryEngine.analyze(b1, c1).probability;
            boolean ok = pAA > pAB && pAA > pAC && pAA > pBC;
            if (ok) pass++; else { fail++; failures.add(String.format("seed=%d pAA=%.3f pAB=%.3f pAC=%.3f pBC=%.3f", seed, pAA, pAB, pAC, pBC)); }
            o.println(String.format("%-6d %-9.4f | %-9.4f %-9.4f %-9.4f | %s",
                    seed, pAA, pAB, pAC, pBC, ok ? "OK" : "FAIL"));
        }

        o.println();
        o.println("Итог: пройдено " + pass + ", провалено " + fail);
        if (fail > 0) {
            o.println("Провалы:");
            for (String f : failures) o.println("  - " + f);
            return 1;
        }
        o.println("ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ");
        return 0;
    }

    private static TextDocument doc(Author a, int sentences, long seed) {
        return TextDocument.fromString(a.name + "_" + seed + ".txt", generate(a, sentences, seed));
    }

    private static String generate(Author a, int sentences, long seed) {
        Random rnd = new Random(seed);
        StringBuilder sb = new StringBuilder();
        int sent = 0;
        while (sent < sentences) {
            int wordsInSentence = Math.max(1, (int) Math.round(a.meanSentence + rnd.nextGaussian() * a.sdSentence));
            for (int w = 0; w < wordsInSentence; w++) {
                String word;
                double p = rnd.nextDouble();
                if (p < a.pShort) word = a.shortDict[rnd.nextInt(a.shortDict.length)];
                else if (p < a.pShort + a.pMid) word = a.midDict[rnd.nextInt(a.midDict.length)];
                else word = a.longDict[rnd.nextInt(a.longDict.length)];
                if (w > 0) {
                    double cp = rnd.nextDouble();
                    if (cp < a.commaRate) sb.append(", ");
                }
                sb.append(word);
                if (rnd.nextDouble() < 0.02) sb.append(a.quoteOpen).append(word).append(a.quoteClose);
                sb.append(' ');
            }
            sb.append(a.tails[rnd.nextInt(a.tails.length)]);
            sb.append(' ');
            if (rnd.nextDouble() < 0.10) sb.append('\n');
            sent++;
        }
        return sb.toString().trim();
    }

    private static class Author {
        final String name;
        final double meanSentence, sdSentence;
        final String[] shortDict, midDict, longDict;
        final double pShort, pMid, commaRate;
        final int exclaimBias, colonBias;
        final String[] tails;
        final String quoteOpen, quoteClose, dash;

        Author(String name, double meanSentence, double sdSentence,
               String[] shortDict, String[] midDict, String[] longDict,
               int exclaimBias, int dotsBias, int colonBias, double commaRate,
               String[] tails, String quoteOpen, String quoteClose, String dash) {
            this.name = name;
            this.meanSentence = meanSentence;
            this.sdSentence = sdSentence;
            this.shortDict = shortDict;
            this.midDict = midDict;
            this.longDict = longDict;
            this.pShort = 0.5;
            this.pMid = 0.45;
            this.commaRate = commaRate;
            this.exclaimBias = exclaimBias;
            this.colonBias = colonBias;
            this.tails = tails;
            this.quoteOpen = quoteOpen;
            this.quoteClose = quoteClose;
            this.dash = dash;
        }
    }
}
