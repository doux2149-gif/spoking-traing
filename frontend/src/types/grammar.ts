/**
 * 语法纠错相关类型定义。
 * 与后端 GrammarCorrection record 字段一一对应。
 */

/** 单条错误明细 */
export interface GrammarError {
  /** 错误片段 */
  wrongText: string
  /** 修改后的片段 */
  correctText: string
  /** 中文简短错误原因 */
  reason: string
}

/** 表达建议 */
export interface Suggestion {
  /** 建议等级：none/better/advanced */
  level: 'none' | 'better' | 'advanced'
  /** 用户原句 */
  original?: string
  /** 2-3种更地道的替代表达 */
  alternatives?: string[]
  /** 中文简短解释为什么更好 */
  tip?: string
  /** 语音朗读文本(TTS 用) */
  speechText?: string
}

/** 语法纠错结果 */
export interface GrammarCorrection {
  /** 是否无错误;true 时其余字段可能为空 */
  noError: boolean
  /** 用户原始英文句子 */
  original?: string
  /** 修正后的完整句子 */
  correctText?: string
  /** 错误明细列表 */
  errors?: GrammarError[]
  /** 适合朗读的中英文结合文本(TTS 用) */
  speechText?: string
  /** 表达建议 */
  suggestion?: Suggestion
}
