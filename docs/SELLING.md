# CommitCraft 售卖说明

## 售卖方式

可以给买家发送 GitHub Release、网盘或其他下载链接。当前发布包已内置通用离线激活码，买家安装后默认自动激活，不需要等待你手工生成 license。

建议流程：

1. 买家安装插件。
2. 买家打开 `Settings/Preferences | Tools | CommitCraft`。
3. 买家配置自己的模型 `Endpoint`、`Model` 和 `API Key` 后使用。

这种方式交付最省事，但 ZIP 被转发后别人也能使用。如果后续要做强授权，可以改回“机器码 + 手动签发激活码”或接入授权服务器。

## 手动生成激活码

当前版本默认不需要手动生成激活码。下面流程保留给后续强授权版本使用。

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
- 下单后提供安装包下载链接和操作手册
- 安装后默认自动激活
- 非 JetBrains/OpenAI/DeepSeek 官方产品
```

避免写：

```text
官方 IDEA 插件
官方 AI 提交工具
内置免费 AI
永久免费无限制
```
