# fight-game-web-1,0

文字格斗游戏 Web 1.0 版：支持注册登录、找回密码、创建角色分配属性、回合制战斗使用道具和技能。

##写在前面
作者本人只是一个刚开始全栈项目学习的小白，这个项目主要改动自黑马最新的java+ai课程里面的一个控制台小游戏。由于我是从前端开始学习的，我当时学到那个小游戏的时候就想为什么不能把这个小游戏和前端结合在一起，让游戏的界面更加丰富，也正好可以练习一下自己全栈项目的能力。所以本项目的灵感来源于黑马，但是其中的springboot+maven+前端页面是本人所写，由于水平有限，项目十分粗糙，也欢迎大家提出意见。最后，如果大家觉得我的项目对你有用，可以帮我点亮一颗star。

## 技术栈

- 后端：Java 17 + Spring Boot+Maven
- 前端：原生 HTML / CSS / JavaScript，静态资源位于 `src/main/resources/static`
- 数据库：无数据库依赖，数据保存在内存中

## 环境要求

| 使用方式 | 需要安装 |
|---|---|
| 直接运行 Release 中的 jar | JDK 17 或 JRE 17 |
| 从源码构建 | JDK 17 + Maven + springboot |
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

- 注册 / 登录 / 忘记密码，验证码校验
- 创建角色：四项属性自由分配，合计 30 点
- 回合制战斗：普通攻击、技能、使用道具、逃跑
- 敌人：糖人豆包、奶娃坦克、大肥鱼、鱼人沃特
- 每累计 3 场胜利属性成长，支持重新分配属性

## 当前限制

- 用户、角色、战斗状态均保存在进程内存中，**服务重启后数据清空**
- 验证码为全服单一字段，多人同时登录时可能互相覆盖
- 仅支持单实例部署，无法水平扩容


