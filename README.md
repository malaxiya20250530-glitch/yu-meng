# 🌙 语梦 (Yu Meng)

> **Live2D × Operit AI 虚拟助手 | GitHub Actions 云编译 APK**

语梦是一个将 Live2D 动态模型与 Operit 轻量级 AI 推理引擎结合的 Android 虚拟助手应用。支持自然语言对话、情感识别联动表情、云端一键编译发布。

---

## ✨ 特性

- 🎭 **Live2D 动态模型** — 实时渲染 2D 角色，支持表情切换与随机动作
- 🧠 **Operit AI 引擎** — 轻量级本地推理，自然语言理解与情感分析
- 🎬 **情感联动** — AI 检测情绪 → Live2D 自动切换表情(开心/难过/惊讶)
- ☁️ **GitHub 云编译** — 推送代码自动构建签名 APK，无需本地环境
- 📦 **零配置下载** — 编译完成直接下载安装包

---

## 🏗️ 架构

```
┌──────────────────────────────────┐
│          MainActivity             │
│  ┌──────────┐  ┌──────────────┐  │
│  │Live2DView│  │  ChatArea    │  │
│  │ 模型渲染  │  │  对话气泡     │  │
│  └────┬─────┘  └──────┬───────┘  │
│       │               │          │
│  ┌────▼───────────────▼───────┐  │
│  │      OperitEngine           │  │
│  │  NLP推理 · 情感检测 · 回复   │  │
│  └─────────────────────────────┘  │
└──────────────────────────────────┘
```

---

## ☁️ 云编译

推送代码到 `main` 分支自动触发，或手动在 Actions 页面运行。

编译完成后在 [Actions](https://github.com/malaxiya20250530-glitch/yu-meng/actions) 页面下载 `YuMeng-Live2D-AI`。

### 密钥配置

在仓库 Settings → Secrets → Actions 添加：

| Secret | 说明 |
|--------|------|
| `KEYSTORE_BASE64` | keystore 文件的 Base64 编码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_PASSWORD` | 密钥密码 |

---

## 🚀 本地开发

```bash
git clone https://github.com/malaxiya20250530-glitch/yu-meng.git
cd yu-meng
# Android Studio → Open Project → 选择 yu-meng 目录
```

---

Built with ❤️ · Live2D Cubism SDK · Operit AI
