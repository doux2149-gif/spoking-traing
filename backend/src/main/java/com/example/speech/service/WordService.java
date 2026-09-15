package com.example.speech.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.speech.entity.Word;
import com.example.speech.mapper.WordMapper;
import com.example.speech.service.deepseek.DeepSeekClient;
import com.example.speech.service.deepseek.DeepSeekClient.ChatMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 单词管理服务: 手动 CRUD + AI 批量生成 + CSV 导入导出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordService {

    private final WordMapper wordMapper;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    // ---------- 管理端 CRUD ----------

    public IPage<Word> page(String keyword, String category, Integer pageNum, Integer pageSize) {
        Page<Word> page = Page.of(pageNum, pageSize);
        LambdaQueryWrapper<Word> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(Word::getEnglish, keyword)
                    .or().like(Word::getChinese, keyword));
        }
        if (StringUtils.hasText(category)) {
            w.eq(Word::getCategory, category);
        }
        w.orderByDesc(Word::getCreateTime);
        return wordMapper.selectPage(page, w);
    }

    @Transactional
    public Word create(Word word) {
        word.setId(null);
        word.setSource(word.getSource() == null ? "UPLOAD" : word.getSource());
        word.setIsPublished(word.getIsPublished() == null ? 1 : word.getIsPublished());
        wordMapper.insert(word);
        return word;
    }

    @Transactional
    public List<Word> batchCreate(List<Word> words) {
        List<Word> inserted = new ArrayList<>();
        for (Word w : words) {
            if (wordMapper.selectOne(new LambdaQueryWrapper<Word>().eq(Word::getEnglish, w.getEnglish())) != null) {
                continue;
            }
            w.setId(null);
            w.setSource(w.getSource() == null ? "UPLOAD" : w.getSource());
            w.setIsPublished(w.getIsPublished() == null ? 1 : w.getIsPublished());
            wordMapper.insert(w);
            inserted.add(w);
        }
        return inserted;
    }

    @Transactional
    public Word update(Long id, Word word) {
        Word existing = wordMapper.selectById(id);
        if (existing == null) throw new IllegalStateException("单词不存在: " + id);
        word.setId(id);
        word.setSource(existing.getSource());   // source 不允许改
        word.setCreateTime(existing.getCreateTime());
        wordMapper.updateById(word);
        return word;
    }

    @Transactional
    public void delete(Long id) {
        wordMapper.deleteById(id);
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            wordMapper.deleteBatchIds(ids);
        }
    }

    @Transactional
    public Word togglePublish(Long id, int published) {
        Word w = wordMapper.selectById(id);
        if (w == null) throw new IllegalStateException("单词不存在");
        w.setIsPublished(published);
        wordMapper.updateById(w);
        return w;
    }

    // ---------- 用户端浏览(只看已发布) ----------

    public IPage<Word> pagePublished(String keyword, String category, Integer pageNum, Integer pageSize) {
        Page<Word> page = Page.of(pageNum, pageSize);
        LambdaQueryWrapper<Word> w = new LambdaQueryWrapper<>();
        w.eq(Word::getIsPublished, 1);
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(Word::getEnglish, keyword)
                    .or().like(Word::getChinese, keyword));
        }
        if (StringUtils.hasText(category)) {
            w.eq(Word::getCategory, category);
        }
        w.orderByAsc(Word::getEnglish);
        return wordMapper.selectPage(page, w);
    }

    public List<String> listCategories() {
        return wordMapper.selectList(new LambdaQueryWrapper<Word>()
                        .select(Word::getCategory)
                        .eq(Word::getIsPublished, 1)
                        .groupBy(Word::getCategory))
                .stream().map(Word::getCategory).filter(Objects::nonNull).toList();
    }

    // ---------- AI 批量生成 ----------

    /**
     * 用 DeepSeek 批量生成单词。
     * prompt 要求 LLM 严格返回 JSON 数组, 后端容错解析。
     * 返回 GenerateResult{created, skipped, total}。
     */
    public GenerateResult aiGenerate(int count, String category, Integer difficulty, String topic) {
        if (count < 1 || count > 50) {
            throw new IllegalArgumentException("生成数量应在 1-50 之间");
        }

        String systemPrompt = """
                你是雅思口语/英语教学专家, 擅长根据用户要求生成实用的英语单词和中文释义。
                必须严格返回 JSON 数组, 每个元素包含以下字段, 不要输出任何额外文字或 Markdown:
                [
                  {
                    "english": "单词(小写字母, 复数/时态形式用 base form)",
                    "chinese": "中文释义(可多个用 / 分隔)",
                    "phonetic": "国际音标(IPA, 用 // 包裹, 如 //ˈkʌzn//)",
                    "partOfSpeech": "词性(noun/verb/adjective/adverb/preposition/phrase)",
                    "exampleSentence": "英文例句(贴合日常口语/雅思场景)",
                    "exampleTranslation": "例句中文翻译"
                  }
                ]
                """;

        String userPrompt = String.format(
                "生成 %d 个单词。要求: %s%s%s\n请严格按 JSON 数组输出, 不要 Markdown, 不要解释。",
                count,
                StringUtils.hasText(category) ? "类别: " + category + "。" : "通用高频实用单词。",
                difficulty != null ? "难度: " + difficulty + "/5。" : "",
                StringUtils.hasText(topic) ? "主题/场景: " + topic + "。" : ""
        );

        List<ChatMessage> messages = List.of(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userPrompt)
        );

        String raw = deepSeekClient.chat(messages);
        log.info("AI 生成单词原始响应({} chars)", raw.length());

        // 容错解析: 先直接 JSON.parse, 失败则 strip ```json ... ``` 或抓第一个 [...]
        List<WordDraft> drafts = parseWordDrafts(raw);
        if (drafts.isEmpty()) {
            return new GenerateResult(0, 0, "AI 返回为空或无法解析");
        }

        int created = 0;
        int skipped = 0;
        for (WordDraft d : drafts) {
            if (!StringUtils.hasText(d.english()) || !StringUtils.hasText(d.chinese())) {
                skipped++;
                continue;
            }
            String english = d.english().trim().toLowerCase();
            Word existing = wordMapper.selectOne(new LambdaQueryWrapper<Word>().eq(Word::getEnglish, english));
            if (existing != null) {
                skipped++;
                continue;
            }
            Word w = new Word();
            w.setEnglish(english);
            w.setChinese(d.chinese().trim());
            w.setPhonetic(d.phonetic());
            w.setPartOfSpeech(d.partOfSpeech());
            w.setCategory(StringUtils.hasText(category) ? category : "日常");
            w.setDifficulty(difficulty != null ? difficulty : 0);
            w.setExampleSentence(d.exampleSentence());
            w.setExampleTranslation(d.exampleTranslation());
            w.setSource("GENERATED");
            w.setIsPublished(1);
            wordMapper.insert(w);
            created++;
        }
        return new GenerateResult(created, skipped, null);
    }

    @SuppressWarnings("unchecked")
    private List<WordDraft> parseWordDrafts(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isArray()) {
                return objectMapper.convertValue(node,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, WordDraft.class));
            }
        } catch (Exception ignored) {}

        try {
            String cleaned = raw.replaceAll("(?s)^.*?```(?:json)?\\s*", "").replaceAll("(?s)\\s*```.*$", "");
            JsonNode node = objectMapper.readTree(cleaned);
            if (node.isArray()) {
                return objectMapper.convertValue(node,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, WordDraft.class));
            }
        } catch (Exception ignored) {}

        try {
            int start = raw.indexOf('[');
            int end = raw.lastIndexOf(']');
            if (start >= 0 && end > start) {
                JsonNode node = objectMapper.readTree(raw.substring(start, end + 1));
                if (node.isArray()) {
                    return objectMapper.convertValue(node,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, WordDraft.class));
                }
            }
        } catch (Exception ignored) {}

        log.warn("AI 生成单词 JSON 解析全部失败, 前 200 chars: {}", raw.length() > 200 ? raw.substring(0, 200) : raw);
        return new ArrayList<>();
    }

    /** 内部 record: LLM 返回的草稿 */
    private record WordDraft(String english, String chinese, String phonetic,
                             String partOfSpeech, String exampleSentence, String exampleTranslation) {}

    /** AI 生成结果 */
    @Data
    public static class GenerateResult {
        private final int created;
        private final int skipped;
        private final String error;

        public GenerateResult(int created, int skipped, String error) {
            this.created = created;
            this.skipped = skipped;
            this.error = error;
        }
    }

    // ---------- CSV 导入导出 ----------

    /** 导出 CSV, UTF-8 BOM, 适合 Excel 打开 */
    public byte[] exportCsv(String keyword, String category) {
        IPage<Word> page = Page.of(1, 5000);
        LambdaQueryWrapper<Word> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(Word::getEnglish, keyword)
                    .or().like(Word::getChinese, keyword));
        }
        if (StringUtils.hasText(category)) {
            w.eq(Word::getCategory, category);
        }
        w.orderByDesc(Word::getCreateTime);
        List<Word> all = wordMapper.selectPage(page, w).getRecords();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            // BOM
            baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            writer.write("english,chinese,phonetic,part_of_speech,category,difficulty,example_sentence,example_translation\n");
            for (Word word : all) {
                writer.write(String.join(",",
                        safeCsv(word.getEnglish()),
                        safeCsv(word.getChinese()),
                        safeCsv(word.getPhonetic()),
                        safeCsv(word.getPartOfSpeech()),
                        safeCsv(word.getCategory()),
                        word.getDifficulty() != null ? word.getDifficulty().toString() : "0",
                        safeCsv(word.getExampleSentence()),
                        safeCsv(word.getExampleTranslation())
                ));
                writer.write("\n");
            }
            writer.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("CSV 导出失败: " + e.getMessage(), e);
        }
    }

    private String safeCsv(String s) {
        if (s == null) return "";
        s = s.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\n") || s.contains("\"")) {
            return "\"" + s + "\"";
        }
        return s;
    }
}
