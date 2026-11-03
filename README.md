# 易行租车 · 车辆租赁管理系统

易行租车是一个前后端联通的车辆租赁管理项目，包含管理端和客户端，支持车辆、客户、预约、交还车、收退款、调度、维修保养、统计与系统管理。收退款仅登记线下业务，不接入真实支付。

本文是项目统一说明，包含运行方法、业务操作、详细文件位置、接口约定、测试和常见问题。

技术组成：前端为 Vue 3、Element Plus、Axios、ECharts 和 Vite；后端为 Java 21、Spring Boot 和 MyBatis，使用服务端会话与 BCrypt 密码哈希；推荐连接 MySQL，代码保留 H2 兼容模式。

**阅读导航：** [快速启动](#quick-start) · [使用与操作说明](#usage) · [详细文件说明](#files) · [接口与数据](#api) · [开发与验证](#development) · [常见问题](#faq) · [素材与使用边界](#assets)

<a id="quick-start"></a>

## 快速启动

### 运行环境

| 环境 | 要求与用途 |
| --- | --- |
| Java | JDK 21，运行和编译后端。 |
| Maven | 3.9 或以上，安装后端依赖、测试和打包。 |
| Node.js | 推荐 22，包含 npm，安装和构建前端。 |
| Python | Python 3，执行启动、数据库和辅助测试脚本。启动本身不需要额外 Python 包。 |
| MySQL | 推荐使用 MySQL 8；可以连接已有实例。本机专用实例的脚本配置见下文。 |
| Windows 桌面入口 | Windows 10/11、.NET Framework 4.8；其他系统使用 Python 命令行入口。 |

在终端检查环境：

```powershell
java -version
mvn -version
node --version
npm --version
python --version
```

启动脚本优先使用项目 `.tools/` 中的 JDK、Maven，再查找系统环境。`.tools/`、依赖、数据库和构建产物不随 Git 分发，首次运行需要自行准备环境并联网下载依赖。

### 启动与停止

**Windows 推荐：**在完整项目根目录双击 [易行租车启动器.exe](易行租车启动器.exe)，点击“启动并打开系统”；也可以双击 [start.cmd](start.cmd)。

| 启动器操作 | 说明 |
| --- | --- |
| 启动并打开系统 | 检查环境；必要时安装前端依赖和构建前后端，等待就绪后打开网页。 |
| 打开网页 | 服务就绪后再次打开系统，不重复启动。 |
| 取消启动 | 取消本窗口正在安装、构建或启动的任务。 |
| 停止本次服务 | 只停止本窗口创建的前后端进程；复用已有服务时不能用它停止原服务。 |
| 查看日志文件夹 | 打开 `data/runtime/`，查看后端、前端和构建日志。 |

命令行方式：在 VS Code 中打开项目根目录，再在该目录的终端执行：

```powershell
python scripts/run.py --open
```

| 命令 | 使用场景 |
| --- | --- |
| `python scripts/run.py --open` | 常规启动，就绪后自动打开网页。 |
| `python scripts/run.py --dev --open` | 前端开发模式，修改 Vue/CSS 后热更新。 |
| `python scripts/run.py --build --open` | 停止现有前后端后，重新测试、构建并启动。 |
| `python scripts/run.py --check` | 只检查前后端状态，不安装、启动或停止服务。 |
| `python scripts/run.py --frontend-port 15173 --open` | 手动指定本机网页端口；通常无需指定，启动器会自动避开系统保留端口。 |
| `python scripts/run.py --mysql --open` | 显式选择 MySQL，连接配置见下一节。 |

命令行窗口需要保持开启，按 `Ctrl+C` 停止本次启动的前后端。桌面启动器关闭前会提示是否停止它管理的服务。数据库进程独立运行，停止前后端不会自动停止 MySQL。

EXE 依赖完整项目，不能单独复制出去运行。普通启动复用已有服务和构建结果，不会自动应用所有源码修改；修改后端或非开发模式下的前端后，应停止服务并重新构建。

默认网页端口为 `5173`。如果 Windows 把它列为系统保留端口，启动器会自动尝试 `15173`、`25173`，并在窗口及日志中显示实际地址；“打开网页”和 `--check` 也会使用当前地址。正在被其他程序占用的端口仍会提示冲突，不会强行关闭该程序。若复用的旧后端不允许新的网页来源，请先通过原启动器停止后端，再重新启动。

### 地址与登录

| 项目 | 默认地址或账号 |
| --- | --- |
| 前端 | `http://127.0.0.1:5173/` |
| 后端 | `http://127.0.0.1:8080` |
| 本机专用 MySQL | `127.0.0.1:3307` |
| 管理员演示账号 | `admin` / `Admin@12345`，使用管理员登录入口。 |
| 业务人员演示账号 | `staff` / `Staff@12345`，使用管理员登录入口。 |
| 客户账号 | 使用客户登录入口，自主注册或由管理员关联已有客户档案。 |

演示账号由空库初始化生成，已被修改的密码不会因重启重置。管理员和业务人员具有相同管理权限；客户仅访问本人资料、订单和收退款。不要把默认账号直接用于公网环境。

### MySQL 配置

**已有本机专用实例：**检测到 `data/mysql/local.json` 时，启动器自动使用项目专用 MySQL。该配置含本机随机密码，不能上传仓库或公开展示。

```powershell
python scripts/local_mysql.py start
python scripts/local_mysql.py status
python scripts/local_mysql.py stop
```

上述脚本面向 Windows，要求 `.tools/mysql-8.4.13-winx64/bin/` 中已有 MySQL 工具，使用本机 3307 端口，不安装系统服务、不设置开机启动。停止数据库前先停止项目后端。

**连接自己的 MySQL：**先创建空数据库 `yixing`，给专用账号授予该库所需权限，然后在同一个终端配置并启动：

```powershell
$env:MYSQL_URL = 'jdbc:mysql://127.0.0.1:3306/yixing?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:MYSQL_USER = '数据库账号'
$env:MYSQL_PASSWORD = '数据库密码'
python scripts/run.py --mysql --open
```

后端读取 `backend/src/main/resources/application-mysql.properties` 和 `schema-mysql.sql`。环境变量只作用于当前终端及其子进程，不应把真实密码写进源码。

**兼容模式：**代码仍保留 H2，没有本机 MySQL 配置且未传 `--mysql` 时会使用 `data/yixing.mv.db`；`--h2` 可显式选择它。两种数据库的数据不会自动同步，切换前必须停止已有前后端。项目内的迁移脚本供已有 H2 数据迁往本机 MySQL 使用，不要对已有正式数据随意重复执行。

<a id="usage"></a>

## 使用与操作说明

### 管理端功能

| 页面 | 可以做什么 | 操作注意 |
| --- | --- | --- |
| 工作台 | 查看经营概览、今日交还车、业务待办、车队状态和收入趋势。 | 待办来自真实订单和工单，点击后进入对应业务办理。 |
| 车辆信息 | 新增、编辑、查询车辆，上传照片，切换卡片/列表，查看历史及导出 CSV。 | 门店与出租、维修等状态通过业务操作更新，不是任意手动改状态。 |
| 客户管理 | 建档、编辑资料、检查驾驶证和限制租赁状态，查看历史订单。 | 客户档案不等于登录账号，需另行关联账号或使用客户注册。 |
| 租赁订单 | 创建预约、登记收款、交车、还车、补款/退款、结算和取消。 | 按订单当前状态显示可用操作，所有收退款先在线下确认。 |
| 租金计算 | 选择车辆，按天、按里程或包月试算费用。 | 试算不会预留车辆；实际还车按订单价格快照和实际用量计费。 |
| 车辆调度 | 查看门店车辆分布，创建、开始、完成或取消调度。 | 有后续预约时先处理订单，完成调度才更新车辆所属门店。 |
| 维修保养 | 查看提醒，创建、开始、完成或取消工单，登记费用和下次保养条件。 | 未开工的工单仍保留提醒；有损坏的还车会自动生成检修工单。 |
| 统计报表 | 按日期查看收入、订单和维修费用，切换车辆使用排行并导出。 | 收入按已结算订单的结算日期统计，押金不算收入。 |
| 系统管理 | 管理账号、门店、保养提醒参数、日志和数据备份恢复。 | 管理员与业务人员权限相同，账号类型用于区分人员身份。 |

### 一笔租赁订单怎么办理

1. **准备档案：**在客户管理中确认姓名、证件、驾驶证有效期和租赁限制；在车辆信息中确认车辆、门店及可预约状态。
2. **创建预约：**在租赁订单中新建，选择客户、车辆、计费方式、取还车时间和门店，核对预计租金后保存。
3. **登记收款：**打开订单详情，选择押金或预收租金，填写已在线下收到的金额。押金需要收齐后才能交车。
4. **办理交车：**在计划取车前 10 分钟至计划还车时间内，确认车辆已在取车门店且可交付，登记起始里程和交车车况。
5. **办理还车：**登记结束里程、还车门店、实际车况、附加费用及优惠。结束里程不能低于起始里程，附加费用需要说明。
6. **确认结算：**系统计算实际费用与净已收款的差额。在线下完成补款或退款后执行结算，生成对应流水。
7. **核对结果：**查看订单完成状态、车辆里程与门店、收退款记录，以及报表中的已结算收入。

| 订单状态 | 含义 | 下一步 |
| --- | --- | --- |
| `RESERVED` | 待取车，预约已保存但尚未交车。 | 登记收款、办理交车；未收款时可修改预约，也可取消。 |
| `ACTIVE` | 已交车，正在租赁。 | 办理还车，必要时登记剩余预收款。 |
| `RETURNED` | 已还车，等待结算。 | 确认补款或退款后结算。 |
| `COMPLETED` | 已结算完成。 | 查看历史和流水，不再修改租赁过程。 |
| `CANCELLED` | 预约已取消。 | 查看取消及退款记录。 |

### 客户端怎么使用

1. 在登录页切到“客户登录”，首次使用点击注册，填写账号、联系方式、证件和驾驶证信息。
2. 在选车出行中筛选车型，选择租期、计费方式、预计里程和还车门店，试算后提交预约。
3. 在我的订单中查看本人预约、订单进度和收退款记录。待取车且净收款为零的订单可以自行取消。
4. 已有收款的预约需联系管理端确认退款并取消；交还车、收款和结算也由管理端处理。
5. 在个人资料中修改姓名、电话和驾照资料。客户不能更改自己的角色、会员等级、身份证号或租赁限制。
6. 不再使用账号时，进入个人资料底部的“注销账号”，输入当前密码并勾选确认。待取车、租赁中或待结算订单需先处理完成；注销只删除登录账号，客户档案、历史订单和收退款仍然保留。重新开通请联系管理员，不要重复注册已有证件。

### 调度、养护和系统管理

| 操作 | 办理步骤 |
| --- | --- |
| 调度车辆 | 创建任务，选择车辆、目标门店、负责人和时间；确认无预约冲突后开始，到达后完成任务。 |
| 维修保养 | 创建工单，选择车辆、维修或保养项目及负责人；开工后车辆进入维修状态，完工时登记实际费用和下次保养条件。 |
| 新增登录账号 | 系统管理 → 账号与权限 → 新增账号，选择管理员、业务人员或客户。新增客户可同时填写联系方式和驾照资料建档，也可关闭“同时新建客户档案”，关联已有的唯一客户档案。 |
| 编辑客户账号 | 在账号列表搜索姓名或账号，编辑登录名、姓名、启用状态，或重置密码。客户档案不可重新绑定到另一个人；电话、驾照等资料在客户管理修改。 |
| 删除登录账号 | 在账号行点击删除，输入该登录账号确认。不能删除当前账号，也不能删除、禁用或降级最后一个启用的管理员；有未完成订单的客户需先处理订单。只删除登录身份，保留历史业务。 |
| 调整提醒 | 在系统参数中设置提前提醒天数和里程，任一条件满足即可提醒。 |
| 修改密码 | 从用户菜单进入修改密码，保存后需要重新登录；管理员也可在账号编辑中重置其他账号密码，旧密码对应的会话会失效。 |
| 导出备份 | 系统管理 → 数据备份与恢复 → 下载 ZIP，妥善保管数据库记录和上传图片。 |
| 恢复备份 | 先确认已保存当前数据备份，再选择可信 ZIP，输入 `RESTORE` 确认。恢复会替换当前数据，并使全部会话失效。 |

### 计费和业务规则

| 规则 | 实际含义 |
| --- | --- |
| 按天 | 每 24 小时算一天，不足一天向上取整，最低一天。 |
| 按里程 | 起租费 + 实际行驶公里数 × 公里单价。 |
| 包月 | 每 30 天按一个计费月计算，剩余天数按日租价；不是按自然月结算。 |
| 最终费用 | 基础租金 + 附加费用 − 优惠。 |
| 净已收款 | 全部收款 − 全部退款，包含已收押金；与最终费用比较后补收或退差额。 |
| 价格快照 | 预约保存时复制价格参数，之后修改价目表不追溯修改该订单。 |
| 租期冲突 | 同一车辆的有效预约不能时间重叠；相邻预约可以存在，但前一单实际归还后才能交付下一单。 |
| 驾驶资格 | 驾照过期、不能覆盖租期或被限制租赁时不能下单；交车时还会重新核验。 |
| 车辆状态 | 预约本身不把车辆改为出租中；交车、归还、调度和维修驱动状态变化。 |
| 已收款预约 | 不直接修改价格和预约条件，需要取消退款后重新创建。 |
| 业务时间 | 实际交车、还车和结算时间由后端记录，不接受随意填写的历史时间。 |

<a id="files"></a>

## 详细文件说明

### 路径怎么找

下表路径都从**项目根目录**开始。例如 `backend/src/main/java/com/yixing/order/RentalService.java`，表示依次打开 `backend → src → main → java → com → yixing → order` 文件夹，再打开 `RentalService.java`。

在 VS Code 中打开整个项目文件夹，不要只打开 `backend` 或 `frontend` 中的某个单独文件。下文文件链接可以直接点击；根目录文件夹叫什么不影响这些相对路径。

```text
项目根目录/
|-- README.md                          项目统一说明
|-- start.cmd                          Windows 启动入口
|-- 易行租车启动器.exe                  桌面启动器
|-- backend/
|   |-- pom.xml                        Java 依赖、测试和构建配置
|   `-- src/
|       |-- main/
|       |   |-- java/com/yixing/        后端接口、业务和数据访问
|       |   `-- resources/             应用配置和建表脚本
|       `-- test/java/com/yixing/      后端自动化测试
|-- frontend/
|   |-- index.html                     浏览器入口
|   |-- package.json                   前端依赖和命令
|   |-- package-lock.json              依赖版本锁文件
|   |-- vite.config.js                 开发、预览与接口代理
|   |-- src/
|   |   |-- views/                     业务页面
|   |   |-- components/                公共界面组件
|   |   `-- *.js、*.css                公共逻辑和样式
|   `-- public/images/                 摄影图片与车型参考图
|-- scripts/                           启动、数据库、构建辅助脚本
|-- tests/                             浏览器验收和启动器测试
|-- data/                              本地运行数据，按需生成
|-- uploads/                           上传照片目录，按需生成
`-- .tools/                            可选的本机开发工具
```

**后端**决定业务规则、权限和数据保存；**前端**处理页面交互和请求；**UI**是前端的一部分，主要由 Vue 模板、组件、CSS 和图片组成，没有独立的 UI 工程。

### 后端：启动与接口

| 完整文件路径 | 在项目中的作用 |
| --- | --- |
| [backend/src/main/java/com/yixing/Application.java](backend/src/main/java/com/yixing/Application.java) | 启动 Spring Boot，装配后端服务。 |
| [backend/src/main/java/com/yixing/web/ApiController.java](backend/src/main/java/com/yixing/web/ApiController.java) | 管理端接口入口，把车辆、客户、订单、调度等请求交给业务服务。 |
| [backend/src/main/java/com/yixing/customer/PortalController.java](backend/src/main/java/com/yixing/customer/PortalController.java) | 客户端接口，限定本人资料、预约和取消操作，不接受前端指定客户归属。 |
| [backend/src/main/java/com/yixing/auth/AuthController.java](backend/src/main/java/com/yixing/auth/AuthController.java) | 登录、退出、获取当前身份和修改密码。 |
| [backend/src/main/java/com/yixing/web/FileController.java](backend/src/main/java/com/yixing/web/FileController.java) | 校验并保存上传图片、登录后读取图片，以及备份下载与恢复上传。 |

### 后端：核心业务

| 完整文件路径 | 在项目中的作用 |
| --- | --- |
| [backend/src/main/java/com/yixing/order/RentalService.java](backend/src/main/java/com/yixing/order/RentalService.java) | 租赁核心：预约冲突、价格快照、收款、交车、还车、结算、取消；使用事务和行锁协调并发操作。 |
| [backend/src/main/java/com/yixing/pricing/PricingService.java](backend/src/main/java/com/yixing/pricing/PricingService.java) | 统一计算日租、里程和包月费用，预约试算和实际还车复用同一计算逻辑。 |
| [backend/src/main/java/com/yixing/vehicle/VehicleService.java](backend/src/main/java/com/yixing/vehicle/VehicleService.java) | 车辆档案保存、启停和图片地址校验，限制直接修改业务状态、门店和里程。 |
| [backend/src/main/java/com/yixing/customer/CustomerService.java](backend/src/main/java/com/yixing/customer/CustomerService.java) | 客户资料保存，以及驾驶证有效期和租赁限制检查。 |
| [backend/src/main/java/com/yixing/dispatch/DispatchService.java](backend/src/main/java/com/yixing/dispatch/DispatchService.java) | 调度任务创建、开始、完成和取消，处理预约冲突和门店变化。 |
| [backend/src/main/java/com/yixing/maintenance/MaintenanceService.java](backend/src/main/java/com/yixing/maintenance/MaintenanceService.java) | 维修保养工单、开工完工、车辆状态与日期/里程提醒。 |
| [backend/src/main/java/com/yixing/report/ReportService.java](backend/src/main/java/com/yixing/report/ReportService.java) | 汇总已结算收入、订单、维修费用和车辆使用数据。 |
| [backend/src/main/java/com/yixing/system/SystemService.java](backend/src/main/java/com/yixing/system/SystemService.java) | 账号、门店、价格参数、提醒设置、日志及数据备份恢复。 |

接口方法主要负责接收请求，具体业务判断在 `Service` 中。修改预约或结算规则时先找 `RentalService.java`，而不是只改前端按钮。

### 后端：权限、公共校验与数据访问

| 完整文件路径 | 在项目中的作用 |
| --- | --- |
| [backend/src/main/java/com/yixing/auth/SessionFilter.java](backend/src/main/java/com/yixing/auth/SessionFilter.java) | 进入接口前校验会话、账号状态、来源和权限；客户端不能借地址访问管理端。 |
| [backend/src/main/java/com/yixing/auth/Access.java](backend/src/main/java/com/yixing/auth/Access.java) | 获取当前操作人、检查管理员身份和记录操作日志。 |
| [backend/src/main/java/com/yixing/auth/AccountService.java](backend/src/main/java/com/yixing/auth/AccountService.java) | 客户注册与注销、账号删除、重复检查、未完成订单拦截和最后一个管理员保护；账号事务与客户下单共用事务锁，避免注销和预约互相穿过校验。 |
| [backend/src/main/java/com/yixing/auth/AccountUpgrade.java](backend/src/main/java/com/yixing/auth/AccountUpgrade.java) | 启动时兼容旧账号结构和旧业务人员权限。 |
| [backend/src/main/java/com/yixing/common/Values.java](backend/src/main/java/com/yixing/common/Values.java) | 统一读取、转换和校验字符串、金额、整数与日期。 |
| [backend/src/main/java/com/yixing/common/BusinessException.java](backend/src/main/java/com/yixing/common/BusinessException.java) | 业务条件不满足时中止操作，如租期冲突、押金不足。 |
| [backend/src/main/java/com/yixing/common/ApiErrors.java](backend/src/main/java/com/yixing/common/ApiErrors.java) | 将异常转换成前端可显示的状态码和消息。 |
| [backend/src/main/java/com/yixing/data/Store.java](backend/src/main/java/com/yixing/data/Store.java) | 数据访问入口，提供查询、新增、更新和行锁。 |
| [backend/src/main/java/com/yixing/data/DataMapper.java](backend/src/main/java/com/yixing/data/DataMapper.java) | MyBatis 映射，执行 SQL 查询和写入。 |
| [backend/src/main/java/com/yixing/data/Statements.java](backend/src/main/java/com/yixing/data/Statements.java) | 按字段生成新增、修改 SQL，实际值使用参数绑定。 |
| [backend/src/main/java/com/yixing/data/DemoData.java](backend/src/main/java/com/yixing/data/DemoData.java) | 空库初始化虚构演示账号、车辆、客户、门店和历史业务数据，不用于重置已有数据库。 |

源码目录 `backend/src/main/java/com/yixing/data/` 与根目录 `data/` 不是同一个目录：前者是 Java 代码，后者保存本地运行数据。

### 后端：配置和数据库

| 完整文件路径 | 在项目中的作用 |
| --- | --- |
| [backend/pom.xml](backend/pom.xml) | Java 版本、Spring Boot/MyBatis/数据库依赖、测试和 Maven 构建配置。 |
| [backend/src/main/resources/application.properties](backend/src/main/resources/application.properties) | 基础端口、会话、上传限制和兼容模式数据源。 |
| [backend/src/main/resources/application-mysql.properties](backend/src/main/resources/application-mysql.properties) | MySQL 连接配置及环境变量入口，指定 MySQL 建表脚本。 |
| [backend/src/main/resources/schema-mysql.sql](backend/src/main/resources/schema-mysql.sql) | MySQL 实际加载的表结构脚本，修改 MySQL 表结构时检查这里。 |
| [backend/src/main/resources/schema.sql](backend/src/main/resources/schema.sql) | 保留的 H2 表结构脚本，供兼容运行和默认隔离测试使用。 |

### 前端：入口和公共逻辑

| 完整文件路径 | 在项目中的作用 |
| --- | --- |
| [frontend/index.html](frontend/index.html) | 浏览器入口、网页标题和 Vue 挂载节点。 |
| [frontend/src/main.js](frontend/src/main.js) | 启动 Vue、配置路由和登录跳转、导入组件库和全局样式。 |
| [frontend/src/App.vue](frontend/src/App.vue) | 整体页面外壳、侧栏、顶栏、用户菜单和初始业务数据加载。 |
| [frontend/src/api.js](frontend/src/api.js) | Axios 请求封装、错误提示、会话过期通知、文件下载和 CSV 导出。 |
| [frontend/src/store.js](frontend/src/store.js) | 各页面共享的响应式业务数据、刷新方法、状态名称和日期金额显示。 |
| [frontend/src/dashboard.js](frontend/src/dashboard.js) | 工作台日期范围、今日交还车、车辆分类、待办排序等纯逻辑。 |
| [frontend/vite.config.js](frontend/vite.config.js) | 开发和预览端口，`/api` 与 `/uploads` 的后端代理，以及构建分包。 |
| [frontend/package.json](frontend/package.json) | 前端依赖及开发、测试、构建、预览命令。 |
| [frontend/package-lock.json](frontend/package-lock.json) | 锁定依赖版本，配合 `npm ci` 重现安装。 |

### 前端：各功能页面

| 页面 | 完整文件路径 | 主要内容 |
| --- | --- | --- |
| 登录注册 | [frontend/src/views/Login.vue](frontend/src/views/Login.vue) | 管理端/客户端切换、登录、客户注册及登录页局部样式。 |
| 工作台 | [frontend/src/views/Dashboard.vue](frontend/src/views/Dashboard.vue) | 经营概览、今日交还车、业务待办、状态与收入图表。 |
| 车辆管理 | [frontend/src/views/Vehicles.vue](frontend/src/views/Vehicles.vue) | 车辆卡片/列表、筛选、建档、编辑、详情和启停。 |
| 客户管理 | [frontend/src/views/Customers.vue](frontend/src/views/Customers.vue) | 客户建档、资料修改、租赁限制和历史记录。 |
| 租赁订单 | [frontend/src/views/Orders.vue](frontend/src/views/Orders.vue) | 预约表单、报价、订单详情和收款、交还车、结算操作。 |
| 租金试算 | [frontend/src/views/Pricing.vue](frontend/src/views/Pricing.vue) | 三种计费输入切换和后端试算结果展示。 |
| 车辆调度 | [frontend/src/views/Dispatch.vue](frontend/src/views/Dispatch.vue) | 门店运力、调度创建、开始、到达和取消。 |
| 维修保养 | [frontend/src/views/Maintenance.vue](frontend/src/views/Maintenance.vue) | 提醒、工单、新建和完工登记。 |
| 统计报表 | [frontend/src/views/Reports.vue](frontend/src/views/Reports.vue) | 日期查询、趋势、车辆排行和导出。 |
| 系统管理 | [frontend/src/views/Settings.vue](frontend/src/views/Settings.vue) | 账号、门店、参数、日志和备份恢复。 |
| 客户端 | [frontend/src/views/CustomerPortal.vue](frontend/src/views/CustomerPortal.vue) | 选车出行、我的订单和个人资料，根据路由切换视图。 |

### UI：样式与公共组件

| 完整文件路径 | 主要控制什么 |
| --- | --- |
| [frontend/src/styles.css](frontend/src/styles.css) | 页面外壳、表格、表单、卡片和小屏布局等基础规则。 |
| [frontend/src/workspace.css](frontend/src/workspace.css) | 工作空间的字号、间距、控件和业务区域布局。 |
| [frontend/src/refinement.css](frontend/src/refinement.css) | 当前主题、侧栏、字体、指标数字和表格等视觉细化。 |
| [frontend/src/components/FormDialog.vue](frontend/src/components/FormDialog.vue) | 按字段配置生成共用表单弹窗，支持输入、下拉、日期、开关和图片上传。 |
| [frontend/src/components/ChartPanel.vue](frontend/src/components/ChartPanel.vue) | ECharts 创建、尺寸调整、更新和销毁。图表数据配置在工作台和报表页面。 |
| [frontend/src/components/StatusTag.vue](frontend/src/components/StatusTag.vue) | 车辆、订单、任务等状态的中文名称和颜色。 |
| [frontend/src/components/VehicleIdentity.vue](frontend/src/components/VehicleIdentity.vue) | 组合车辆缩略图、品牌车型和车牌，统一各页面的车辆信息。 |
| [frontend/src/components/VehicleThumbnail.vue](frontend/src/components/VehicleThumbnail.vue) | 图片显示、参考图标记、加载失败回退与示意车牌遮挡。 |

全局样式导入顺序为 Element Plus 样式 → `styles.css` → `workspace.css` → `refinement.css`。后面的同等优先级规则覆盖前面的规则，还需要考虑选择器、局部样式和 `!important`。

修改全站外观先查 `frontend/src/refinement.css`；修改某页字段和按钮先查对应 `frontend/src/views/` 文件；修改统一弹窗查 `frontend/src/components/FormDialog.vue`。界面与导航适配小屏，颜色不仅用于装饰，也用于区分状态；不要只改某一层 CSS 就认定最终显示一定已变更。

### 图片及素材映射

| 完整文件路径 | 在项目中的作用 |
| --- | --- |
| [frontend/public/images/rental-forest.webp](frontend/public/images/rental-forest.webp) | 登录页、侧栏等位置的摄影图片。 |
| [frontend/public/images/rental-road.webp](frontend/public/images/rental-road.webp) | 客户端欢迎区域图片。 |
| [frontend/public/images/vehicles/](frontend/public/images/vehicles/) | 18 款车型参考图，不等同于每辆库存车的实拍照片。 |
| [frontend/src/vehiclePhotoCatalog.js](frontend/src/vehiclePhotoCatalog.js) | 车型、别名、图片地址和来源元数据清单。 |
| [frontend/src/vehiclePhotos.js](frontend/src/vehiclePhotos.js) | 图片选择顺序：档案上传照片优先，车型参考图作为后备。 |
| [frontend/src/vehiclePlateShields.js](frontend/src/vehiclePlateShields.js) | 参考图中示意车牌的遮挡区域坐标，只改变展示图层。 |

完整摄影署名、车型原图地址和版权边界合并在本文末尾的[素材来源](#assets)，不要在清理文件时一并删去。

### 启动与辅助脚本

| 完整文件路径 | 在项目中的作用 |
| --- | --- |
| [start.cmd](start.cmd) | Windows 桌面启动器入口。 |
| [scripts/Launcher.cs](scripts/Launcher.cs) | 原生桌面启动器源码，负责按钮、日志和状态显示。 |
| [scripts/build-launcher.ps1](scripts/build-launcher.ps1) | 用 Windows 内置 .NET Framework 编译器重新生成根目录 EXE。 |
| [scripts/run.py](scripts/run.py) | 检查环境、按需构建、启动前后端、识别就绪状态，只停止本次创建的进程。 |
| [scripts/local_mysql.py](scripts/local_mysql.py) | 管理项目专用本机 MySQL、私有密码配置和数据库连接。 |
| [scripts/migrate_local_mysql.py](scripts/migrate_local_mysql.py) | 将已有本地 H2 数据迁往项目 MySQL，并保留核对和备份资料；不是日常启动步骤。 |
| [scripts/test_mysql.py](scripts/test_mysql.py) | 使用独立 MySQL 测试数据库构建并执行后端测试，可选追加浏览器验收。 |
| [scripts/vscode.ps1](scripts/vscode.ps1) | 为 VS Code 查找并准备项目的 Java、Maven 运行环境。 |

### 测试文件

| 完整文件路径 | 覆盖内容 |
| --- | --- |
| [backend/src/test/java/com/yixing/PricingTest.java](backend/src/test/java/com/yixing/PricingTest.java) | 计费公式及边界。 |
| [backend/src/test/java/com/yixing/RentalWorkflowTest.java](backend/src/test/java/com/yixing/RentalWorkflowTest.java) | 租赁闭环、冲突、收退款及关联业务。 |
| [backend/src/test/java/com/yixing/CustomerPortalTest.java](backend/src/test/java/com/yixing/CustomerPortalTest.java) | 客户注册、本人数据隔离、预约和权限。 |
| [backend/src/test/java/com/yixing/AccountLifecycleTest.java](backend/src/test/java/com/yixing/AccountLifecycleTest.java) | 三类账号创建编辑删除、客户注销、密码及会话、事务回滚、档案与历史记录保留、最后一个管理员保护。 |
| [frontend/src/dashboard.test.js](frontend/src/dashboard.test.js) | 待办与车辆分类逻辑、车型参考图和图片回退。 |
| [tests/test_launcher.py](tests/test_launcher.py) | 启动器环境、服务复用、端口冲突、构建和停止逻辑的单元测试。 |
| [tests/browser_acceptance.py](tests/browser_acceptance.py) | 启动隔离服务，验收八个业务模块和租赁流程。 |
| [tests/dashboard_acceptance.py](tests/dashboard_acceptance.py) | 浏览器验收中的工作台检查。 |
| [tests/order_photo_acceptance.py](tests/order_photo_acceptance.py) | 订单中车辆图片展示检查。 |
| [tests/vehicle_photo_acceptance.py](tests/vehicle_photo_acceptance.py) | 车型图片与回退检查。 |
| [tests/revision_acceptance.py](tests/revision_acceptance.py) | 客户端及调整后的业务与权限验收。 |
| [tests/account_acceptance.py](tests/account_acceptance.py) | 实际操作三类账号的新增、编辑、删除及客户注销，检查桌面和手机弹窗，并由浏览器验收入口调用。 |
| [tests/check_preserved_records.py](tests/check_preserved_records.py) | 与旧数据库基线核对业务记录，仅在基线存在时使用。 |
| [tests/launcher_cold_start.py](tests/launcher_cold_start.py) | 在隔离端口检查真实前后端冷启动；脚本还要求原项目服务已运行。 |
| [tests/launcher_window_preview.py](tests/launcher_window_preview.py) | Windows 启动器窗口和服务复用检查，截图写入本地测试产物目录。 |
| [tests/refresh_screenshots.py](tests/refresh_screenshots.py) | 只读浏览当前管理页面，生成截图及清单，不创建业务记录。 |
| [tests/ui_preview.py](tests/ui_preview.py) | 管理页面、移动端、交互和截图检查。 |
| [tests/revision_preview.py](tests/revision_preview.py) | 客户注册界面、报表等页面预览检查。 |
| [tests/color_preview.py](tests/color_preview.py) | 色彩和页面展示检查。 |
| [tests/sidebar_scroll_preview.py](tests/sidebar_scroll_preview.py) | 侧栏滚动条和不同高度窗口检查。 |
| [tests/requirements.txt](tests/requirements.txt) | 浏览器和图片测试所需 Python 依赖。 |

部分窗口和预览脚本依赖原开发电脑的 Edge 路径及已运行服务，不能把它们视作任意电脑上开箱即用的单元测试。

### 本地数据目录：哪些可以清理

| 路径 | 内容与清理原则 |
| --- | --- |
| `data/mysql/db/` | MySQL 实际数据文件；不能当缓存删除，也不要在运行中移动。 |
| `data/mysql/local.json` | 本机数据库私有配置；删除可能导致已有数据库无法按原凭据连接。 |
| `data/yixing.mv.db` | 保留的 H2 数据；切换数据库不代表可以直接丢弃原数据。 |
| `data/runtime/` | 启动日志、锁文件，以及可能保留的业务核对基线。服务停止后可清理确认无用的旧日志，不能整目录盲删。 |
| `data/test-artifacts/` | 浏览器截图、录屏和结果；不参与业务运行，确认不需要验收证据后可清理。 |
| `uploads/` | 车辆上传照片，上传或恢复备份时自动创建；有文件时需先备份，不能因为“不属于源码”就删除。 |
| `.tools/` | 本机 JDK、Maven、MySQL 工具，不随仓库分发，删除后要重新配置。 |
| `frontend/node_modules/` | 前端依赖，可以通过 `npm ci` 重装。 |
| `backend/target/`、`frontend/dist/` | 后端 JAR 和前端构建结果，可重新生成，但直接启动需要这些产物。 |
| `tmp/`、`__pycache__/` | 临时研究、预览或 Python 缓存，不属于业务数据；确认无未保存资料后可清理，运行时可能重新生成缓存。 |

不要提交数据库、上传照片、本地密码配置、依赖、构建产物和测试录屏。源码、依赖锁文件、建表脚本和真实引用的前端素材需要保留。

<a id="api"></a>

## 接口与数据约定

### 通用约定

- 后端地址为 `http://127.0.0.1:8080`，Vite 代理 `/api` 和 `/uploads`。同源访问时由浏览器携带登录会话。
- 登录使用 HttpOnly Cookie；浏览器写请求发送 `X-Requested-With: XMLHttpRequest`，后端检查允许的来源。
- JSON 使用下划线字段，例如 `vehicle_id`、`planned_start`。日期为 `YYYY-MM-DD`，时间为本地 ISO 日期时间，运行环境需要采用一致时区。
- 金额最多两位小数，后端使用 `BigDecimal` 与数据库 `DECIMAL`。成功返回对象或集合，业务失败返回 `message`。
- 未登录通常返回 401，权限不足返回 403，业务校验失败返回 400。
- 列表主要由 `bootstrap` 接口加载，筛选和分页在前端完成，不是服务端分页。

### 主要接口

| 模块 | 方法与路径 | 用途 |
| --- | --- | --- |
| 认证 | `POST /api/auth/login` | 传 `username`、`password`、`portal`（`ADMIN`/`CUSTOMER`）登录。 |
| 认证 | `POST /api/auth/register` | 仅客户注册，传账号、姓名、电话、证件和驾照资料。 |
| 认证 | `GET /api/auth/me`、`POST /api/auth/logout` | 查询登录身份、退出。 |
| 认证 | `POST /api/auth/password` | 传 `old_password`、`new_password`，修改后退出。 |
| 认证 | `DELETE /api/auth/account` | 客户传当前 `password` 和 `confirmation=CLOSE` 注销本人账号，未完成订单会拦截；其他登录会话失效。 |
| 管理端数据 | `GET /api/bootstrap`、`GET /api/dashboard` | 业务基础数据和工作台统计。 |
| 图片 | `POST /api/uploads`、`GET /uploads/{name}` | 上传表单字段 `file`，只接收 5 MB 以内有效 PNG/JPEG；读取图片需要登录。 |
| 车辆 | `POST /api/vehicles`、`PUT /api/vehicles/{id}` | 新增、编辑车辆档案。 |
| 车辆 | `POST /api/vehicles/{id}/toggle` | 启用或停用，检查关联业务。 |
| 客户 | `POST /api/customers`、`PUT /api/customers/{id}` | 新增、编辑客户档案。 |
| 报价 | `POST /api/pricing/quote` | 服务端计算租金、押金和公式，不占用车辆。 |
| 预约 | `POST /api/orders`、`PUT /api/orders/{id}` | 创建或修改未收款待取车预约。 |
| 订单办理 | `POST /api/orders/{id}/{action}` | `action` 为 `collect`、`pickup`、`return`、`settle`、`cancel`。 |
| 调度 | `POST /api/dispatch`、`POST /api/dispatch/{id}/{action}` | 创建调度；操作为 `start`、`complete`、`cancel`。 |
| 养护 | `POST /api/maintenance`、`POST /api/maintenance/{id}/{action}` | 创建工单；操作为 `start`、`complete`、`cancel`。 |
| 报表 | `GET /api/reports?from=YYYY-MM-DD&to=YYYY-MM-DD` | 区间最多 367 天。 |
| 系统 | `GET /api/system` | 账号、系统参数和最近 300 条日志。 |
| 账号 | `POST /api/system/users`、`PUT /api/system/users/{id}` | 创建、编辑三类账号；编辑时密码留空不修改。客户新建可不传 `customer_id`，同时传姓名、电话、证件和驾照字段建档；有编号时关联已有档案。 |
| 账号 | `DELETE /api/system/users/{id}` | 管理端传 `confirmation=DELETE` 删除登录账号，校验当前账号、最后一个管理员和客户未完成订单；保留客户档案及历史业务。 |
| 门店 | `POST /api/system/branches`、`PUT /api/system/branches/{id}` | 创建、编辑门店。 |
| 参数 | `PUT /api/system/settings` | 设置 `reminder_days`、`reminder_km`。 |
| 计费参数 | `PUT /api/system/pricing/{id}` | 后端保留价格规则维护接口，不追溯改变已有订单快照。 |
| 备份 | `GET /api/system/backup`、`POST /api/system/restore` | 下载快照；恢复上传 `file` 和 `confirmation=RESTORE`。 |
| 客户端 | `GET /api/portal/bootstrap`、`PUT /api/portal/profile` | 本人档案、订单和可修改的个人资料。 |
| 客户端 | `POST /api/portal/quote`、`POST /api/portal/orders` | 本人报价和预约，客户编号由会话决定。 |
| 客户端 | `POST /api/portal/orders/{id}/cancel` | 仅取消本人待取车、净收款为零的预约。 |

### 预约与办理字段

| 请求 | 主要字段 |
| --- | --- |
| 报价 | `vehicle_id`、`billing_mode`、`planned_start`、`planned_end`、`expected_mileage`。 |
| 管理端预约 | 报价字段，加 `customer_id`、`pickup_branch`、`return_branch`。 |
| 客户端预约 | 报价字段，加 `return_branch`；客户身份和取车店由后端确定。 |
| 收款 | `request_id`、`kind`（`DEPOSIT`/`RENT`）、`amount`。 |
| 交车 | `start_mileage`、`condition`。 |
| 还车 | `end_mileage`、`return_branch`、`extra_fee`、`discount`、`fee_notes`、`condition`、`damage`。 |
| 管理端结算与取消 | `request_id`。客户端取消的请求号由后端生成。 |

收款、结算和取消使用独立请求号防止重复登记。预约保存、交还车、调度和维修更新同一车辆时通过事务与行锁协调，不依赖前端按钮禁用来保证正确性。

### 主要数据表

| 表名 | 在项目中的作用 |
| --- | --- |
| `sys_user` | 登录账号、角色、账号类型和关联客户编号。 |
| `customer` | 客户资料、驾照有效期和租赁限制。 |
| `vehicle` | 车辆档案、里程、门店、状态、图片和价格规则关联。 |
| `branch` | 取还车与调度使用的门店。 |
| `pricing_rule` | 日租、月租、公里单价、起租费和押金参数。 |
| `rental_order` | 预约、租期、实际交还车、价格快照和结算结果。 |
| `payment_record` | 线下收退款流水及请求号，不是支付平台交易。 |
| `dispatch_task` | 车辆调度的时间、门店、负责人和状态。 |
| `maintenance_record` | 维修保养项目、费用和完成情况。 |
| `settings` | 保养提前提醒的天数和里程。 |
| `operation_log` | 操作人、关键业务动作、对象与时间。 |

完整字段以 `backend/src/main/resources/schema-mysql.sql` 为准。建表脚本不是任意版本的自动迁移工具，修改已有数据库结构前需要备份并核对数据兼容性。

<a id="development"></a>

## 开发与验证

### 常用修改位置

| 想修改什么 | 优先查看 |
| --- | --- |
| 预约、交还车、押金、结算规则 | `backend/src/main/java/com/yixing/order/RentalService.java` 和 `frontend/src/views/Orders.vue`。 |
| 计费公式和试算输入 | `backend/src/main/java/com/yixing/pricing/PricingService.java` 和 `frontend/src/views/Pricing.vue`。 |
| 客户数据隔离 | `backend/src/main/java/com/yixing/auth/SessionFilter.java`、`backend/src/main/java/com/yixing/customer/PortalController.java`。 |
| 登录与导航 | `backend/src/main/java/com/yixing/auth/AuthController.java`、`frontend/src/main.js`、`frontend/src/App.vue`、`frontend/src/views/Login.vue`。 |
| 全站颜色、字体和侧栏 | `frontend/src/refinement.css`，结合另外两层 CSS 检查覆盖。 |
| 某页字段与布局 | `frontend/src/views/` 中对应 `.vue`；公共弹窗在 `frontend/src/components/FormDialog.vue`。 |
| 工作台与统计口径 | `frontend/src/views/Dashboard.vue`、`frontend/src/views/Reports.vue` 和 `backend/src/main/java/com/yixing/report/ReportService.java`。 |
| 上传和车型图片 | `backend/src/main/java/com/yixing/web/FileController.java`、`frontend/src/components/VehicleThumbnail.vue` 和图片映射脚本。 |
| 表结构和连接 | `backend/src/main/resources/schema-mysql.sql`、`application-mysql.properties`，本机实例工具在 `scripts/local_mysql.py`。 |

### 测试与构建命令

默认后端测试使用隔离内存数据库，不修改日常演示数据：

```powershell
mvn -f backend/pom.xml test
```

真实 MySQL 验证使用本机专用 MySQL，并为每轮测试创建独立测试库，不清空演示库：

```powershell
python scripts/test_mysql.py
```

该脚本依赖项目 `.tools/` 下的 JDK、Maven 和 MySQL。测试结果在 `data/mysql/test-results.json`，带时间标记的测试库会保留，不能误将它们当成演示库。

前端逻辑测试、启动脚本单元测试和生产构建：

```powershell
npm --prefix frontend test
python -B -m unittest discover -s tests -p test_launcher.py
npm --prefix frontend run build
```

浏览器验收需要额外依赖和浏览器：

```powershell
python -m pip install -r tests/requirements.txt
python -m playwright install chromium ffmpeg
python tests/browser_acceptance.py
```

普通浏览器验收使用隔离的 8081 后端、5174 前端和测试数据库，要求对应端口空闲、后端 JAR 已构建。截图、录屏和结果写入 `data/test-artifacts/browser/`。如需 MySQL 浏览器闭环，执行：

```powershell
python scripts/test_mysql.py --browser
```

MySQL 验收产物写入 `data/mysql/`。当前页面截图命令 `python tests/refresh_screenshots.py` 使用已运行服务，输出到 `data/test-artifacts/current-screenshots/`；Windows 窗口检查输出到 `data/test-artifacts/launcher-window/`。这些产物均不提交 Git。

### 重新生成桌面启动器

修改 `scripts/Launcher.cs` 后，在 Windows 项目根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-launcher.ps1
```

需要 Windows 内置 .NET Framework C# 编译器。输出仍为根目录 `易行租车启动器.exe`，不要求安装完整 Visual Studio。

### 提交检查

1. 从最新主分支创建具体任务分支，说明修改模块及所需环境、数据库变更。
2. 只提交当前任务相关文件，不提交数据库、上传照片、真实密码和本地工具。
3. 接口或表结构变化时，同步前端调用、本文说明和相关测试。
4. 执行覆盖改动范围的测试；页面有变化时保存实际运行截图作为评审依据。
5. 注明测试命令、结果和未验证项；“已经 push”不等于测试通过。
6. 提交后由另一位成员检查功能、异常情况与兼容性，再合并主分支。

<a id="faq"></a>

## 常见问题

| 现象 | 排查与处理 |
| --- | --- |
| 提示缺少 Java、Maven、Node 或 Python | 核对安装位置与 PATH；Java 需要 JDK 21。修改环境变量后重新打开终端或启动器。 |
| 8080/5173 被占用 | 先确认占用程序；本项目不会按端口强行关闭其他服务。已有项目服务会复用。 |
| 另一个启动器正在管理系统 | 使用原窗口停止服务，或等待启动结束，不要直接删除锁文件强行接管。 |
| 桌面启动器一直等待前端 | 已修复 GUI 停止指令管道被 Vite 等子进程共用的问题。更新 `scripts/run.py` 后重新打开启动器即可；超时信息会列出未就绪服务和对应的 `data/runtime/backend.log`、`frontend.log`。 |
| 前端报 `listen EACCES`，但端口没有监听进程 | Windows 可能保留了该端口。新版启动器会自动切换可用端口，以窗口中显示的地址为准；无需删除系统的端口保留规则。 |
| 修改代码后页面没变化 | 普通启动不强制重建。停止服务后运行 `python scripts/run.py --build --open`；只开发前端可使用 `--dev`。 |
| MySQL 连接失败 | 检查实例是否运行、端口、账号授权与环境变量；本机专用实例查看 `data/mysql/server.log`。不要公开密码配置。 |
| 换数据库后仍看到旧数据 | 先停止原前后端再切换；启动器可能复用了原后端，且两种数据库不会自动同步。 |
| 不能交车 | 检查押金是否收齐、是否到取车时间、上笔订单是否归还、车辆是否在取车店、驾照是否有效。 |
| 客户无法取消订单 | 只允许本人、待取车且净收款为零的预约自行取消；已收款需管理端确认退款。 |
| 图片不显示 | 检查登录会话、`uploads/` 中实际文件和车辆图片地址；参考图目录是前端素材，不是上传目录。 |
| 收退款金额与租金不同 | 押金计入净收款，但不计收入；结算按实际费用与净收款计算补款/退款。 |
| 测试提示没有 Playwright 或找不到浏览器 | 在实际运行测试的 Python 环境中安装依赖，并执行浏览器安装命令；普通启动不需要 Playwright。 |
| 首次依赖下载或构建失败 | 查看 `data/runtime/build.log`，确认网络和环境，然后重试。 |
| Windows 提示 EXE 未签名 | 该程序由仓库 C# 源码编译，先核对来源；不要关闭系统安全防护，可用 Python 入口或自行编译。 |

<a id="assets"></a>

## 素材与使用边界

本项目面向本地课程演示，数据为虚构样例。它不代表已完成生产安全审计、服务端大规模分页、真实支付、征信、地图调度或细粒度权限平台。部署到服务器前需单独验证网络、HTTPS、账号与数据库安全、备份和运维条件。

上传图片和备份可能包含敏感业务资料，只能导入可信备份并妥善保管。参考车型图不代表具体库存车辆的颜色、年款、配置或实车车况。

以下完整保留素材原有署名与来源记录，记录日期不是本次重新验收日期。具体图片来源元数据还可在 `frontend/src/vehiclePhotoCatalog.js` 查看。

<details>
<summary>摄影署名、18 款车型图片来源与版权边界</summary>

### 品牌摄影素材

两张照片仅用于登录页与工作台的品牌表达，不代表在租车辆、门店实景或客户交付记录。车辆照片与档案仍以业务库上传资料为准。

#### 公路汽车

- 摄影：Zsolt Joo，Pexels。
- 来源：https://www.pexels.com/photo/white-coupe-car-in-mountains-on-highway-5054158/
- 文件：`frontend/public/images/rental-road.webp`。下载原始公开照片后进行 WebP 压缩，不修改车辆标识或伪装业务记录。

#### 森林汽车

- 摄影：Danilo Marcelino，Pexels。
- 来源：https://www.pexels.com/photo/black-bmw-car-on-a-road-10507791/
- 文件：`frontend/public/images/rental-forest.webp`。缩放并转为 WebP，用于登录页。

#### 使用依据

- Pexels 许可：https://www.pexels.com/license/
- 照片允许下载、修改并用于网站。禁止暗示图中品牌、人物或作者背书，禁止将未修改照片单独转售。
- 本项目将素材本地化加载，运行时不依赖外部图片服务；保留来源与摄影师信息便于核查。
- 使用与核验日期：2026-10-28。


#### 统一视角透明车型图库（2026-10-28 更新）

按最新要求，18 款车型全部替换为车头朝左的前侧视角车型图，全部保留真实 Alpha 透明通道。原先侧面图、车头朝右图片和带场景背景图片不再使用。

来源为懂车帝与汽车之家各车型公开展示图片，不是 AI 生成图片。仅裁切透明留白、等比缩放并放入统一的 960 × 520 透明画布，统一画面位置和轮胎基线；不镜像、不改变车身、不移除原图平台标记。各车型原始拍摄俯仰和焦距存在细微差异，前侧视角与车头方向统一，不声称不同原图经过同一机位标定。

车辆卡片、列表、档案、首页待办、订单列表、订单详情及车辆选择器共享素材索引。车辆档案上传 `image` 仍优先，来源图片不写入业务库。页面显示“车型参考 / 参考图”，不代表特定车牌的实拍、颜色、年款和配置；库存品牌、车型、车牌、颜色等字段不修改。

完整来源、原图尺寸、处理方式及图片属性记录于 `frontend/src/vehiclePhotoCatalog.js`。

##### 大众 朗逸

- 文件：`frontend/public/images/vehicles/volkswagen-lavida.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：朗逸）。
- 车型页面：https://www.dongchedi.com/auto/series/393
- 原图：https://p3-dcd.byteimg.com/tos-cn-i-dcdx/32129dbc6b824b1abf2e2388c1890b95~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 丰田 RAV4 荣放

- 文件：`frontend/public/images/vehicles/toyota-rav4.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：RAV4荣放）。
- 车型页面：https://www.dongchedi.com/auto/series/546
- 原图：https://p3-dcd.byteimg.com/tos-cn-i-dcdx/e8651090ef4344d1be51b524ff1904bf~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 比亚迪 海豹

- 文件：`frontend/public/images/vehicles/byd-seal.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：海豹）。
- 车型页面：https://www.dongchedi.com/auto/series/5579
- 原图：https://p3-dcd.byteimg.com/tos-cn-i-dcdx/d84891563f414575a52c8ee687f7a3ba~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 别克 GL8

- 文件：`frontend/public/images/vehicles/buick-gl8.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：别克GL8）。
- 车型页面：https://www.dongchedi.com/auto/series/353
- 原图：https://p9-dcd.byteimg.com/tos-cn-i-dcdx/101a350c342f4960866dd96f321cacb8~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 本田 雅阁

- 文件：`frontend/public/images/vehicles/honda-accord.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：雅阁）。
- 车型页面：https://www.dongchedi.com/auto/series/289
- 原图：https://p3-dcd.byteimg.com/tos-cn-i-dcdx/8fb97d323f40418e817aacb90ae8f644~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 特斯拉 Model 3

- 文件：`frontend/public/images/vehicles/tesla-model-3.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：Model 3）。
- 车型页面：https://www.dongchedi.com/auto/series/3762
- 原图：https://p3-dcd.byteimg.com/tos-cn-i-dcdx/f1cb1d06a54f44e0a4b8c7eefd1791cd~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 丰田 凯美瑞

- 文件：`frontend/public/images/vehicles/toyota-camry.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：凯美瑞）。
- 车型页面：https://www.dongchedi.com/auto/series/535
- 原图：https://p3-dcd.byteimg.com/tos-cn-i-dcdx/f1438809b5aa46dca1296a9144a2f039~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 大众 途观 L

- 文件：`frontend/public/images/vehicles/volkswagen-tiguan-l.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：途观L）。
- 车型页面：https://www.dongchedi.com/auto/series/404
- 原图：https://p9-dcd.byteimg.com/tos-cn-i-dcdx/531775569f224f82a4f363c9ae6461f7~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 比亚迪 宋 PLUS

- 文件：`frontend/public/images/vehicles/byd-song-plus.webp`；960 × 520，透明背景，前侧朝左。
- 来源：汽车之家车型库（原站车型名称：宋PLUS新能源）。
- 车型页面：https://www.autohome.com.cn/5761/
- 原图：https://car2.autoimg.cn/cardfs/series/g30/M09/04/73/autohomecar__ChxknGV4DzSAHG4RAAdLphsVYos293.png

##### 理想 L7

- 文件：`frontend/public/images/vehicles/li-l7.webp`；960 × 520，透明背景，前侧朝左。
- 来源：汽车之家车型库（原站车型名称：理想L7）。
- 车型页面：https://www.autohome.com.cn/6949/
- 原图：https://car3.autoimg.cn/cardfs/series/g27/M06/1F/55/autohomecar__ChtlxmgvFKaAGmTaAAV94eOnU7w267.png

##### 本田 奥德赛

- 文件：`frontend/public/images/vehicles/honda-odyssey.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：奥德赛）。
- 车型页面：https://www.dongchedi.com/auto/series/291
- 原图：https://p3-dcd.byteimg.com/tos-cn-i-dcdx/ed5dc7d3e0384ccc97804975413d1835~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 日产 轩逸

- 文件：`frontend/public/images/vehicles/nissan-sylphy.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：轩逸）。
- 车型页面：https://www.dongchedi.com/auto/series/1145
- 原图：https://p9-dcd.byteimg.com/tos-cn-i-dcdx/b4fb6a87e743456b85258d35e894b3f2~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 吉利 星越 L

- 文件：`frontend/public/images/vehicles/geely-xingyue-l.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：星越L）。
- 车型页面：https://www.dongchedi.com/auto/series/4857
- 原图：https://p3-dcd.byteimg.com/tos-cn-i-dcdx/588bea89ef084984b66a2ee353b261ff~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 小鹏 P7

- 文件：`frontend/public/images/vehicles/xpeng-p7.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：小鹏P7）。
- 车型页面：https://www.dongchedi.com/auto/series/3352
- 原图：https://p9-dcd.byteimg.com/tos-cn-i-dcdx/7d0261ff50644213b56f6982fdacab0c~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 丰田 赛那

- 文件：`frontend/public/images/vehicles/toyota-sienna.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：赛那）。
- 车型页面：https://www.dongchedi.com/auto/series/5223
- 原图：https://p3-dcd.byteimg.com/tos-cn-i-dcdx/0c4126f4e19f4486ab824f4c0abb7a95~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 大众 帕萨特

- 文件：`frontend/public/images/vehicles/volkswagen-passat.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：帕萨特）。
- 车型页面：https://www.dongchedi.com/auto/series/398
- 原图：https://p9-dcd.byteimg.com/tos-cn-i-dcdx/b4285df0389c47399c7f0fa8d52a8334~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 蔚来 ET5

- 文件：`frontend/public/images/vehicles/nio-et5.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：蔚来ET5）。
- 车型页面：https://www.dongchedi.com/auto/series/5409
- 原图：https://p9-dcd.byteimg.com/tos-cn-i-dcdx/021a6d4c02a74d718f7806e6c7239461~tplv-resize:1280:0.png?psm=motor.car.agg_sort

##### 长安 CS75 PLUS

- 文件：`frontend/public/images/vehicles/changan-cs75-plus.webp`；960 × 520，透明背景，前侧朝左。
- 来源：懂车帝车型库（原站车型名称：长安CS75 PLUS）。
- 车型页面：https://www.dongchedi.com/auto/series/3196
- 原图：https://p3-dcd.byteimg.com/tos-cn-i-dcdx/9c588cfcdf8048d0a0cf1736baeccc39~tplv-resize:1280:0.png?psm=motor.car.agg_sort

#### 版权边界

汽车品牌和平台图片版权归原权利人，公开访问不等于取得商用或再分发许可。本项目未取得独立商业授权，现仅作本地课程演示的车型参考，不将素材声明为开源图库。原图中的平台标记和车身文字保持不变；库存车牌在界面文字中独立呈现，不将素材中的展示牌当作业务车牌。商业发布前应更换为自有统一机位实拍或已经取得许可的素材。

所有图片本地加载，运行时不依赖第三方图床，不向来源平台发送库存车牌等业务数据。

#### 2026-10-29 展示层调整

按用户要求，对现有 18 张车型参考图中的示意车牌增加定位遮挡，坐标记录在 `frontend/src/vehiclePlateShields.js`。遮挡只作用于界面图层，原始 WebP、图片来源与版权说明保持不变；仍明确标注车型参考，不能视为对应业务车牌的实车照片。

</details>
