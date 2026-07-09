# CommitCraft

<p align="center">
  <img src="assets/commitcraft-logo.png" alt="CommitCraft logo" width="220">
</p>

一个 IntelliJ IDEA 插件，用 OpenAI-compatible Chat Completions API 根据当前 Git diff 生成提交信息。

## 插件介绍

`CommitCraft` 适合日常开发中快速生成规范、清晰的 Git commit message。插件会读取当前项目的 Git 变更内容，调用你配置的大模型接口，并把生成结果填入 IDEA 的 Commit 提交信息输入框。

当前版本：`0.2.1`

## 功能特性

- 在 IDEA Commit 工具窗口中生成提交信息。
- 生成按钮位于提交信息输入框上方工具栏，靠近“修正”和历史图标。
- 支持从已勾选的变更文件生成提交信息。
- 支持一个 IDEA 项目中包含多个子 Git 仓库的场景。
- 支持已勾选的 unversioned text 文件。
- 优先读取 staged diff，未 staged 时回退到 working tree diff。
- 支持 Git index 或 IntelliJ partial-line tracker 表达的部分行提交内容。
- 支持 OpenAI-compatible 接口，例如 OpenAI、DeepSeek、SiliconFlow 或公司内部网关。
- API Key 存储在 IDEA Password Safe 中，不写入普通配置文件。
- 支持离线自动激活，便于直接交付 ZIP 和操作手册。
- 保留 `Tools | Generate Commit Message` 和变更文件右键菜单入口。

## 下载

如果你只想安装插件，下载仓库中的 ZIP：

[releases/commitcraft-0.2.1.zip](releases/commitcraft-0.2.1.zip)

也可以从 GitHub Releases 下载同名 ZIP。

## 安装

1. 打开 IntelliJ IDEA。
2. 进入 `Settings/Preferences | Plugins`。
3. 点击插件页右上角齿轮按钮。
4. 选择 `Install Plugin from Disk...`。
5. 选择 `commitcraft-0.2.1.zip`。
6. 重启 IDEA。

如果你之前安装过旧版插件，建议先卸载旧版再安装新版。

## 配置

安装后进入：

`Settings/Preferences | Tools | CommitCraft`

常用配置项：

- `Endpoint`：OpenAI-compatible chat completions 地址。
- `Model`：模型名称。
- `API Key`：模型服务的 API Key。
- `Output Language`：提交信息输出语言，下拉可选常用全球语种，默认 `简体中文`。
- `Max Diff Chars`：发送给模型的最大 diff 字符数，避免超长 diff。
- `Temperature`：生成随机性，推荐 `0.2` 左右。
- `Prompt Template`：提交信息生成提示词模板。
- `Machine Code`：本机机器码，保留用于后续手动授权场景。
- `Activation Code`：离线激活码，默认会自动填充。

## 激活

安装后默认自动激活。买家只需要配置 `Endpoint`、`Model` 和 `API Key` 即可使用。

DeepSeek 示例：

```text
Endpoint: https://api.deepseek.com/chat/completions
Model: deepseek-v4-flash
Output Language: 简体中文
```

## 使用方法

1. 打开项目的 Commit 工具窗口。
2. 勾选本次要提交的文件或部分变更。
3. 在提交信息输入框上方点击 CommitCraft 图标 `Generate Commit Message`。
4. 等待生成完成，插件会把提交信息写入 Commit message 输入框。
5. 检查生成内容后再点击 `提交` 或 `提交并推送`。

也可以使用：

- `Tools | Generate Commit Message`
- 变更文件右键菜单中的 `Generate Commit Message`

## 生成范围说明

在 Commit 工具窗口触发时，插件会尽量按 IDEA 当前提交范围生成：

- 已勾选文件：只读取已勾选文件对应的 diff。
- 已勾选 unversioned 文件：会读取小型文本文件并转成 diff。
- 部分行提交：如果由 Git index 或 IntelliJ partial-line tracker 表达，会尽量只读取被纳入提交的部分。
- 如果没有可用的 partial tracker，会回退到文件级 diff。

从 `Tools` 菜单触发时，插件按仓库整体 diff 生成。

如果 IDEA 项目根目录本身不是 Git 仓库，但子目录是多个 Git 仓库，插件会按选中文件自动解析所属子仓库。

## 常见问题

### HTTP 402: Insufficient Balance

这是模型服务返回的余额不足错误，不是插件错误。需要给 API Key 所属账号充值，或换一个有余额的模型服务。

### 按钮没有出现

确认安装的是 `0.2.1` 或更新版本，并重启 IDEA。按钮应出现在 Commit message 输入框上方工具栏，靠近“修正”和历史图标。

### 生成结果为空或提示没有 diff

确认当前项目是 Git 仓库，并且至少有 staged、modified 或勾选的 unversioned 文本文件。

### 大文件没有被读取

插件会跳过大型 unversioned 文件和二进制文件，避免把无效或过大的内容发给模型。

## 开发运行

```bash
gradle runIde
```

## 构建插件 ZIP

```bash
gradle buildPlugin
```

插件 ZIP 会输出到：

```text
build/distributions/
```

当前本机环境中，Gradle 依赖解析曾遇到 Maven Central / Gradle Plugin Portal 的 TLS handshake 问题。因此当前发布 ZIP 是用本机 IntelliJ IDEA SDK jars 和本地 Gson 依赖手工打包生成的。

## 技术边界

- 当前发布包内置通用离线激活码，不需要联网；这适合低摩擦交付，但不适合强防盗版。
- 插件通过本地 `git` 命令读取 diff。
- 大 diff 会按 `Max Diff Chars` 截断。
- 二进制文件会被跳过。
- 非 staging 模式的部分行支持依赖 IntelliJ VCS implementation API；不同 IDEA 版本可能存在兼容差异。
