package com.gsmv.ai.rag;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RagTextChunker {
    static final int DEFAULT_CHUNK_SIZE = 600;
    static final int DEFAULT_OVERLAP = 100;

    public List<ChunkDraft> chunk(String title, String text) {
        String normalized = normalize(text);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        List<ChunkDraft> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + DEFAULT_CHUNK_SIZE, normalized.length());
            if (end < normalized.length()) {
                int sentenceBreak = Math.max(
                        Math.max(normalized.lastIndexOf('。', end), normalized.lastIndexOf('；', end)),
                        Math.max(normalized.lastIndexOf('\n', end), normalized.lastIndexOf('.', end))
                );
                if (sentenceBreak > start + 240) {
                    end = sentenceBreak + 1;
                }
            }
            String content = normalized.substring(start, end).trim();
            if (StringUtils.hasText(content)) {
                chunks.add(new ChunkDraft(index++, title, content, summarize(content), content.length()));
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - DEFAULT_OVERLAP, start + 1);
        }
        return chunks;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String summarize(String content) {
        String compact = content.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return compact.length() <= 180 ? compact : compact.substring(0, 180) + "...";
    }

    public record ChunkDraft(
            int index,
            String title,
            String content,
            String summary,
            int characterCount
    ) {
    }
}
