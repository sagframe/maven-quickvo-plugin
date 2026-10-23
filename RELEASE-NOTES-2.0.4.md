# quickvo-maven-plugin 2.0.4 发版说明

本次版本聚焦"让失败提前且可定位"与配置解析正确性:新增生成阶段 Java 字段名校验与空任务失败语义,修复多类属性/占位符/YAML 解析缺陷。quickvo.xml 配置与生成产物格式完全兼容,从 2.0.x 升级无需改动。

## 新增功能

- **生成阶段 Java 字段名校验**:列名转换出的字段名若属于下列形态,历史行为是生成无法编译的源码(字段/getter 编译失败),错误暴露在使用方的 `mvn compile` 阶段且难以定位到具体列:
  - Java 关键字(如列 `CLASS`、`NEW` 转换出字段 `class`、`new`)
  - 非法 Java 标识符(如列 `A-B`、`USER NAME`)
  - 与本表其它列转换出的字段名重名(如 `ReciveTime`/`reciveTime`,或三列归一消歧后仍剩两个同名的场景)
  - 现统一汇总为插件错误并指明表与列,失败发生在 quickvo 生成阶段(quickvo:quickvo),失败即 Maven 构建失败
  - 只报告不重命名,字段命名与生成物格式保持历史行为;校验在驼峰冲突消歧之后进行,已消歧字段(如 `Recive_Time`/`ReciveTime`)不误报;中文标识符、`record`/`var`/`sealed` 等受限标识符、含 `$` 的标识符均为合法,不报错

## 缺陷修复

- **修复 `<task>` 属性不解析 `${}` 占位符的问题**:author/datasource/swagger-model/api-doc/fields-rid-prefix 此前原样取值,`author="${proj.author}"` 会把字面量输出到生成物头注释;`<task datasource="${proj.ds}">` 与 `<datasource name="${proj.ds}">` 一侧解析一侧不解析,导致注册名与引用名对不上、validate 阶段误报数据源未定义;现与 url 等其它属性同步解析
- **修复 YAML 属性文件解析产生脏值的问题**(解析器换用 snakeyaml-engine,在 Compositor 层正确展开锚点与引用):
  - 列表值(如 `app.roles: [admin, user]`)此前被取成 `"["` 字面量;列表项内的 key 会被挂到错误前缀(如 `app.servers[0].host` 被写成 `app.host`),后续 `${app.host}` 占位符即取到静默脏值;现列表整体跳过(本插件的属性仅用于 `${}` 标量替换),null 值不写入
  - 锚点/引用(`&anchor` / `*alias`)此前被取成锚名字符串(`x: *a` 取到 `"a"` 而非引用值),现正确展开为被引用的标量或对象
  - merge key(`<<: *base`)此前被写成 `"<<"` 脏键,现按 YAML 语义合并进当前层级(同层显式键优先)
  - 多文档(`---` 分隔)按序加载、后者覆盖前者;重复 key 沿用历史语义(后者覆盖)
- **修复字段名校验键与生成物不一致的问题**:校验此前用 `String.toLowerCase(ROOT)` 计算字段名,对 U+0130(İ)等字符会映射为多字符形态(i+组合点),与模板 `?uncap_first`(仅首字符小写)的实际产物不一致,同表存在同名列时漏报重复;现校验键与 FreeMarker 完全同算法
- **修复未配置 project.version/project.name 时头注释输出字面量的问题**:`getPropertyValue` 未配置时返回 null 而不是 key 本身,头注释据此省略 `@version`/`@project` 行,不再出现 `@version project.version` 字面量
- **修复默认 configFile(`classpath:quickvo.xml`)在 Maven 环境失效的问题**:插件执行期的 TCCL 是插件自身 realm,工程的 `src/main/resources` 不在其中,classpath 资源取不到后会拼出含 `classpath:` 的非法文件名并报"配置文件不存在";现按"工程根目录 → `src/main/resources`"回退,不显式配置 `configFile` 也能直接使用
- **修复 baseDir 参数默认值不生效的问题**:`@Parameter(defaultValue = "${project.basedir}")` 在 Maven 运行时不会被注入,回退到 `user.dir`——多模块构建中 `user.dir` 为 reactor 根目录,各模块会按根目录解析 `dist`,产物写错位置;改用 Maven 内置表达式 `${basedir}`(已实测注入正确)
- **空任务默认令构建失败**:任务 include 未匹配到任何表时,历史行为是打一段提示后 BUILD SUCCESS、零产物,是最难发现的配置错误;现汇总为"任务未匹配到表"错误并令构建失败,无表提示同时提升为 WARNING。确认该任务可以空跑时,在 `<tasks>` 上设 `fail-on-empty="false"`(2.0.4 起 XSD 可校验可提示;等效的 `<property name="fail-on-empty" value="false"/>` 写法保留兼容,tasks 属性优先)
- **quickvo.xml 解析 XXE 加固**:XML 解析显式拒绝 DOCTYPE 与外部实体(disallow-doctype-decl 等),防止配置文件被注入实体引用读取本地文件或发起外连;quickvo.xml 为本地配置,正常使用不含 DOCTYPE,行为无感
- **修复非法表名正则抛裸 PatternSyntaxException 的问题**:`exclude="*_LOG"` 这类通配符风格的值此前在提取表阶段裸抛 PatternSyntaxException、难以定位到具体属性;现 include/exclude 与 extra-annotations 的表名正则在配置解析期预检,报错指明属性名、原值与写法指引(如 `.*_LOG`)
- **修复视图产物每次重生成打误导性告警的问题**:视图 parent 天然无主键构造器区,新旧内容均无区域标记属预期形态,此前每次重生成都打"内容中不存在区域标记,已保留原文件内容(如需重新生成请删除该文件)!"的 WARNING 误导用户;现该场景静默,仅"新内容有区域而旧文件缺标记"(用户误删标记)才告警
- **修正 cascade 的 delete 默认值(防误删子表)**:`<cascade><table>` 未写 `delete` 时,历史行为是一律覆盖为级联删除(`delete=true`),即使外键是 `ON DELETE RESTRICT`——仅为配置 `load`/`orderBy`/`update-cascade` 而写的 cascade 条目会静默打开级联删除;现 `delete` 为三态:未配置即跟随外键 `ON DELETE` 规则(仅 `ON DELETE CASCADE` 才输出 `delete=true`),显式 `delete="true"/"false"` 才覆盖外键
- **修复 DB2 配置 catalog 后取不到任何表/外键的问题**:DB2 驱动的 JDBC 元数据不接受非 null catalog(实测 DB2 12.1.5 传库名时 `getTables`/`getExportedKeys` 均返回 0 行),按 README 给 DB2 配 `catalog=库名` 会导致"没有匹配到表"、级联注解缺失;现 DB2 归一为忽略 catalog(库名已在连接 URL 中)、仅保留 schema 并纠正大小写,README 的 DB2 配置说明同步修正
- **修复 Oracle/达梦隐藏列(invisible)被当成普通字段的问题**:Oracle 12c+ 的 invisible 列经 JDBC `getColumns` 仍会返回(23.x 驱动实测),此前会进入实体,sqltoy 按实体写 insert/update 时会带出未声明列;现按数据字典 `ALL_TAB_COLS.HIDDEN_COLUMN='YES'` 剔除并在日志中列出被忽略的列,达梦同规则处理(库无该列/无权限时自动保持原行为)
- **修复 ClickHouse 列长度/默认值噪音**:ClickHouse 的 String/DateTime 等无长度类型此前输出 `length=0L`,String 列还会输出 `defaultValue=""`(空 DEFAULT_EXPRESSION 实为"未声明默认",不等于空串默认);现模板仅在 precision>0 时输出 length,ClickHouse 空默认表达式统一置空

## 工程化

- README 增加目录导航、Maven 生命周期绑定(process-sources 自动触发生成)示例、XSD 悬停提示说明
- 插件描述符显式声明最低 Maven 版本 `3.6.3`:此前 `requiredMavenVersion=auto` 取 maven-plugin-api 版本 3.9.16,会把最低要求误抬到 3.9.16(与 README 的 Maven 3.6+ 不符,老版本 Maven 会直接拒绝执行)
- 依赖精简:YAML 解析由 Jackson 3 流式改为 snakeyaml-engine(更小的传递依赖面,锚点/引用由解析器正确处理),jackson-dataformat-yaml/jackson-core 从插件依赖中移除
- 测试覆盖:新增字段名校验(关键字/非法/重名/消歧不误报/校验键与模板 uncap_first 一致性)、YAML 解析(嵌套展平/标量类型/列表跳过/引号值/根级列表/锚点引用/merge key/多文档/重复 key)、占位符解析(未配置返回 null/系统属性回退/task 与 datasource 名称)、`classpath:` 配置文件回退(工程资源目录/工程根目录)、DOCTYPE 拒绝、非法表名正则指引、视图双缺标记静默、cascade delete 三态(未配置跟随外键/显式覆盖)、DB2 catalog 归一、隐藏列剔除单测;累计 128 个用例,golden 基线字节级验证全绿
- 真库验证:MySQL 9 / PostgreSQL 18 / openGauss 5 / OceanBase / KingbaseES / Oracle Free 23 / SQL Server 2025 / DB2 12 / DM8 / SAP HANA / HighGo / Vastbase 共 12 种库上验证 `DELETE_RULE` 推导与三态级联产出

## 升级提示

- 无需改配置,直接升级插件版本即可
- 若此前依赖"<task datasource> 不解析占位符"的行为(引用名原样),升级后请确保 `<datasource name>` 与 `<task datasource>` 使用相同写法,占位符两侧同步解析
- 列名本身不合法(如含 `-`、空格)的表,升级后生成阶段会直接报错并指明表与列,请调整列名或改用视图为该列起合法别名
- 任务 include 匹配不到任何表时,升级后默认**构建失败**(此前是 SUCCESS + 零产物);若某些环境确实允许该任务为空,在 `<tasks>` 上设 `fail-on-empty="false"`(property 写法亦兼容)
- 若此前依赖"cascade 条目不写 `delete` 即级联删除"的行为(如子表数据靠 sqltoy 级联清理),升级后请在该 `<table>` 上显式加 `delete="true"` 以保持原语义;未显式配置的 cascade 条目现在跟随数据库外键的 `ON DELETE` 规则

---

- 项目地址:<https://gitee.com/sagacity/maven-quickvo-plugin>
- sqltoy-orm:<https://gitee.com/sagacity/sqltoy>
- 完整示例工程:<https://gitee.com/sagacity/sqltoy-quickstart>
