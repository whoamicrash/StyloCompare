package com.stylo.core;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TextDocument {

    public static class NotTextException extends java.io.IOException {
        public NotTextException(String message) { super(message); }
    }

    public final String title;
    public final String raw;
    public final String text;
    public final List<String> words;
    public final List<Integer> sentenceLengths;
    public final int wordCount;
    public final int letterCount;
    public final int sentenceCount;
    public final Map<String, Integer> scriptCounts;
    public final String dominantScript;
    public final String charsetUsed;

    private final String lettersLower;

    public static TextDocument fromFile(File f) throws Exception {
        byte[] data = Files.readAllBytes(f.toPath());
        CharsetResult cr = detectCharset(data);
        return new TextDocument(f.getName(), cr.text, cr.charset.name(), f.length());
    }

    public static TextDocument fromString(String title, String content) {
        return new TextDocument(title, Normalizer.normalize(content, Normalizer.Form.NFC), "строка/внутренняя", content.length());
    }

    public TextDocument(String title, String rawContent, String charset, long fileBytes) {
        this.title = title;
        this.raw = rawContent;
        this.charsetUsed = charset;
        String t = Normalizer.normalize(rawContent, Normalizer.Form.NFC);
        t = t.replace("\r\n", "\n").replace('\r', '\n').trim();
        this.text = t;
        this.words = tokenize(t.toLowerCase());
        this.wordCount = words.size();
        this.lettersLower = buildLettersLower(t);
        this.letterCount = countLetters(t);
        this.sentenceLengths = splitSentences(t);
        this.sentenceCount = sentenceLengths.size();
        this.scriptCounts = countScripts(t);
        this.dominantScript = findDominantScript();
    }

    public String lettersLower() {
        return lettersLower;
    }

    public boolean isCjkDominant() {
        String ds = dominantScript;
        return "HAN".equals(ds) || "HIRAGANA".equals(ds) || "KATAKANA".equals(ds);
    }

    public String scriptDescription() {
        String ds = dominantScript;
        if (ds == null) return "не определена";
        if ("CYRILLIC".equals(ds)) return "кириллица (русский/украинский/болгарский и др.)";
        if ("LATIN".equals(ds)) return "латиница (английский/немецкий/французский и др.)";
        if ("HAN".equals(ds)) return "иероглифика (китайский/японский)";
        if ("HIRAGANA".equals(ds) || "KATAKANA".equals(ds)) return "канa (японский)";
        if ("HANGUL".equals(ds)) return "хангыль (корейский)";
        if ("ARABIC".equals(ds)) return "арабица (арабский/персидский/урду)";
        if ("HEBREW".equals(ds)) return "иврит";
        if ("GREEK".equals(ds)) return "греческий";
        if ("DEVANAGARI".equals(ds)) return "деванагари (хинди/маратхи)";
        if ("THAI".equals(ds)) return "тайский";
        String low = ds.toLowerCase();
        return low.substring(0, 1).toUpperCase() + low.substring(1);
    }

    private static class CharsetResult {
        final Charset charset;
        final String text;
        CharsetResult(Charset charset, String text) { this.charset = charset; this.text = text; }
    }

    private static CharsetResult detectCharset(byte[] data) throws NotTextException {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            return new CharsetResult(StandardCharsets.UTF_8, new String(data, 3, data.length - 3, StandardCharsets.UTF_8));
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            return new CharsetResult(StandardCharsets.UTF_16LE, new String(data, 2, data.length - 2, StandardCharsets.UTF_16LE));
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            return new CharsetResult(StandardCharsets.UTF_16BE, new String(data, 2, data.length - 2, StandardCharsets.UTF_16BE));
        }
        rejectIfBinary(data);
        try {
            CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            String s = dec.decode(java.nio.ByteBuffer.wrap(data)).toString();
            return new CharsetResult(StandardCharsets.UTF_8, s);
        } catch (Exception ignore) { }

        String w1251 = new String(data, Charset.forName("windows-1251"));
        String koi8 = new String(data, Charset.forName("KOI8-R"));
        int cyr1251 = countCyrillic(w1251);
        int cyrKoi = countCyrillic(koi8);
        if (cyr1251 > 0 && cyr1251 >= cyrKoi) return new CharsetResult(Charset.forName("windows-1251"), w1251);
        if (cyrKoi > 0) return new CharsetResult(Charset.forName("KOI8-R"), koi8);
        return new CharsetResult(StandardCharsets.ISO_8859_1, new String(data, StandardCharsets.ISO_8859_1));
    }

    private static void rejectIfBinary(byte[] d) throws NotTextException {
        String fmt = binarySignature(d);
        if (fmt != null) throw new NotTextException("обнаружен бинарный формат: " + fmt);
        int n = Math.min(d.length, 4096);
        if (n == 0) return;
        int nul = 0, ctrl = 0;
        for (int i = 0; i < n; i++) {
            int b = d[i] & 0xFF;
            if (b == 0) nul++;
            else if (b < 32 && b != 9 && b != 10 && b != 13) ctrl++;
        }
        if (nul > 0 && nul * 50 >= n) throw new NotTextException("файл содержит бинарные данные (нулевые байты)");
        if (ctrl * 20 >= n) throw new NotTextException("файл содержит бинарные данные (управляющие символы)");
    }

    private static String binarySignature(byte[] b) {
        if (b.length >= 8 && b[0] == (byte) 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "PNG-изображение";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "JPEG-изображение";
        if (b.length >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "GIF-изображение";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F') {
            if (b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "WebP-изображение";
            if (b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E') return "WAV-аудио";
        }
        if (b.length >= 18 && b[0] == 'B' && b[1] == 'M') {
            long dib = (b[14] & 0xFFL) | (b[15] & 0xFFL) << 8 | (b[16] & 0xFFL) << 16 | (b[17] & 0xFFL) << 24;
            if (dib == 12 || dib == 40 || dib == 52 || dib == 56 || dib == 64 || dib == 108 || dib == 124) return "BMP-изображение";
        }
        if (b.length >= 4 && ((b[0] == 'I' && b[1] == 'I' && b[2] == 0x2A && b[3] == 0)
                || (b[0] == 'M' && b[1] == 'M' && b[2] == 0 && b[3] == 0x2A))) return "TIFF-изображение";
        if (b.length >= 4 && b[0] == 0 && b[1] == 0 && b[2] == 1 && b[3] == 0) return "ICO-иконка";
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return "PDF-документ";
        if (b.length >= 4 && b[0] == 'P' && b[1] == 'K' && (b[2] == 3 || b[2] == 5 || b[2] == 7) && (b[3] == 4 || b[3] == 6 || b[3] == 8)) return "ZIP-архив (docx/xlsx/jar)";
        if (b.length >= 6 && b[0] == 'R' && b[1] == 'a' && b[2] == 'r' && b[3] == '!' && b[4] == 0x1A && b[5] == 7) return "RAR-архив";
        if (b.length >= 6 && b[0] == '7' && b[1] == 'z' && b[2] == (byte) 0xBC && b[3] == (byte) 0xAF && b[4] == 0x27 && b[5] == 0x1C) return "7Z-архив";
        if (b.length >= 2 && (b[0] & 0xFF) == 0x1F && (b[1] & 0xFF) == 0x8B) return "GZIP-архив";
        if (b.length >= 4 && b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S') return "OGG-медиа";
        if (b.length >= 4 && b[0] == 'f' && b[1] == 'L' && b[2] == 'a' && b[3] == 'C') return "FLAC-аудио";
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') return "MP4/MOV-медиа";
        if (b.length >= 3 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') return "MP3-аудио";
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && ((b[1] & 0xFF) == 0xFB || (b[1] & 0xFF) == 0xF3 || (b[1] & 0xFF) == 0xF2)) return "MP3-аудио";
        if (b.length >= 4 && b[0] == 0x7F && b[1] == 'E' && b[2] == 'L' && b[3] == 'F') return "исполняемый файл (ELF)";
        if (b.length >= 64 && b[0] == 'M' && b[1] == 'Z') return "исполняемый файл (Windows PE)";
        if (b.length >= 8 && (b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF && (b[2] & 0xFF) == 0x11 && (b[3] & 0xFF) == 0xE0) return "документ MS Office (.doc/.xls)";
        if (b.length >= 16 && b[0] == 'S' && b[1] == 'Q' && b[2] == 'L' && b[3] == 'i') return "SQLite-база данных";
        return null;
    }

    private static int countCyrillic(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0x0400 && cp <= 0x04FF) n++;
        }
        return n;
    }

    private static boolean isMark(int cp) {
        int t = Character.getType(cp);
        return t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK || t == Character.ENCLOSING_MARK;
    }

    private static boolean isCjkScript(Character.UnicodeScript sc) {
        return sc == Character.UnicodeScript.HAN
                || sc == Character.UnicodeScript.HIRAGANA
                || sc == Character.UnicodeScript.KATAKANA;
    }

    public static List<String> tokenize(String lowerText) {
        List<String> out = new ArrayList<String>();
        StringBuilder run = new StringBuilder();
        int i = 0;
        int len = lowerText.length();
        while (i < len) {
            int cp = lowerText.codePointAt(i);
            i += Character.charCount(cp);
            boolean wordPart = Character.isLetterOrDigit(cp) || isMark(cp);
            if (wordPart) {
                Character.UnicodeScript sc;
                try { sc = Character.UnicodeScript.of(cp); } catch (IllegalArgumentException e) { sc = Character.UnicodeScript.UNKNOWN; }
                if (Character.isLetter(cp) && isCjkScript(sc)) {
                    if (run.length() > 0) { out.add(run.toString()); run.setLength(0); }
                    out.add(new String(Character.toChars(cp)));
                } else {
                    run.appendCodePoint(cp);
                }
            } else {
                if (run.length() > 0) { out.add(run.toString()); run.setLength(0); }
            }
        }
        if (run.length() > 0) out.add(run.toString());
        return out;
    }

    private static String buildLettersLower(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetter(cp)) sb.appendCodePoint(Character.toLowerCase(cp));
        }
        return sb.toString();
    }

    private static int countLetters(String text) {
        int n = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetter(cp)) n++;
        }
        return n;
    }

    private static boolean isSentenceEnder(int cp) {
        return cp == '.' || cp == '!' || cp == '?' || cp == '…'
                || cp == '。' || cp == '！' || cp == '？' || cp == '؛' || cp == '۔'
                || cp == '॥' || cp == '|' ;
    }

    private static boolean isClosingQuote(int cp) {
        return cp == '"' || cp == '\'' || cp == '»' || cp == '“' || cp == '’'
                || cp == ')' || cp == ']' || cp == '}';
    }

    private static List<Integer> splitSentences(String text) {
        List<Integer> out = new ArrayList<Integer>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        int len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\n') {
                flushSentence(cur, out);
                continue;
            }
            cur.appendCodePoint(cp);
            if (isSentenceEnder(cp)) {
                while (i < len) {
                    int nx = text.codePointAt(i);
                    if (isSentenceEnder(nx) || isClosingQuote(nx) || nx == ' ' || nx == '\t') {
                        if (!Character.isWhitespace(nx)) cur.appendCodePoint(nx);
                        i += Character.charCount(nx);
                        if (nx == '\n') { break; }
                    } else {
                        break;
                    }
                }
                flushSentence(cur, out);
            }
        }
        flushSentence(cur, out);
        if (out.isEmpty() && !text.isEmpty()) {
            List<String> w = tokenize(text.toLowerCase());
            for (int k = 0; k < w.size(); k += 12) {
                out.add(Math.min(12, w.size() - k));
            }
        }
        return out;
    }

    private static void flushSentence(StringBuilder cur, List<Integer> out) {
        String s = cur.toString().trim();
        cur.setLength(0);
        if (s.isEmpty()) return;
        int wc = tokenize(s.toLowerCase()).size();
        if (wc > 0) out.add(wc);
    }

    private static Map<String, Integer> countScripts(String text) {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) continue;
            Character.UnicodeScript sc;
            try { sc = Character.UnicodeScript.of(cp); } catch (IllegalArgumentException e) { continue; }
            if (sc == Character.UnicodeScript.UNKNOWN) continue;
            String name = sc.name();
            Integer old = counts.get(name);
            counts.put(name, old == null ? 1 : old + 1);
        }
        return counts;
    }

    private String findDominantScript() {
        String best = null;
        int bestN = -1;
        for (Map.Entry<String, Integer> e : scriptCounts.entrySet()) {
            if (e.getValue() > bestN) { bestN = e.getValue(); best = e.getKey(); }
        }
        return best;
    }

    public static boolean scriptsFundamentallyDiffer(TextDocument a, TextDocument b) {
        String sa = a.dominantScript, sb = b.dominantScript;
        if (sa == null || sb == null) return false;
        boolean aCjk = a.isCjkDominant();
        boolean bCjk = b.isCjkDominant();
        if (aCjk != bCjk) return true;
        return !sa.equals(sb);
    }
}
