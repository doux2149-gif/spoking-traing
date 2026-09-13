<template>
  <div class="landing">
    <!-- 顶部导航 -->
    <header class="land-header">
      <div class="land-header-inner">
        <div class="land-logo">
          <span class="land-logo-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 2a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z" />
              <path d="M19 10v1a7 7 0 0 1-14 0v-1" />
              <line x1="12" y1="18" x2="12" y2="22" />
            </svg>
          </span>
          AI 英语口语对话
        </div>
        <nav class="land-nav">
          <a href="#features">功能特性</a>
          <a href="#how">使用流程</a>
        </nav>
        <div class="land-header-actions">
          <button class="btn-ghost" type="button" @click="go('/login')">登录</button>
          <button class="btn-primary" type="button" @click="go('/register')">免费注册</button>
        </div>
      </div>
    </header>

    <!-- Hero -->
    <section class="hero">
      <!-- 动态视频背景: 视频文件放 public/videos/landing-bg.mp4, 封面 landing-poster.jpg 可选 -->
      <video
        class="hero-video"
        src="/videos/landing-bg.mp4"
        poster="/videos/landing-poster.jpg"
        autoplay
        muted
        loop
        playsinline
        preload="auto"
      ></video>
      <!-- 品牌色蒙版: 压暗视频保证文字可读, 视频缺失时也不影响 -->
      <div class="hero-mask"></div>
      <div class="hero-inner">
        <div class="hero-text">
          <h1>和 AI 考官练口语<br />轻松备战雅思</h1>
          <p class="hero-sub">
            流式 AI 对话 · 实时语法纠错 · 真人语音播报 · 场景化练习
            <br />随时随地, 把口语考官装进口袋。
          </p>
          <div class="hero-actions">
            <button class="btn-primary btn-lg" type="button" @click="go('/login')">立即开始练习</button>
            <button class="btn-outline btn-lg" type="button" @click="go('/login')">我要登录</button>
          </div>
          <div class="hero-meta">无需下载 · 网页直接使用 · 注册即练</div>
        </div>
      </div>
    </section>

    <!-- 功能特性 -->
    <section id="features" class="features">
      <h2 class="section-title">核心功能</h2>
      <p class="section-sub">围绕雅思口语评分标准, 全链路辅助你的口语练习</p>
      <!-- 功能演示视频: 静音循环, 滚动进入视口时自动播放, 滚出自动暂停 -->
      <div class="feature-video">
        <video
          ref="featureVideo"
          src="/videos/features-demo.mp4"
          poster="/videos/features-poster.jpg"
          muted
          loop
          playsinline
          preload="metadata"
        ></video>
        <span class="feature-video__badge">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" aria-hidden="true">
            <path d="M8 5v14l11-7z" />
          </svg>
          功能演示
        </span>
      </div>
      <div class="feature-grid">
        <div v-for="f in features" :key="f.title" class="feature-card">
          <span class="feature-icon" v-html="f.icon" />
          <h3>{{ f.title }}</h3>
          <p>{{ f.desc }}</p>
        </div>
      </div>
    </section>

    <!-- 使用流程 -->
    <section id="how" class="how">
      <h2 class="section-title">三步开始练习</h2>
      <div class="how-grid">
        <div class="how-step">
          <span class="how-num">1</span>
          <h3>注册登录</h3>
          <p>一分钟创建账号, 直接进入对话练习</p>
        </div>
        <div class="how-step">
          <span class="how-num">2</span>
          <h3>选择场景</h3>
          <p>酒店入住、餐厅点餐等真实场景, 或进入自由对话</p>
        </div>
        <div class="how-step">
          <span class="how-num">3</span>
          <h3>对话 + 纠错</h3>
          <p>AI 考官实时回应, 每句话都有雅思评分维度的改进建议</p>
        </div>
      </div>
      <div class="how-cta">
        <button class="btn-primary btn-lg" type="button" @click="go('/login')">免费开始</button>
      </div>
    </section>

    <!-- Footer -->
    <footer class="land-footer">
      <div>© 2024 AI 英语口语对话系统 · 雅思口语练习平台</div>
      <div class="land-footer-links">
        <a href="#" @click.prevent="go('/login')">登录</a>
        <a href="#" @click.prevent="go('/register')">注册</a>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

function go(path: string): void {
  router.push(path)
}

// 功能演示视频: 进入视口自动播放, 离开视口自动暂停
const featureVideo = ref<HTMLVideoElement | null>(null)
let videoObserver: IntersectionObserver | null = null

onMounted(() => {
  const video = featureVideo.value
  if (!video) return
  // 用户系统开启"减少动态效果"时不自动播放, 保留封面
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  videoObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting && entry.intersectionRatio >= 0.4) {
          video.play().catch(() => {
            // 浏览器拦截自动播放时忽略, 保持封面
          })
        } else {
          video.pause()
        }
      }
    },
    { threshold: [0, 0.4] }
  )
  videoObserver.observe(video)
})

onUnmounted(() => {
  videoObserver?.disconnect()
  videoObserver = null
})

const features = [
  {
    title: 'AI 雅思考官对话',
    desc: '模拟真实口语考试考官, 流式回复, 像面对面交流一样自然',
    icon: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>'
  },
  {
    title: '实时语法纠错',
    desc: '按词汇多样性、语法多样性、流利性与连贯性给出雅思 7/8 分建议',
    icon: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4z"/></svg>'
  },
  {
    title: '场景化练习',
    desc: '酒店入住、餐厅点餐、旅行问路等高频场景, 逐个击破',
    icon: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polygon points="16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88 16.24 7.76"/></svg>'
  },
  {
    title: '真人语音交互',
    desc: 'AI 回答真人发音朗读, 支持语音输入对话, 练的是真正的口语',
    icon: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"/><path d="M19 10v1a7 7 0 0 1-14 0v-1"/><line x1="12" y1="18" x2="12" y2="22"/></svg>'
  },
  {
    title: '智能工具调用',
    desc: '问天气、查时间、查单词, AI 自动调用工具给你准确答案',
    icon: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>'
  },
  {
    title: '打卡与记录',
    desc: '连续打卡养成习惯, 历史对话随时回看, 见证每天进步',
    icon: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>'
  }
]
</script>

<style scoped>
.landing {
  min-height: 100vh;
  background: #fff;
  font-family: 'PingFang SC', 'Helvetica Neue', Arial, sans-serif;
}

/* ---------- Header ---------- */
.land-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid #eef1f6;
}

.land-header-inner {
  max-width: 1120px;
  margin: 0 auto;
  padding: 14px 24px;
  display: flex;
  align-items: center;
  gap: 32px;
}

.land-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 700;
  color: #1f2d3d;
}

.land-logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #2f6fe4);
}

.land-nav {
  display: flex;
  gap: 24px;
  flex: 1;
}

.land-nav a {
  color: #4e5969;
  text-decoration: none;
  font-size: 14px;
  transition: color 0.15s;
}

.land-nav a:hover {
  color: #409eff;
}

.land-header-actions {
  display: flex;
  gap: 12px;
}

/* ---------- Buttons ---------- */
.btn-primary,
.btn-ghost,
.btn-outline {
  border: none;
  cursor: pointer;
  border-radius: 8px;
  font-size: 14px;
  padding: 8px 18px;
  transition: all 0.15s;
}

.btn-primary {
  color: #fff;
  background: linear-gradient(135deg, #409eff, #2f6fe4);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.35);
}

.btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.45);
}

.btn-ghost {
  background: transparent;
  color: #4e5969;
  border: 1px solid #dcdfe6;
}

.btn-ghost:hover {
  color: #409eff;
  border-color: #409eff;
}

.btn-outline {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.5);
}

.btn-outline:hover {
  background: rgba(255, 255, 255, 0.22);
}

.btn-lg {
  padding: 12px 28px;
  font-size: 16px;
  border-radius: 10px;
}

/* ---------- Hero ---------- */
.hero {
  position: relative;
  /* 渐变作为兜底: 视频加载中/加载失败/移动端降级时显示 */
  background: linear-gradient(160deg, #123a8f 0%, #2f6fe4 55%, #4f9bff 100%);
  color: #fff;
  overflow: hidden;
}

.hero-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  z-index: 0;
}

.hero-mask {
  position: absolute;
  inset: 0;
  z-index: 1;
  /* 中性黑蒙版: 保留视频原本色彩, 中心压暗保证文字可读 */
  background: radial-gradient(
    ellipse at center,
    rgba(0, 0, 0, 0.55) 0%,
    rgba(0, 0, 0, 0.28) 55%,
    rgba(0, 0, 0, 0.08) 100%
  );
}

.hero-inner {
  position: relative;
  z-index: 2;
  max-width: 1120px;
  margin: 0 auto;
  padding: 110px 24px 120px;
  display: flex;
  justify-content: center;
}

.hero-text {
  max-width: 720px;
  text-align: center;
}

.hero-text h1 {
  font-size: 44px;
  line-height: 1.25;
  margin: 0 0 20px;
  letter-spacing: 1px;
  text-shadow: 0 2px 18px rgba(0, 0, 0, 0.35);
}

.hero-sub {
  font-size: 17px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.92);
  margin: 0 0 32px;
  text-shadow: 0 1px 12px rgba(0, 0, 0, 0.4);
}

.hero-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 20px;
}

.hero-meta {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.65);
}

/* ---------- Sections ---------- */
.section-title {
  text-align: center;
  font-size: 30px;
  color: #1f2d3d;
  margin: 0 0 10px;
}

.section-sub {
  text-align: center;
  color: #8a919f;
  font-size: 15px;
  margin: 0 0 44px;
}

.features {
  max-width: 1120px;
  margin: 0 auto;
  padding: 80px 24px 40px;
}

.feature-video {
  position: relative;
  max-width: 960px;
  margin: 0 auto 56px;
  border-radius: 18px;
  overflow: hidden;
  background: #f4f6fa;
  border: 1px solid #e9edf3;
  box-shadow: 0 24px 60px rgba(18, 58, 143, 0.14);
}

.feature-video video {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

.feature-video__badge {
  position: absolute;
  right: 12px;
  bottom: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 150px;
  padding: 9px 18px;
  border-radius: 999px;
  font-size: 13px;
  letter-spacing: 2px;
  color: #5f6b7a;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  pointer-events: none;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 22px;
}

.feature-card {
  border: 1px solid #eef1f6;
  border-radius: 14px;
  padding: 26px 24px;
  transition: all 0.2s;
  background: #fbfcfe;
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 28px rgba(31, 45, 61, 0.08);
  border-color: #d6e8ff;
}

.feature-icon {
  display: inline-flex;
  width: 46px;
  height: 46px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  color: #409eff;
  background: rgba(64, 158, 255, 0.1);
  margin-bottom: 16px;
}

.feature-card h3 {
  margin: 0 0 8px;
  font-size: 17px;
  color: #1f2d3d;
}

.feature-card p {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.7;
  color: #6b7482;
}

/* ---------- How ---------- */
.how {
  max-width: 1120px;
  margin: 0 auto;
  padding: 60px 24px 90px;
}

.how-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 22px;
  margin-bottom: 48px;
}

.how-step {
  text-align: center;
  padding: 30px 22px;
  border-radius: 14px;
  background: #f7f9fc;
}

.how-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #2f6fe4);
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 14px;
}

.how-step h3 {
  margin: 0 0 8px;
  font-size: 17px;
  color: #1f2d3d;
}

.how-step p {
  margin: 0;
  font-size: 13.5px;
  color: #6b7482;
  line-height: 1.7;
}

.how-cta {
  text-align: center;
}

/* ---------- Footer ---------- */
.land-footer {
  border-top: 1px solid #eef1f6;
  padding: 28px 24px;
  text-align: center;
  color: #9096a3;
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.land-footer-links a {
  color: #6b7482;
  text-decoration: none;
  margin: 0 10px;
}

.land-footer-links a:hover {
  color: #409eff;
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  /* 移动端不加载视频, 降级为渐变背景, 节省流量 */
  .hero-video {
    display: none;
  }

  .hero-mask {
    background: linear-gradient(160deg, rgba(18, 58, 143, 0.9), rgba(47, 111, 228, 0.85));
  }

  .hero-inner {
    flex-direction: column;
    padding: 56px 20px 64px;
  }

  .hero-text h1 {
    font-size: 32px;
  }

  .land-nav {
    display: none;
  }

  .feature-grid,
  .how-grid {
    grid-template-columns: 1fr;
  }
}

/* 用户系统设置开启"减少动态效果"时, 不播放视频 */
@media (prefers-reduced-motion: reduce) {
  .hero-video {
    display: none;
  }
}
</style>
