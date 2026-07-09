# CommitCraft 售卖说明

## 售卖方式

可以给买家发送 GitHub Release、网盘或其他下载链接，再单独发送激活码。插件 ZIP 可以公开下载，但生成提交信息功能需要有效激活码。

建议流程：

1. 买家安装插件。
2. 买家打开 `Settings/Preferences | Tools | CommitCraft`。
3. 买家复制 `Machine Code` 发给你。
4. 你用本机私钥生成激活码。
5. 买家把激活码填入 `Activation Code` 并保存。
6. 买家配置自己的模型 `Endpoint`、`Model` 和 `API Key` 后使用。

## 生成激活码

编译签发工具：

```bash
javac -d build/license-tool tools/license/LicenseKeyTool.java
```

生成绑定机器码、有效期 365 天的激活码：

```bash
java -cp build/license-tool LicenseKeyTool issue \
  --private seller-secrets/commitcraft-private.pkcs8 \
  --buyer "买家昵称或订单号" \
  --machine "买家发来的机器码" \
  --days 365
```

生成永久激活码：

```bash
java -cp build/license-tool LicenseKeyTool issue \
  --private seller-secrets/commitcraft-private.pkcs8 \
  --buyer "买家昵称或订单号" \
  --machine "买家发来的机器码"
```

生成不绑定机器的激活码：

```bash
java -cp build/license-tool LicenseKeyTool issue \
  --private seller-secrets/commitcraft-private.pkcs8 \
  --buyer "买家昵称或订单号" \
  --machine "*" \
  --days 365
```

不建议默认使用不绑定机器的激活码，买家转发后别人也能用。

## 私钥保管

私钥在本机：

```text
seller-secrets/commitcraft-private.pkcs8
```

这个目录已被 `.gitignore` 忽略，不要上传到 GitHub、网盘、聊天软件或发给买家。私钥泄露后，任何人都能生成有效激活码。

## 闲鱼商品描述建议

可写：

```text
CommitCraft 是一款第三方 IntelliJ IDEA 插件，可根据当前 Git diff 生成规范提交信息。

说明：
- 支持 IntelliJ IDEA 2024.2+
- 需要自行准备 OpenAI-compatible 模型 API Key
- 插件不包含模型额度，不代付 API 费用
- 下单后提供安装包下载链接和激活码
- 激活码默认绑定一台机器
- 非 JetBrains/OpenAI/DeepSeek 官方产品
```

避免写：

```text
官方 IDEA 插件
官方 AI 提交工具
内置免费 AI
永久免费无限制
```
