# Minecraft Control

远程控制一个 Minecraft 机器人的 Fabric 模组对，支持跨版本（机器人 1.20.1 / 主人 26.2）。

## 组件

| 目录 | 版本 | 角色 |
|---|---|---|
| `robot-mod/` | Minecraft 1.20.1 + Fabric | 装在**机器人账号**客户端：执行命令、转发结果、命令补全、状态/背包/掉落 |
| `owner-mod/` | Minecraft 26.2 + Fabric | 装在**主人**客户端：命令补全面板、`/status` `/mybag` `/drop` 快捷命令 |

## 功能

- **远程执行**：聊天框输入 `<机器人名> cmd <命令>`，机器人执行，命令反馈（如 `[RobotCmd] ...`）通过 `/msg` **私聊回显**给你，不打扰其他玩家；`/say` 这类本身就是公屏广播的命令，输出照常显示在公屏
- **命令补全**：`<机器人名> cmd <前缀>` 后按 Tab，补全列表显示在输入框上方（服务端命令 + Baritone `#` 命令），Tab 循环选择，Enter 填入
- **快捷命令**（注册在机器人客户端，结果私聊回显）：
  - `MyBot cmd /status` — 生命/饱食/饱和/护甲/全套装备
  - `MyBot cmd /mybag` — 背包物品清单（按物品聚合）
  - `MyBot cmd /drop <物品> [数量]` — 从背包/物品栏真实掉落（自动挪到选中格再扔，服务端权威）
- **安全**：所有指令仅限配置中的 ownerNames 触发；1 秒冷却防刷屏；自回显拦截防死循环

## 通信原理

全程**不需要服务器端任何配合**（无管理员权限也可用）：

```
主人(26.2) 输入 "MyBot cmd /give @p 钻石 64"
  → 服务器广播聊天消息
  → 机器人(1.20.1) 收到，识别 cmd 前缀
  → 执行命令 / 内部读取本地状态
  → 结果 /msg 私聊回显给主人（公屏只出现 /say 之类的主动广播）
```

补全流程：主人按 Tab → 发 `<机器人名> cmds <前缀>` → 机器人向自己服务器请求补全 + 调 Baritone `tabComplete` → `/msg` 私聊返回 `[RC-SUGG] JSON` → 主人端模组拦截解析并显示。

## 构建

需要 JDK 17（robot-mod）和 JDK 25（owner-mod，MC 26.2 要求）：

```bash
# 机器人端 (1.20.1)
cd robot-mod && ./gradlew build

# 主人端 (26.2)
cd owner-mod && JAVA_HOME=<jdk25> ./gradlew build
```

国内网络可在 `gradle/wrapper/gradle-wrapper.properties` 把 distributionUrl 换成腾讯云镜像，Maven 依赖走阿里云镜像（本项目已配置）。

## 配置

首次启动生成配置文件（在各自客户端的 `config/` 目录）：

**robot-mod** → `config/robotcmd.json`：
```json
{ "ownerNames": ["你的游戏名"], "botId": "", "broadcastResults": true, "captureWindowMs": 1500 }
```

**owner-mod** → `config/ownercmd.json`：
```json
{ "botId": "MyBot", "requestKeyword": "cmds", "replyToken": "[RC-SUGG]" }
```

## 部署

1. 机器人客户端 `mods/`：`robotcmd.jar` + Fabric API + Baritone（可选）
2. 主人客户端 `mods/`：`ownercmd.jar` + Fabric API
3. 机器人账号需有 OP 权限才能执行 `/give` 等服务端命令

## 许可证

MIT
