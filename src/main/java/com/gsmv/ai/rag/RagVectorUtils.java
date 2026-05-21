package com.gsmv.ai.rag;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

public final class RagVectorUtils {
    private RagVectorUtils() {
    }

    public static double cosine(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0d;
        }
        int size = Math.min(left.size(), right.size());
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int i = 0; i < size; i++) {
            double a = left.get(i) == null ? 0.0d : left.get(i);
            double b = right.get(i) == null ? 0.0d : right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm))));
    }

    public static double keywordScore(String query, String text) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(text)) {
            return 0.0d;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        String normalizedText = text.toLowerCase(Locale.ROOT);
        if (normalizedText.contains(normalizedQuery)) {
            return 1.0d;
        }
        Set<String> terms = extractTerms(normalizedQuery);
        if (terms.isEmpty()) {
            return 0.0d;
        }
        long matched = terms.stream().filter(normalizedText::contains).count();
        return Math.min(1.0d, matched / (double) terms.size());
    }

    private static Set<String> extractTerms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        for (String item : query.split("[\\s,，。；;、:：?？!！()（）\\[\\]【】]+")) {
            if (item.length() >= 2) {
                terms.add(item);
            }
        }
        if (terms.isEmpty() && query.length() >= 2) {
            for (int i = 0; i < query.length() - 1 && terms.size() < 12; i += 2) {
                terms.add(query.substring(i, Math.min(i + 3, query.length())));
            }
        }
        return terms;
    }
}
