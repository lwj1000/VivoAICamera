# 构图助手 (VivoAICamera)

专为 **vivo X200 Ultra** 打造的个人拍照 AI 构图辅助 App（纯原生 Kotlin + Jetpack Compose + CameraX + ML Kit）。

## 核心功能

1. **四大场景专属构图引导（100% 端侧离线免费）**
   - **人像打卡**：自动检测人脸，实时判断头顶留白比例、人物大小占比与三分线站位，提供上下左右/靠近/退后引导箭头。
   - **风景建筑**：高精度双轴水平仪与垂直仰俯角提醒，防止地平线歪斜和建筑梯形畸变。
   - **美食静物**：桌面主体识别，支持 **90° 俯拍端平校准圆** 与 **45° 经典美食视角**提示。
   - **日常随手拍**：综合主体位置与水平度实时打出 **0~100 构图评分**，达标后快门环自动变绿。
2. **多焦段切换与网格辅助**
   - 自动读取硬件支持的超广角（0.6x）、主摄（1x）、中长焦（2x / 3.7x / 5x）倍率一键切换。
   - 支持三分法、黄金比例、中心对称、无网格切换，以及点击屏幕对焦测光。
   - 照片直接保存到系统相册 `Pictures/构图助手`。
3. **可选「AI 点评」（多模态大模型指导）**
   - 点击右下角 **「AI点评」**，可将当前取景画面发给兼容 OpenAI 格式的多模态视觉大模型，获取摆姿、机位和光影建议。
   - **不填 API Key 也能完整使用全部端侧实时构图功能**；如需使用可填写智谱免费视觉模型 `glm-4v-flash` 或阿里云百炼 `qwen-vl-plus` 等。

---

## 如何用 GitHub Actions 云端免费打包 APK（电脑无需安装 Android SDK）

1. 在 GitHub 上新建一个仓库（公开或私有均可），例如命名为 `VivoAICamera`。
2. 将本目录 **`VivoAICamera` 内的所有文件**（注意包含隐藏文件夹 `.github/workflows/build-apk.yml`）上传或 `git push` 到该仓库的 `main` 分支：
   ```bash
   cd D:\codex\测试\VivoAICamera
   git init
   git add .
   git commit -m "init VivoAICamera"
   git branch -M main
   git remote add origin https://github.com/<你的用户名>/VivoAICamera.git
   git push -u origin main
   ```
3. 推送完成后，打开 GitHub 仓库页面的 **Actions** 标签页，等待约 2~4 分钟即可看到 `Build Android APK` 构建完成（绿色对勾）。
4. 点进该次构建记录，在底部的 **Artifacts** 区域下载 **`vivo-ai-composition-cam-apk`**（解压后得到 `app-debug.apk`）。
5. 将 `app-debug.apk` 发送到你的 **vivo X200 Ultra** 点击安装即可（首次安装未知来源应用时，按系统提示允许安装）。
