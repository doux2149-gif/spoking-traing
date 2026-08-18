<script setup lang="ts">
import type { GrammarCorrection } from '../../types/grammar'

const props = defineProps<{
  grammar: GrammarCorrection
}>()

const emit = defineEmits<{
  playText: [text: string]
}>()

/** 是否有建议需要展示 */
function hasSuggestion(): boolean {
  return !!(props.grammar.suggestion
    && props.grammar.suggestion.level
    && props.grammar.suggestion.level !== 'none')
}
</script>

<template>
  <!-- 有语法错误时:显示纠错卡片 -->
  <div v-if="!grammar.noError" class="grammar-card">
    <div class="grammar-card__header">语法纠错</div>

    <div class="grammar-card__row">
      <span class="grammar-card__label">原句</span>
      <span class="grammar-card__original">{{ grammar.original }}</span>
    </div>

    <div class="grammar-card__row">
      <span class="grammar-card__label">纠正</span>
      <span class="grammar-card__correct">{{ grammar.correctText }}</span>
    </div>

    <ul v-if="grammar.errors?.length" class="grammar-card__errors">
      <li v-for="(err, i) in grammar.errors" :key="i" class="grammar-card__error">
        <div class="grammar-card__error-text">
          <span class="grammar-card__wrong">{{ err.wrongText }}</span>
          <span class="grammar-card__arrow">→</span>
          <span class="grammar-card__fixed">{{ err.correctText }}</span>
        </div>
        <p class="grammar-card__reason">{{ err.reason }}</p>
      </li>
    </ul>
  </div>

  <!-- 无错误但有建议时:显示建议卡片 -->
  <div v-if="hasSuggestion()" class="suggestion-card">
    <div class="suggestion-card__header">
      <span>💡 更好的表达方式</span>
      <span class="suggestion-card__level" :class="`suggestion-card__level--${grammar.suggestion!.level}`">
        {{ grammar.suggestion!.level === 'advanced' ? '雅思8分' : '雅思7分' }}
      </span>
    </div>

    <div v-if="grammar.suggestion!.original" class="suggestion-card__row">
      <span class="suggestion-card__label">你的句子</span>
      <span class="suggestion-card__original">{{ grammar.suggestion!.original }}</span>
    </div>

    <div v-if="grammar.suggestion!.alternatives?.length" class="suggestion-card__alternatives">
      <div
        v-for="(alt, i) in grammar.suggestion!.alternatives"
        :key="i"
        class="suggestion-card__alt"
      >
        <span class="suggestion-card__alt-icon">{{ i === 0 ? '★' : '○' }}</span>
        <span class="suggestion-card__alt-text" :class="{ 'suggestion-card__alt-text--top': i === 0 }">{{ alt }}</span>
        <button class="suggestion-card__play-btn" type="button" @click="emit('playText', alt)">🔊</button>
      </div>
    </div>

    <p v-if="grammar.suggestion!.tip" class="suggestion-card__tip">
      💬 {{ grammar.suggestion!.tip }}
    </p>
  </div>
</template>

<style scoped>
/* ===== 纠错卡片（橙色，已有样式） ===== */
.grammar-card {
  margin-top: 8px;
  padding: 10px 12px;
  border: 1px solid #f0ad4e;
  border-left: 3px solid #f0ad4e;
  border-radius: 6px;
  background: #fff8e1;
  font-size: 13px;
  color: #5d4037;
}

.grammar-card__header {
  font-size: 12px;
  font-weight: 600;
  color: #f57c00;
  margin-bottom: 6px;
}

.grammar-card__row {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
  line-height: 1.5;
}

.grammar-card__label {
  flex-shrink: 0;
  width: 32px;
  font-size: 11px;
  color: #8d6e63;
  padding-top: 2px;
}

.grammar-card__original {
  color: #c62828;
  text-decoration: line-through;
  text-decoration-color: rgba(198, 40, 40, 0.5);
}

.grammar-card__correct {
  color: #2e7d32;
  font-weight: 500;
}

.grammar-card__errors {
  list-style: none;
  padding: 0;
  margin: 8px 0 0;
}

.grammar-card__error {
  padding: 6px 8px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 4px;
  margin-bottom: 4px;
}

.grammar-card__error-text {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.grammar-card__wrong {
  color: #c62828;
  font-weight: 500;
}

.grammar-card__arrow {
  color: #8d6e63;
}

.grammar-card__fixed {
  color: #2e7d32;
  font-weight: 500;
}

.grammar-card__reason {
  margin: 0;
  font-size: 12px;
  color: #6d4c41;
  line-height: 1.4;
}

/* ===== 建议卡片（蓝色，鼓励性） ===== */
.suggestion-card {
  margin-top: 8px;
  padding: 10px 12px;
  border: 1px solid #81d4fa;
  border-left: 3px solid #0288d1;
  border-radius: 6px;
  background: #e1f5fe;
  font-size: 13px;
  color: #01579b;
}

.suggestion-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: #0277bd;
  margin-bottom: 8px;
}

.suggestion-card__level {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
}

.suggestion-card__level--better {
  background: #b3e5fc;
  color: #0277bd;
}

.suggestion-card__level--advanced {
  background: #e1bee7;
  color: #7b1fa2;
}

.suggestion-card__row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  line-height: 1.5;
}

.suggestion-card__label {
  flex-shrink: 0;
  font-size: 11px;
  color: #0288d1;
  padding-top: 2px;
}

.suggestion-card__original {
  color: #455a64;
}

.suggestion-card__alternatives {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
}

.suggestion-card__alt {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 4px;
}

.suggestion-card__alt-icon {
  color: #0288d1;
  font-size: 14px;
  flex-shrink: 0;
}

.suggestion-card__alt-text {
  flex: 1;
  color: #37474f;
  line-height: 1.5;
}

.suggestion-card__alt-text--top {
  color: #0277bd;
  font-weight: 600;
}

.suggestion-card__play-btn {
  flex-shrink: 0;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 16px;
  opacity: 0.6;
  transition: opacity 0.2s;
  padding: 2px 4px;
}

.suggestion-card__play-btn:hover {
  opacity: 1;
}

.suggestion-card__tip {
  margin: 0;
  font-size: 12px;
  color: #546e7a;
  line-height: 1.5;
  padding-top: 4px;
  border-top: 1px dashed #b3e5fc;
}
</style>
