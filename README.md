# fight-game-web

文字格斗游戏 Web 版：注册登录、创建角色、回合制战斗，前端页面与后端服务打包在同一个 jar 内，启动后即可访问。

## 技术栈

- Java 17 + Spring Boot 3.2.5
- 原生 HTML / CSS / JavaScript，静态资源位于 `src/main/resources/static`
- 无数据库依赖，数据保存在内存中

## 环境要求

| 使用方式 | 需要安装 |
|---|---|
| 直接运行 Release 中的 jar | JDK 17 或 JRE 17 |
| 从源码构建 | JDK 17 + Maven 3.9+ |
| Docker | Docker Desktop |

## 快速开始

### 方式一：下载 Release 中的 jar（最简单）

从 [Releases](https://github.com/jglChina/fight-game-web-1.0/releases) 页面下载 `fight-game-web-1.0.0.jar`，放到任意目录，然后执行：

```bash
java -jar fight-game-web-1.0.0.jar
```

启动后浏览器访问 <http://localhost:8080>，看到登录页即为成功。
控制台出现 `Tomcat started on port 8080` 表示服务已就绪。

如果你已经自己构建过，也可以直接运行本地产物：

```bash
java -jar target/fight-game-web-1.0.0.jar
```

### 方式二：从源码构建

```bash
git clone https://github.com/jglChina/fight-game-web-1.0.git
cd fight-game-web-1.0
mvn -B clean package -DskipTests
java -jar target/fight-game-web-1.0.0.jar
```

### 方式三：Docker

```bash
docker build -t fight-game-web .
docker run --rm -p 8080:8080 fight-game-web
```

### 方式四：开发调试

```bash
mvn spring-boot:run
```

## 停止服务

- 前台运行时按 `Ctrl+C`
- 后台运行时按端口查找并结束进程（Windows）：

```powershell
$id = (Get-NetTCPConnection -LocalPort 8080 -State Listen).OwningProcess
Stop-Process -Id $id -Force
```

## 端口被占用

服务默认监听 8080。若提示 `Port 8080 was already in use`，说明已有实例在运行，先停止旧进程或修改
`src/main/resources/application.properties` 中的 `server.port`。

## 项目结构

```
src/main/java/uestc/jgl/fightgame
├── common/        统一返回结构 Result、业务异常 BusinessException
├── config/        全局异常处理、会话管理 SessionManager、战斗管理 BattleManager
├── controller/    用户、角色、战斗、道具的 HTTP 接口
├── dto/           请求体与视图对象
├── entity/        用户、角色、敌人、技能、消耗品等模型
├── service/       用户、角色、战斗、道具业务逻辑
└── FightGameApplication.java  启动类

src/main/resources
├── application.properties     端口、编码、客服电话
└── static/                    登录页、主页、战斗页及其样式与脚本
```

## 主要功能

- 注册 / 登录 / 忘记密码，图形验证码校验
- 创建角色：四项属性自由分配，合计 30 点
- 回合制战斗：普通攻击、技能、使用道具、逃跑
- 敌人：糖人豆包、奶娃坦克、大肥鱼、鱼人沃特
- 每累计 3 场胜利属性成长，支持重新分配属性

## 已知限制

- 用户、角色、战斗状态均保存在进程内存中，**服务重启后数据清空**
- 验证码为全服单一字段，多人同时登录时可能互相覆盖
- 仅支持单实例部署，无法水平扩容

## 本机开发脚本

`build.ps1` 为当前开发机专用脚本，内部写死了 JDK 与 Maven 的本地路径，其他环境请直接使用
`mvn` 命令构建。
