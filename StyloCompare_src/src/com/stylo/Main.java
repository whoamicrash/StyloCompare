package com.stylo;

import com.stylo.gui.MainWindow;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            launchGui();
            return;
        }
        String a0 = args[0];
        if ("--gui".equals(a0)) { launchGui(); return; }
        if ("--help".equals(a0) || "-h".equals(a0) || "/?".equals(a0)) { printHelp(); return; }
        if ("--selftest".equals(a0)) { System.exit(SelfTest.run()); return; }
        if ("--cli".equals(a0)) {
            String[] rest = new String[args.length - 1];
            System.arraycopy(args, 1, rest, 0, rest.length);
            System.exit(CliMain.run(rest));
            return;
        }
        if (args.length >= 2) { System.exit(CliMain.run(args)); return; }
        printHelp();
    }

    private static void launchGui() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Графический режим недоступен (headless). Используйте: java -jar StyloCompare.jar --cli file1 file2");
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { new MainWindow(); }
        });
    }

    private static void printHelp() {
        System.out.println("StyloCompare: сравнение двух текстов, один автор или разные");
        System.out.println();
        System.out.println("Использование:");
        System.out.println("  java -jar StyloCompare.jar                  графический интерфейс");
        System.out.println("  java -jar StyloCompare.jar file1 file2      консольный анализ двух файлов");
        System.out.println("  java -jar StyloCompare.jar --cli f1 f2 [--out report.txt] [--force]");
        System.out.println("  java -jar StyloCompare.jar --selftest       встроенный самотест");
        System.out.println();
        System.out.println("Опции CLI:");
        System.out.println("  --out <файл>  сохранить отчёт в файл (UTF-8 с BOM)");
        System.out.println("  --force       не спрашивать подтверждение при коротких текстах (<500 слов)");
    }
}
