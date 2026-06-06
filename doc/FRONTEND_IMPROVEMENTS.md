# SynchPlay — 前端改善方案(给前端组员)

> 目标:把现在"通用深色后台"的观感,统一升级成一套**有记忆点、贴合"社交图谱推荐"主题**的设计系统。
> 关键策略:**几乎所有改动集中在 `frontend-vue/src/style.css`(全局样式)+ 字体引入 + 少量结构**。所有标签页共用 `.card / .video-card / .topnav / .btn-* / table` 等全局类,所以改一处、全站统一升级,**不碰任何业务逻辑、风险很低**。

## 推荐方向:网络 / 数据终端感(Network / Data-Terminal)

近黑背景 + 等宽字体 + 细网格线 + 节点/连线装饰,像一块"网络监控面板"。技术感强、独特,和图算法主题最搭。

---

## 1. 设计 Token(直接替换 `style.css` 的 `:root`)

```css
:root {
  /* 背景分层:近黑 → 略亮的面板 */
  --bg:        #0a0e14;
  --surface:   #111721;
  --surface2:  #161e2b;
  --card-bg:   #121925;
  --border:    #1e2a3a;
  --border-lit:#2c3e57;        /* hover/active 时的亮边 */

  /* 主色:电青 + 一个暖色副强调 */
  --accent:    #4cc9f0;        /* 主强调:链接、active、数据高亮 */
  --accent2:   #2dd4bf;        /* 副强调:teal,用于第二类数据 */
  --warn:      #fbbf24;
  --danger:    #f87171;
  --success:   #34d399;

  /* 文本 */
  --text:      #d6e2f0;
  --text-dim:  #6b7c93;        /* 次要文字、标签 */

  /* 发光(节点/active 用) */
  --glow: 0 0 0 1px var(--accent), 0 0 12px -2px var(--accent);
}
```

## 2. 字体(在 `index.html` 引入,改 `style.css` 的 `font-family`)

用 **IBM Plex** 家族——技术感、成体系、不落俗套(避开 Inter/Roboto/Arial):
- **IBM Plex Mono** → 品牌名、标题、标签、数字/分数(强化"数据终端"感)
- **IBM Plex Sans** → 正文

```html
<!-- index.html <head> -->
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&family=IBM+Plex+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
```
```css
:root { font-family: 'IBM Plex Sans', system-ui, sans-serif; }
.brand, h1, h2, h3, .value-tag, th, .score, .dist-badge, .native-badge { font-family: 'IBM Plex Mono', monospace; }
```

## 3. 氛围背景(给 `body` 加细网格,体现"图")

```css
body {
  background:
    linear-gradient(var(--border) 1px, transparent 1px) 0 0 / 40px 40px,
    linear-gradient(90deg, var(--border) 1px, transparent 1px) 0 0 / 40px 40px,
    radial-gradient(1200px 600px at 80% -10%, #13233a 0%, var(--bg) 60%);
  background-blend-mode: overlay, overlay, normal;
}
```
(网格要非常淡,几乎只在边缘可见——靠把 `--border` 调暗即可。)

---

## 4. 任务清单(按优先级)

### P0 — 设计系统重做(改完全站就变样,1 个人 1~2 天)
- [ ] 替换 `:root` Token(上面那套)
- [ ] 引入 IBM Plex 字体,标题/数据用 Mono
- [ ] `body` 加网格 + 角落光晕背景
- [ ] **卡片升级**:`.card / .video-card` 加细边框、左上"角标"装饰(`::before` 画两条短线像电路角)、hover 时 `border-color: var(--border-lit)` + 轻微上浮 + 缩略图微缩放
- [ ] **顶栏升级**:`.topnav` 半透明 + `backdrop-filter: blur(8px)` 固定吸顶;active 标签用下划线指示条而非纯色块
- [ ] **按钮/输入**:focus 用 `--glow`;`.btn-primary` 用 accent 描边+浅填充而非实心,更克制
- [ ] **表格**(PageRank/History):表头吸顶、行 hover 高亮、PageRank 进度条用 accent 渐变

### P1 — 品牌与状态(半天)
- [ ] `index.html` 标题从 `frontend-vue` 改成 `SynchPlay — Graph Video Recs`,换个 favicon(可用一个简单的"节点-连线"SVG)
- [ ] **登录/注册页**做成有记忆点的入口:左侧一块"社交图"装饰(SVG 节点连线 + 轻动画),右侧表单;别再是居中一张卡
- [ ] **加载态**用骨架屏(skeleton)替代纯文字 "Loading…"
- [ ] **空状态**(没好友/没历史/没上传)配一句话 + 一个引导按钮,别只一行灰字

### P2 — 动效与细节打磨(锦上添花)
- [ ] 页面载入:卡片**错峰淡入**(`animation-delay` 递增),一次有编排的入场胜过到处小动画
- [ ] 推荐卡 hover:缩略图轻微 zoom + 分数条增长动画
- [ ] Echo Chamber 风险等级用"信号强度/进度环"可视化,而不是纯文字 Low/Med/High
- [ ] 原生上传视频的 `native` 角标做成发光小胶囊,和 YouTube 视频区分更明显
- [ ] 统一圆角(建议卡片 12px、按钮 8px)、统一阴影层级

---

## 5. 验收标准
1. 六个标签页 + 登录/注册/上传**全部正常**(只是变好看,功能不变)。
2. **响应式**:窄屏(<900px)顶栏不挤爆,卡片网格自适应;手机宽度可用。
3. **可访问性**:文字对比度达标(深底浅字 ≥ 4.5:1)、输入有可见 focus 态、图片有 `alt`。
4. `npm run build` 通过,无控制台报错。

## 6. 落点文件一览
| 改什么 | 文件 |
|--------|------|
| 设计 Token / 全局类 / 背景 / 动效 | `frontend-vue/src/style.css`(主战场) |
| 字体引入 + 标题 + favicon | `frontend-vue/index.html` |
| 登录/注册视觉 | `src/views/LoginPage.vue` / `RegisterPage.vue` |
| 顶栏结构(active 指示条) | `src/views/AppShell.vue` |
| 骨架屏 / 空状态 | 各 `*Tab.vue`(用全局 skeleton 类,逐个套) |
| 缩略图组件(已存在) | `src/components/VideoThumb.vue` |

> 提示:P0 做完就已经"脱胎换骨"了;P1/P2 是加分项,时间紧可只做 P0+品牌。
> 不要改 `src/api/`、`src/stores/`、`src/router/` 里的逻辑——纯视觉层即可。
