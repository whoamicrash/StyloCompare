package com.stylo;

import com.stylo.core.AnalysisResult;
import com.stylo.core.ReportBuilder;
import com.stylo.core.StylometryEngine;
import com.stylo.core.TextDocument;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class CliMain {

    private CliMain() {}

    public static int run(String[] args) {
        String f1 = null, f2 = null, out = null;
        boolean force = false;
        java.util.List<String> positional = new java.util.ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--out".equals(a) || "-o".equals(a)) {
                if (i + 1 >= args.length) { err("После --out нужно указать имя файла"); return 2; }
                out = args[++i];
            } else if ("--force".equals(a)) {
                force = true;
            } else if ("--cli".equals(a)) {
            } else {
                positional.add(a);
            }
        }
        if (positional.size() < 2) { err("Нужны два файла: --cli <файл1> <файл2>"); return 2; }
        f1 = positional.get(0);
        f2 = positional.get(1);

        PrintStream outP = System.out;
        try { outP = new PrintStream(System.out, true, "UTF-8"); } catch (Exception ignore) {}

        TextDocument d1, d2;
        try {
            d1 = TextDocument.fromFile(new File(f1));
        } catch (TextDocument.NotTextException e) { err("Файл 1 не текст (" + e.getMessage() + ")"); return 2; }
        catch (Exception e) { err("Не удалось прочитать файл 1: " + e.getMessage()); return 2; }
        try {
            d2 = TextDocument.fromFile(new File(f2));
        } catch (TextDocument.NotTextException e) { err("Файл 2 не текст (" + e.getMessage() + ")"); return 2; }
        catch (Exception e) { err("Не удалось прочитать файл 2: " + e.getMessage()); return 2; }

        boolean short1 = d1.wordCount < StylometryEngine.SHORT_WORDS_THRESHOLD;
        boolean short2 = d2.wordCount < StylometryEngine.SHORT_WORDS_THRESHOLD;
        if ((short1 || short2) && !force) {
            outP.println("ВНИМАНИЕ: текст короче 500 слов, оценка будет грубой.");
            if (short1) outP.println("  Файл 1: " + d1.wordCount + " слов (< 500)");
            if (short2) outP.println("  Файл 2: " + d2.wordCount + " слов (< 500)");
            java.io.Console cons = System.console();
            if (cons != null) {
                cons.printf("Продолжить анализ? [y/N]: ");
                String ans = cons.readLine();
                if (ans == null || !(ans.trim().equalsIgnoreCase("y") || ans.trim().equalsIgnoreCase("д") || ans.trim().equalsIgnoreCase("yes"))) {
                    outP.println("Отменено пользователем.");
                    return 3;
                }
            } else {
                outP.println("Консоль недоступна, анализ продолжен. Добавьте --force, чтобы убрать этот вопрос.");
            }
        }

        AnalysisResult r = StylometryEngine.analyze(d1, d2);
        String report = ReportBuilder.build(r);

        outP.println(report);
        if (out != null) {
            try {
                writeUtf8Bom(new File(out), report);
                outP.println("Отчёт сохранён: " + out);
            } catch (IOException e) {
                err("Не удалось сохранить отчёт: " + e.getMessage());
                return 2;
            }
        }
        return 0;
    }

    public static void writeUtf8Bom(File file, String content) throws IOException {
        OutputStream os = new BufferedOutputStream(new FileOutputStream(file));
        try {
            os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            os.write(content.getBytes(StandardCharsets.UTF_8));
        } finally {
            os.close();
        }
    }

    private static void err(String msg) {
        System.out.println("ОШИБКА: " + msg);
    }
}
