# 项目：开屏广告 & 摇一摇拦截工具

完整设计见 docs/design.md，开始任何任务前先读它。

## 技术约定
- Kotlin + Jetpack Compose + MVVM + Hilt + Room + DataStore
- minSdk 26；不使用 root、Xposed 等方案
- 无障碍服务中的节点遍历不能阻塞主线程
- 规则格式严格按 design.md 第 6 节

## 协作方式
- 我是 Java 后端开发者，Android 和 Kotlin 是新手：
  关键代码请加中文注释，Android 特有概念第一次出现时简单解释
- 按 design.md 第 8 节的里程碑推进，一次只做一个里程碑
- 每个里程碑先给出实现计划，我确认后再写代码
- 完成后告诉我如何验证，等我确认再进入下一个