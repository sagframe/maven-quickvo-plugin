# maven-quickvo-plugin

[中文](#中文说明) | [English](#english-description)

---

## 目录

- [中文说明](#中文说明)
  - [功能特性](#功能特性) · [环境要求](#环境要求) · [快速开始](#快速开始) · [配置总览](#配置总览quickvoxml)
  - [生成产物与重复生成保护](#生成产物说明与重复生成保护重点) · [schema 与 catalog 对照表](#schema-与-catalog-配置对照表重点)
  - [主键策略](#主键策略配置详解) · [类型映射](#数据类型映射配置详解) · [级联](#级联cascade) · [接口文档](#接口文档注解api-doc)
  - [乐观锁](#乐观锁版本字段data-version) · [分区/MPP 开关](#分区与-mpp-注解开关) · [参数定义](#参数定义property与内置属性) · [特殊符号处理](#表字段备注中的特殊符号处理)
  - [框架架构](#框架架构) · [构建与测试](#构建与测试) · [2.0 版本说明](#20-版本说明全面优化改造) · [FAQ](#常见问题faq) · [发布记录](#发布记录) · [License 与贡献](#license-与贡献)
- [English Description](#english-description)

# 中文说明

sqltoy-orm 官方代码生成插件：依据数据库表结构生成 sqltoy 所需的 entity(POJO) 与 vo(DTO)，支持 20+ 种数据库、主键策略、级联、接口文档注解、类型自定义映射，重复生成不覆盖手工代码。

- 项目地址：<https://gitee.com/sagacity/maven-quickvo-plugin>
- sqltoy-orm：<https://gitee.com/sagacity/sqltoy>
- 完整示例工程：<https://gitee.com/sagacity/sqltoy-quickstart>

## 功能特性

* **数据库支持广**：mysql/mariadb/tidb/doris/starrocks/clickhouse、oracle、sqlserver、postgresql、db2、达梦 dm、openGauss/gaussdb/vastbase/kingbase、瀚高 highgo、h2、sqlite、hana 等 20+ 种，方言级适配（含向量列、生成列、分区表、MPP 引擎元数据）
* **产物形态灵活**：entity/vo 两层或单层生成，抽象类拆分、lombok、链式 setter、继承父类、类名/包名模板（`#{subName}`）
* **重复生成安全**：自动生成区标记之外的手工代码永不覆盖；sqltoy 语义注解（@DataVersion/@Secure/@Tenant/@OneToMany 等）改表重生成时自动继承
* **主键策略全**：数据库自增/序列默认值自动识别，6 种内置生成器、sequence、应用层赋值、业务规则编号（business-primary-key）
* **接口文档注解**：swagger-v3 / swagger-v2 内置（别名自动归一），支持 freemarker 自定义模板；缺省输出 javadoc 天然适配 smart-doc
* **工程化**：quickvo.xml 全量 XSD 注释（IDE 悬停可见）、双语日志、生成失败即构建失败、golden 基线字节级测试

## 环境要求

| 项 | 要求 |
|---|---|
| JDK | 17+（插件以 Java 17 编译发布） |
| Maven | 3.6+ |
| ORM | sqltoy-orm（建议 5.x 较新版本；老版本无 @Partition/@MppTable 注解时需显式关闭，见下文） |
| JDBC 驱动 | **必须声明在插件的 `<dependencies>` 中**（插件自身不捆绑任何驱动） |

## 快速开始

### 1. 在 pom.xml 中声明插件

```xml
<plugin>
    <groupId>com.sagframe</groupId>
    <artifactId>quickvo-maven-plugin</artifactId>
    <version>2.0.4</version>
    <configuration>
        <configFile>src/main/resources/quickvo.xml</configFile>
        <baseDir>${project.basedir}</baseDir>
    </configuration>
    <dependencies>
        <!-- 数据库驱动放这里 -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>26.7.0</version>
        </dependency>
    </dependencies>
</plugin>
```

插件参数（Mojo `quickvo`）：

| 参数 | 默认值 | 说明 |
|---|---|---|
| configFile | `classpath:quickvo.xml` | 配置文件路径，支持文件系统相对/绝对路径及 `classpath:` 前缀 |
| baseDir | `${project.basedir}` | 执行根路径，quickvo.xml 中相对路径（输出目录、参数文件等）的基准 |
| loggerFileEncoding | UTF-8 | 日志文件编码 |

### 2. 编写 quickvo.xml

完整示例（**带上 xsi:schemaLocation 后 IDE 可校验并有悬停提示**）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<quickvo xmlns="http://www.sagframe.com/schema/quickvo"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.sagframe.com/schema/quickvo https://sagframe.github.io/schema/quickvo.xsd">

    <!-- 参数定义：供配置内 ${} 占位符引用；也可 <property file="xxx.properties|.yml"/> 批量引入 -->
    <property name="project.name" value="sqltoy-quickstart" />

    <!-- 数据源：task 通过 datasource 名引用，可定义多个 -->
    <datasource name="demo" driver="com.mysql.cj.jdbc.Driver"
        url="jdbc:mysql://localhost:3306/quickvo_demo?useUnicode=true&amp;characterEncoding=utf8"
        catalog="quickvo_demo" username="root" password="root" />

    <!-- 任务集合：dist 为输出根目录（相对 baseDir），encoding 为产物编码，log-language 日志语言(XSD枚举校验) -->
    <tasks dist="src/main/java" encoding="UTF-8" log-language="zh">
        <!-- include 为表名正则（忽略大小写），^SAG_\w+ 表示 SAG_ 前缀的全部表 -->
        <task author="chenrenfei" include="^SAG_\w+" datasource="demo" api-doc="swagger-v3">
            <!-- substr 剔除表名前缀，#{subName} 为剔除后的驼峰名：SAG_STAFF_INFO → StaffInfo / StaffInfoVO -->
            <entity package="com.demo.entity" substr="SAG_" name="#{subName}" />
            <vo package="com.demo.vo" substr="SAG_" name="#{subName}VO" />
        </task>
    </tasks>

    <!-- 主键策略（可选）：不配置则按数据库特征自动推导 -->
    <primary-key>
        <table name="SAG_\w+" strategy="generator" generator="default" />
    </primary-key>

    <!-- 扩展注解（可选）：无法从表结构推导的注解配置化，重复生成稳定输出 -->
    <extra-annotations>
        <table name="SAG_ORDER">
            <class-annotation>@org.sagacity.sqltoy.config.annotation.Sharding(fields="orderId")</class-annotation>
            <field-annotation column="DATA_VERSION">@org.sagacity.sqltoy.config.annotation.DataVersion</field-annotation>
            <imports value="org.sagacity.sqltoy.config.annotation.Sharding,org.sagacity.sqltoy.config.annotation.DataVersion" />
        </table>
    </extra-annotations>

    <!-- 自定义类型映射（可选）：后配置优先 -->
    <type-mapping>
        <sql-type native-types="NUMBER,DECIMAL,NUMERIC" precision="1..8" scale="0"
            jdbc-type="INTEGER" java-type="Integer" />
    </type-mapping>
</quickvo>
```

### 3. 执行生成

```sh
mvn quickvo:quickvo
```

执行完输出统计：处理表数、文件新建/更新/无变化数量与耗时。可在项目根路径写个 `mvn-quickvo.bat`（Windows）/ shell 脚本快捷执行。

如需在构建或 CI 中自动执行，可绑定到 Maven 生命周期阶段：

```xml
<plugin>
    <groupId>com.sagframe</groupId>
    <artifactId>quickvo-maven-plugin</artifactId>
    <version>2.0.4</version>
    <configuration>
        <configFile>src/main/resources/quickvo.xml</configFile>
        <baseDir>${project.basedir}</baseDir>
    </configuration>
    <executions>
        <execution>
            <id>quickvo-generate</id>
            <phase>process-sources</phase>
            <goals>
                <goal>quickvo</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

绑定后执行 `mvn generate-sources`（或任何包含该阶段的命令，如 `mvn package`）即自动触发生成。若你希望每次生成后人工检查产物再提交，可保持手动执行方式。

## 配置总览（quickvo.xml）

xsd 已内置全量注释（IDE 悬停可见），核心结构（子元素顺序不限）：

```
property*      参数定义/${}占位符/外部properties、yml引入
api-doc?       自定义接口文档注解模板(配合task的api-doc="custom")
datasource+    数据源定义(task按名引用)
tasks          任务集合(include正则圈表 → entity/vo两层或单层生成)
               属性: dist(输出根目录)/abstractPath(抽象类子包,默认base)/encoding(默认UTF-8)
                    include-partition/include-mpp-table(分区与MPP注解开关,默认true)
                    log-language(日志语言zh|en|auto,缺省auto按系统Locale)
primary-key?   主键策略
business-primary-key?  业务主键(有规则编号)
cascade?       主子表级联细化
extra-annotations?     扩展注解(@Sharding/@DataVersion等,2.0新增)
type-mapping?  自定义类型映射
```

### task 属性

| 属性 | 说明 |
|---|---|
| include（必填） | 表名匹配正则，忽略大小写、包含语义；`^SAG_\w+` 前缀匹配，`*` 全部 |
| exclude | 排除正则，优先于 include |
| datasource | 数据源名称；仅定义一个数据源时可省略 |
| active | 是否启用，默认 true（同一批表可配多个 task 按不同风格生成） |
| author | 作者，写入生成文件头 |
| api-doc | swagger-v3 / swagger-v2 / custom / none（见下文） |
| fields-rid-prefix | 剔除字段统一前缀，如 `A_` 则 A_STAFF_ID → staffId |

### entity / vo 属性

| 属性 | entity 默认 | vo 默认 | 说明 |
|---|---|---|---|
| package | 必填 | 必填 | 包名 |
| name | `#{subName}` | `#{subName}` | 类名模板，`#{subName}` 为剔除前缀后的驼峰名 |
| substr | — | — | 剔除的表名前缀，如 `SAG_` |
| has-abstract | **true** | false | 是否拆分抽象类+扩展类两个文件（见下文产物说明） |
| lombok / lombok-chain | false | false | lombok 模式生成单文件（自动禁用抽象类拆分）；chain 为 `@Accessors(chain=true)` 链式 setter |
| extends | — | — | 父类全限定名 |
| skip-extends-fields | — | — | 父类已有的公共字段，逗号分隔，生成时跳过 |
| to-dir | tasks.dist | tasks.dist | 输出目录（绝对路径或相对 baseDir） |

## 生成产物说明与重复生成保护（重点）

不同产物采用不同写入策略，保证表结构变化后可放心重复执行 `mvn quickvo:quickvo`：

| 产物 | 写入策略 | 重复生成行为 |
|---|---|---|
| AbstractXxx（抽象类，承载全部字段） | 全量重生成 | 内容无变化跳过；表结构变化整体重写 |
| Xxx entity 扩展类（has-abstract=true） | 仅更新构造器自动生成区 | **手工代码永不触碰** |
| lombok 实体（单文件） | 仅更新字段自动生成区 + import 区 | 标记区外手工方法/字段保留 |
| 纯 entity（has-abstract=false 且非 lombok） | 全量重生成 | 内容无变化跳过；**要手工扩展代码请用默认抽象类模式或 lombok 模式** |
| AbstractXxxVO | 全量重生成 | 同抽象 entity |
| XxxVO 扩展类（has-abstract=true） | 仅首次生成 | 已存在永不覆盖 |
| 普通 VO/DTO | 仅更新字段自动生成区 + import 区 | 标记区外手工代码保留 |

标记成对出现、共四类（不同类型产物用到不同标记）：

```
/*---begin-imports-area---auto-managed---*/                 import 自动管理区（所有产物）
/*---begin-class-annotations-area---auto-managed---*/       类级注解区（dto / lombok 实体）
/*---begin-auto-generate-don't-update-this-area--*/         字段自动生成区（dto、lombok 实体）
/*---begin-constructor-area---don't-update-this-area--*/    构造器自动生成区（has-abstract 扩展类实体）
```

以默认抽象类模式生成的实体扩展类为例（字段全部承载于 AbstractXxx，扩展类只含构造器自动生成区）：

```java
/**
 *@Generated by quickvo-maven-plugin 2.0.4
 */
package com.demo.entity;

/*---begin-imports-area---auto-managed---*/
import org.sagacity.sqltoy.config.annotation.SqlToyEntity;
import com.demo.entity.base.AbstractStaffInfo;
/*---end-imports-area---auto-managed---*/

/**
 * @author chenrenfei
 * Table: SAG_STAFF_INFO,Remark:员工信息表
 */
@SqlToyEntity
public class StaffInfo extends AbstractStaffInfo {
    /** default constructor */
    public StaffInfo() { super(); }

    /*---begin-constructor-area---don't-update-this-area--*/
    /** pk constructor */
    public StaffInfo(String staffId) { this.staffId = staffId; }
    /*---end-constructor-area---don't-update-this-area--*/

    @Override
    public String toString() { return super.toString(); }
}
```

import 自动管理区随表结构自动增减（加列补 import、删列去 import）；历史版本生成的无标记文件自动退化为**只增不删**，绝不触碰既有内容。

| 手工内容 | 保护方式 |
|---|---|
| 父类/实体类/DTO 中手工方法、字段 | 自动生成区标记之外的代码永不覆盖 |
| 手工 import | import 自动管理区标记之外的 import 永不触碰 |
| 手工加的注解（@DataVersion 等） | 用 `extra-annotations` 配置化声明，每次生成稳定输出（见下） |

### sqltoy 语义注解的自动继承（2.0 新增）

改表重新生成时，quickvo 会解析旧实体文件，自动继承无法从表结构推导的 sqltoy 注解，无需任何配置：

| 注解 | 继承规则 |
|---|---|
| @DataVersion（类级/字段级）、@Secure、@SecureConfig、@Tenant、@Translate/@Translates、@SqlToyFieldAlias、@OneToOne | 旧文件原样继承；quickvo.xml 的 extra-annotations 同名注解优先 |
| @OneToMany | quickvo.xml cascade 配置命中的子表以配置为准（覆盖手工修改）；未配置 cascade 的子表继承旧注解的 load/update/orderBy/delete 手工调优 |
| @BusinessId | quickvo.xml business-primary-key 命中该表以配置为准；未配置则继承旧注解原文 |

继承的简单名注解所需 import 自动补入；全限定名形式注解无需 import 亦正确处理。

## schema 与 catalog 配置对照表（重点）

内部原理是 JDBC 标准 `conn.getMetaData().getTables(catalog, schema, tableNamePattern, TABLE)`，**两者填反或填错会取不到表或字段重复**：

| 数据库 | catalog | schema | 示例 |
|---|---|---|---|
| mysql / mariadb | **数据库名**（小写） | 不配置 | `catalog="quickvo_demo"` |
| tidb | **数据库名** | 不配置 | `catalog="test"` |
| doris / starrocks | **数据库名** | 不配置 | `catalog="sqltoy_verify"` |
| oracle | 不配置 | **用户名（大写）** | `schema="SYSTEM"` |
| 达梦 dm | 不配置 | **用户名（大写）** | `schema="SYSDBA"` |
| postgresql | 一般不配置 | **模式名（小写）** | `schema="public"` |
| openGauss / gaussdb / vastbase / mogdb | 一般不配置 | **模式名（小写）** | `schema="public"` |
| 人大金仓 kingbase | 一般不配置 | **模式名** | `schema="public"` |
| 瀚高 highgo | 一般不配置 | **模式名（小写）** | `schema="public"` |
| sqlserver | **数据库名** | **架构名** | `catalog="master" schema="dbo"` |
| db2 | 不配置（库名写在 URL，驱动不支持 catalog） | **用户名（大写）** | `schema="DB2INST1"` |
| clickhouse | 不配置 | 不配置 | 走 system.columns |
| sqlite / h2 | 不配置 | 不配置 | 文件/内存库 |

经验法则：

- **单用户单库场景：都不配置**（最简，先跑通再加过滤）
- 一个属性就够的库不要两个都配（如 mysql 只配 catalog）
- 取不到表时看日志：2.0 起会**按数据库类型给出专属指引，并列出当前库实际可用的 catalog/schema 清单**
- **底层按数据库类型自动容错（2.0）**：识别数据库类型后自动纠正常见配置错误——mysql 系把库名配到 schema 上会自动挪到 catalog；oracle/达梦 schema 小写自动大写化、catalog 误配自动挪为 schema；postgresql 系按库内实际 schema 清单纠正大小写（配 `PUBLIC` 库内是 `public` 也能跑通）。纠错以库内清单为准，无法唯一纠正时保持原样并走精准提示，绝不因容错引入新失败
- 出现"字段重复"是 schema/catalog 未隔离多库/多用户导致

## 主键策略配置详解

### 优先级：数据库优先

| 场景 | 结果 |
|---|---|
| 列为数据库自增（identity/auto_increment） | 自动 `identity` 策略（配置了其它策略也不覆盖，除非 `force="true"`） |
| 列默认值为序列 `nextval(...)`（postgresql 系、oracle） | 自动 `identity`（2.0 增强，此前 pg 会被误判） |
| 单主键、非自增、未配置策略 | 按类型和精度自动推导（见下） |
| 复合主键、未配置策略 | 不推导，需显式配置 |

精度自动推导（单主键、字符或数字类型）：

| 主键精度(length) | 推导策略 |
|---|---|
| ≥ 19 | snowflake（63位long型雪花，当前19位十进制） |
| ≥ 22 | default（22位时序） |
| ≥ 26 | nanotime（26位纳秒） |
| < 19 | 应用层赋值 |

### 显式配置（primary-key）

```xml
<primary-key>
    <!-- generator策略:指定生成器 -->
    <table name="SAG_\w+" strategy="generator" generator="default" />
    <table name="SYS_ORDER" strategy="generator" generator="snowflake" />
    <table name="SYS_LOG" strategy="generator" generator="ulid" />        <!-- 需依赖ulid-creator -->
    <table name="SYS_PAY" strategy="generator" generator="redis" />       <!-- 基于redis序号 -->
    <table name="SYS_T1" strategy="generator" generator="UUID" />
    <!-- sequence策略:${tableName}占位符自动替换为表名 -->
    <table name="BIZ_\w+" strategy="sequence" sequence="SEQ_${tableName}" />
    <!-- 应用层赋值 -->
    <table name="TMP_\w+" strategy="assign" />
    <!-- force:强制配置优先于数据库自增 -->
    <table name="SYS_SEQ" strategy="sequence" sequence="SEQ_SYS" force="true" />
</primary-key>
```

生成器对照（strategy="generator" 时 generator 取值）：

| generator | 实现 | 说明 |
|---|---|---|
| default | DefaultIdGenerator | 22位时序主键（推荐） |
| snowflake | SnowflakeIdGenerator | 雪花算法（63位long型，18~19位十进制） |
| nanotime | NanoTimeIdGenerator | 26位纳秒 |
| UUID | UUIDGenerator | 36位UUID |
| ULID | ULIDGenerator | 时序可排序（需 com.github.f4b6a3:ulid-creator） |
| redis | RedisIdGenerator | 基于 redis 自增序号 |

注意：数字型主键配了雪花/22位/26位生成器而 java 类型是 int/short/long 时，自动升格为 `BigInteger` 防溢出。多条 `table` 规则命中同一表时**以最后一条为准**。

### 业务主键（business-primary-key）

有业务规则编号（渠道_类型_日期_流水）时使用，可替代主键策略：

```xml
<business-primary-key>
    <table name="SQLTOY_DEVICE_ORDER" column="ORDER_ID" generator="redis"
        signature="${psType}@case(${deviceType},PC,PC,NET,NT,OT)@df(yyMMdd)"
        related-columns="psType,deviceType" length="12" />
</business-primary-key>
```

signature 支持宏：`@substr(${field},start,length)` 截取、`@case(value,c1,v1,c2,v2,other)` 条件映射、`@df(yyMMdd)` 日期格式化。其余属性：`sequence-size`（流水位数）、`start`（流水起始值，默认1）、`generator` 支持 `redis`、`snowflake` 或 `@bean(beanName)` 自定义 spring bean；日内流水超容量自动扩位。

## 数据类型映射配置详解

### 默认映射（无需配置）

常用类型开箱即用：

| 数据库类型 | Java 类型 | JDBC 类型 |
|---|---|---|
| varchar / char / text / nvarchar | String | VARCHAR / CHAR |
| int / integer / tinyint / smallint | Integer | INTEGER |
| bigint | BigInteger | BIGINT |
| decimal / numeric(p,s) | BigDecimal | DECIMAL |
| float / double | Float / Double | FLOAT / DOUBLE |
| bit / boolean | Boolean | BIT / BOOLEAN |
| date | LocalDate | DATE |
| oracle date（含时分秒） | LocalDateTime | DATE |
| time | LocalTime | TIME |
| datetime / datetime2 | LocalDateTime | DATE / TIMESTAMP |
| timestamp | Timestamp | TIMESTAMP |
| timestamptz / datetimeoffset | OffsetDateTime | TIMESTAMP_WITH_TIMEZONE |
| json / jsonb | String | JSON / JSONB |
| vector（pgvector/oracle23/sqlserver2025） | Float[] | VECTOR |
| clob | String | CLOB |
| blob / bytea | byte[] | BLOB / BINARY |
| 计算列（generated） | 同基类型 | 附 generatedType=VIRTUAL/STORED |

内置两条默认规则（可被自定义覆盖）：`NUMBER/DECIMAL/NUMERIC` 精度 1..8 且无小数 → Integer；精度 9..64 且无小数 → Long。

### 自定义映射（type-mapping）

```xml
<type-mapping>
    <!-- 1.按类型+精度区间:小数精度1..8且无小数位映射Integer -->
    <sql-type native-types="NUMBER,DECIMAL,NUMERIC" precision="1..8" scale="0"
        jdbc-type="INTEGER" java-type="Integer" />

    <!-- 2.按类型:BIGINT统一映射BigInteger -->
    <sql-type native-types="BIGINT" jdbc-type="BIGINT" java-type="java.math.BigInteger" />

    <!-- 3.指定表.字段:json列映射泛型List(注意xml转义) -->
    <sql-type table-field="sqltoy_jsontype_showcae.staff_set" native-types="json"
        java-type="List&lt;StaffInfoVO&gt;" import-types="com.sqltoy.quickstart.vo.StaffInfoVO" />

    <!-- 4.向量列改映射float[]原生数组 -->
    <sql-type table-field="ai_demo_tab.embedding" native-types="vector" java-type="float[]" />
</type-mapping>
```

规则：**后配置优先**；`table-field` 精确限定（表名.字段名，小写）；`precision`/`scale` 支持 `min..max` 区间或单值，未配置不限；`java-type` 常用类（BigDecimal/BigInteger/LocalDateTime 等）可简写，其它需全限定名；`import-types` 补充需要 import 的类型。

## 级联（cascade）

存在外键约束即自动生成 @OneToMany（**父表主键被子表外键引用时**），无需配置；`cascade` 元素用于细化子表行为，**只需配置父表侧**：

```xml
<cascade>
    <table name="STAFF_LOG" update-cascade="delete" load="STATUS=1" orderBy="createTime desc" />
    <table name="STAFF_PHOTO" update-cascade="ENABLED=0" />
</cascade>
```

| 属性 | 说明 |
|---|---|
| update-cascade | 父记录更新时子记录处理方式：`delete` 级联删除，或 SQL 片段（如 `ENABLED=0` 置失效） |
| delete | 是否级联删除子记录；**不配置时跟随外键 ON DELETE 规则**（仅 `ON DELETE CASCADE` 才输出 `delete=true`），显式 `true`/`false` 覆盖外键 |
| load | 级联加载附加条件，如 `STATUS=1` |
| not-null-field | 用于判断关联子记录是否存在的字段（输出 @OneToMany notNullField） |
| orderBy | 级联加载排序，如 `createTime desc` |

无外键约束的表不会产生级联。

## 接口文档注解（api-doc）

task 上 `api-doc="swagger-v3|swagger-v2|custom|none"`，取值别名会自动归一，无需手写模板：

* `swagger-v3`（别名 `openapi3`/`openapi-3`/`openapi`/`springdoc`/`v3`）—— springdoc / OpenAPI 3 主流形态，生成 `io.swagger.v3.oas.annotations.media.@Schema`；字段按非空约束自动输出 `requiredMode = Schema.RequiredMode.REQUIRED`，数据库默认值自动输出 `example`；
* `swagger-v2`（别名 `springfox`/`v2`）—— 旧版 springfox 的 `@ApiModel`/`@ApiModelProperty`，仅存量项目使用；
* `none`（默认）—— 不生成注解，字段/表备注直接输出为 javadoc，天然适配 smart-doc 等基于 javadoc 的文档工具；
* `custom` —— 配合顶层 `api-doc` 元素自定义 freemarker 模板（类模板变量 className/tableName/tableRemark，字段模板变量 colName/colRemark/nullable/fieldType/fieldName/defaultValue），主流场景已内置无需使用。

task 上的 `swagger-model`（v3/v2/false）为历史兼容写法，建议改用 `api-doc`。

## 乐观锁版本字段（data-version）

声明表的版本列，生成字段级 `@DataVersion`（仅 entity，VO/DTO 不受影响）：

```xml
<data-version>
    <table name="SAG_ORDER" column="LAST_UPDATE"/>
    <table name="SYS_\w+" column="DATA_VERSION" start-date="true"/>
</data-version>
```

`name` 为纯名称（仅字母/数字/下划线/连字符）时按表名精确匹配（忽略大小写）；含正则元字符时按完整匹配正则，多条命中以最后一条为准。`column` 忽略大小写和 `_`/`-` 差异；`start-date="true"` 适配 `yyyyMMdd+流水` 版本形式。列不存在时打 warning 不中断。与 extra-annotations 对同一字段二选一（同时配置会产生重复注解）。

## 分区与 MPP 注解开关

`<tasks>` 上的正式属性 `include-partition` / `include-mpp-table`（均默认 true，XSD 可校验）。生成 `@Partition`/`@PartitionKey`（分区表，pg/mysql/oracle 方言提取分区策略）与 `@MppTable`（ClickHouse/Doris/StarRocks 引擎元数据，供 sqltoy DDL 还原引擎定义）；**老版本 sqltoy 无这些注解时显式设 false**，避免生成代码编译失败：

```xml
<tasks include-partition="false" include-mpp-table="false" ...>
```

也支持等效的 property 写法（`include.partition` / `include.mpp-table`），**属性形式优先**。

## 参数定义（property）与内置属性

```xml
<property name="project.name" value="myProject" />          <!-- name+value -->
<property name="license.text">授权内容...</property>          <!-- name+正文 -->
<property file="src/main/resources/application.yml" />      <!-- 批量引入 properties/yml -->
```

* 配置中通过 `${name}` 引用；value 支持 `##{globa.identity}` 全局实例标识占位符；yml 按层级 `.` 连接展平为 key
* 未配置的 key 自动回退 JVM 系统属性

内置属性：

| 属性 | 默认 | 说明 |
|---|---|---|
| include.schema | false | 生成的 @Entity 是否携带 schema |
| field.support.linked.set | false | setter 返回 this（链式赋值），与 lombok-chain 无关 |
| generate.selectFields.class | false | 生成 SelectField 内部类 |
| include.partition | true | 是否输出 @Partition/@PartitionKey（老版本 sqltoy 设 false） |
| include.mpp-table | true | 是否输出 @MppTable（老版本 sqltoy 设 false） |
| max.scale.length | — | 类型匹配的小数位截断上限 |
| skip.primary.constraint | false | 跳过主键约束名提取（主要针对 postgresql） |
| fail-on-empty | true | 任务 include 未匹配到任何表时令构建失败（防零产物假成功）；推荐在 `<tasks fail-on-empty="false">` 上关闭（XSD 可提示），property 写法保留兼容 |
| project.version / project.name | — | 写入生成文件头注释；未配置时回退 JVM 系统属性（`-Dproject.version=...`），仍没有则不输出该行 |

## 表/字段备注中的特殊符号处理

| 符号 | 处理方式 | 说明 |
|---|---|---|
| 双引号 `"` / 反斜杠 `\` | 转义为 `\"`、`\` | 保证注解字符串(`comment="..."`)是合法Java字面量 |
| 换行/回车/Tab | 归一为空格 | 备注进入单行注解前统一压平(含sqlserver多行扩展属性) |
| `*/` | javadoc 中自动替换为 `*\/` | 防止备注提前终结 `/** ... */` 块注释；注解字符串中保留原样(合法) |
| `${}`、单引号、`<>&` | 原样输出 | 模板值不再二次解析,无注入风险 |

以上对表备注和字段备注一致生效；含特殊符号的实测表(`QUICKVO_DEMO_SPECIAL_NOTE`)已纳入 golden 基线。

## 框架架构

### 模块分层

2.0 起废除全局 static 可变状态（多模块构建不再互相污染），按职责分层：

```
org.sagacity.quickvo
├── QuickVoMojo        Maven 插件入口(goal: quickvo)，失败抛 MojoExecutionException
├── QuickVoCli         命令行入口(非 maven 场景)
├── QuickVoContext     单次执行上下文(配置/属性/日志/错误汇聚，实例隔离)
├── QuickVoRunner      总控：解析配置 → 循环任务 → 汇总统计与错误
├── config/            XmlConfigParser(配置+xsd) / PropertyResolver(${}占位符、外部properties、yml)
├── datasource/        DataSourceRegistry / DbSession / DbType / SchemaCatalogNormalizer(schema·catalog自动纠错)
├── dialect/           MetadataDialect 接口 + JdbcDialect 通用实现
│                      + mysql/oracle/postgres/sqlserver/db2/dm/clickhouse/hana/impala 专属方言
├── metadata/          MetadataReader：表/列/主键/外键/索引/分区/MPP/注释提取
├── typemapping/       默认类型映射 + oracle/pg 专属映射 + 自定义规则合并
├── core/              TableProcessor / PrimaryKeyResolver / CascadeResolver /
│                      TypeMappingResolver / AnnotationCarryOver(注解继承)
├── render/            TemplateRenderer：FreeMarker 渲染(内置7套ftl模板)
├── gen/               ClassFileWriter：四种写入模式实现重复生成保护
├── model/             配置与元数据模型
└── utils/             通用工具(Msg双语日志等)
```

### 执行流程

```
quickvo.xml(XSD校验+${}占位符替换)
  → 按任务循环：连接数据库 → 识别数据库类型并选择方言(schema/catalog自动纠错)
      → include/exclude 正则匹配表和视图(无匹配输出数据库专属指引+可用catalog/schema清单)
      → 逐表提取元数据(列/主键/外键/索引/分区/MPP/注释)
      → 加工：类型映射 → 主键策略 → 级联 → 业务主键 → extra-annotations → 旧文件注解继承
      → FreeMarker 渲染 → ClassFileWriter 按产物类型选择写入模式(增量保护)
  → 汇总：表数、文件新建/更新/无变化、耗时
  → 任一任务失败或字段重复 → 抛异常令 Maven 构建失败(历史版本假 SUCCESS 已修复)
```

### 扩展点

* **新增数据库**：实现 `MetadataDialect`（一般继承 `JdbcDialect` 覆写差异点），在 `Dialects` 中按 DbType 注册即可，类型差异在 `typemapping` 补映射
* **自定义产物形态**：内置模板为 classpath `templates/*.ftl`（entity/abstract-entity/parent-entity/dto/dto-abstract/dto-parent/entity-lombok），配合 `api-doc="custom"` 可自定义注解模板
* **本地调试**：test 目录 `org.sagacity.quickvo.debug.QuickVoDebug`，IDE 指定 quickvo.xml 断点调试；非 maven 场景可直接运行 `org.sagacity.quickvo.QuickVoCli`（args[0]=配置路径, args[1]=执行根路径）

## 构建与测试

```sh
mvn install    # 构建并安装到本地仓库(JDK 17+)
mvn test       # 运行测试
```

* **纯逻辑单测**：配置解析、主键策略、类型映射、schema/catalog 纠错、注解继承、文件合并写入等 17 个测试类（另有 golden 集成测试）
* **golden 基线**：mysql/postgresql 各 2 套（基础 + 全类型覆盖），生成物字节级比对 + 二次运行幂等校验；数据库不可达时自动跳过
* **方言实测**：mysql9、postgresql16、oracle23、sqlserver2025、达梦dm8、openGauss(6/7)、vastbase、db2、clickhouse、doris、starrocks、tidb、sqlite、h2、hana、oceanbase、highgo(瀚高PG12内核)（kingbase 因本机容器故障未实测，走与已实测库一致的通用 JDBC 路径）

## 2.0 版本说明（全面优化改造）

* **quickvo.xml 配置格式与生成物完全兼容**：区域标记注释、serialVersionUID 算法、模板内容均保持不变，升级无感
* **架构**：废除全局 static 可变状态（多模块构建不再互相污染），拆分为 config/datasource/dialect/typemapping/metadata/core/render/gen 分层
* **数据库方言**：`MetadataDialect` 接口 + JdbcDialect 默认实现，新增数据库=新增一个方言类
* **构建失败语义修复**：生成失败/字段重复现在会令 maven 构建失败（历史版本只打日志仍报 SUCCESS）
* **修复**：mysql 列信息 GROUP BY 在 only_full_group_by 模式下报错导致分区键/生成列检测失效；视图类二次运行增量合并抛异常中断任务；sqlserver 中文表注释丢失、DATETIME2 生成非法枚举、2025 VECTOR 列丢失；pg 序列默认值主键误配策略；ULID 生成器映射缺失；复合主键去重破坏顺序；h2 CHARACTER VARYING 非法枚举
* **新增**：schema/catalog 按库自动纠错、sqltoy 语义注解自动继承、extra-annotations、data-version、include-partition/include-mpp-table 开关、api-doc 取值归一

## 常见问题（FAQ）

| 现象 | 原因与处理 |
|---|---|
| 没有匹配到表 | ① include 正则写错；② schema/catalog 配置错误（含大小写）——2.0 起日志会给出当前库专属指引和可用 catalog/schema 清单，可先全部去除再跑通；2.0.4 起默认**令构建失败**（避免零产物假成功），确认该任务可空跑时在 `<tasks fail-on-empty="false">` 关闭 |
| 生成的 VO 字段重复 | schema/catalog 未隔离多库/多用户，按对照表修正 |
| 日志提示表无主键 | 通常是 schema/catalog 配错，匹配到了其它 schema 的同名表 |
| 生成代码编译报错找不到 @Partition/@MppTable | 目标 sqltoy 版本较老，tasks 上设 `include-partition="false" include-mpp-table="false"` |
| 运行时报驱动类找不到 | 驱动 jar 必须声明在**插件的** `<dependencies>` 中，不是工程依赖 |
| 生成失败但 maven 仍 SUCCESS | 历史版本问题；2.0 起失败即构建失败，请升级 |
| 手工加的注解每次被覆盖 | 用 `extra-annotations` 配置化声明；@DataVersion/@Secure/@Tenant 等已支持自动继承 |
| 某数据库类型生成了不合适的 Java 类型 | 用 `type-mapping` 覆盖默认映射，并欢迎提 issue 反馈 |

## 发布记录

- **2.0.4（当前版本）**：新增生成阶段 Java 字段名校验（关键字/非法标识符/同表重名提前报错并指明表与列）与空任务默认构建失败（`fail-on-empty`）；修复 `<task>` 的 author/datasource/api-doc/fields-rid-prefix 与 `<datasource name>` 不解析 `${}` 占位符、YAML 属性文件列表值/锚点引用产生脏数据、未配置 project.version 时头注释输出 `@version project.version` 字面量、默认 configFile/baseDir 参数在 Maven 环境失效、非法表名正则抛裸异常、视图重生成误导性告警等问题；quickvo.xml 解析加固 XXE 防护。详见 [RELEASE-NOTES-2.0.4.md](RELEASE-NOTES-2.0.4.md)
- **2.0.3**：修复组合索引只保留第一列、json/文本字面量默认值误判为 STORED 生成列、未映射原生类型产出无法编译代码等问题；新增瀚高 HighGo 支持，人大金仓 KingbaseES / TiDB 升级为独立方言路径。详见 [RELEASE-NOTES-2.0.3.md](RELEASE-NOTES-2.0.3.md)
- **2.0**：架构全面优化（废除全局 static、方言接口化）、生成失败即构建失败、schema/catalog 自动纠错、sqltoy 语义注解自动继承、extra-annotations 等。详见 [RELEASE-NOTES-2.0.md](RELEASE-NOTES-2.0.md)

## License 与贡献

- 以 **Apache License 2.0** 开源（见 [LICENSE](LICENSE)），可自由用于商业与个人项目
- 使用问题、缺陷反馈与功能建议：<https://gitee.com/sagacity/maven-quickvo-plugin/issues>
- 代码贡献：Fork 后提交 Pull Request，请附带相应测试

---

# English Description

The official code generator for sqltoy-orm: generates entity (POJO) and vo (DTO) classes from database tables. Supports 20+ databases, primary-key strategies, cascading, API-doc annotations and custom type mappings — regeneration never overwrites your hand-written code.

- Project: <https://gitee.com/sagacity/maven-quickvo-plugin>
- sqltoy-orm: <https://gitee.com/sagacity/sqltoy>
- Quickstart sample: <https://gitee.com/sagacity/sqltoy-quickstart>

## Features

* **Broad database support**: mysql/mariadb/tidb/doris/starrocks/clickhouse, oracle, sqlserver, postgresql, db2, DM (Dameng), openGauss/gaussdb/vastbase/kingbase, HighGo (highgo), h2, sqlite, hana and 20+ more — dialect-level adaptation including vector columns, generated columns, partitioned tables and MPP engine metadata
* **Flexible artifacts**: entity+vo two-layer or single-layer, abstract/parent split, lombok, chained setters, custom parent class, `#{subName}` naming templates
* **Safe regeneration**: hand-written code outside auto-generate markers is never overwritten; sqltoy semantic annotations (@DataVersion/@Secure/@Tenant/@OneToMany…) are carried over from your previous files
* **Full primary-key strategies**: database identity/sequence-default auto-detection, 6 built-in generators, sequences, application assignment and rule-based business primary keys
* **API-doc annotations**: swagger-v3 / swagger-v2 built in (aliases normalized), freemarker custom templates supported; default javadoc output works natively with smart-doc
* **Engineering quality**: fully commented XSD (hover docs in your IDE), bilingual logs, generation failures fail the Maven build, byte-level golden baseline tests

## Requirements

| Item | Requirement |
|---|---|
| JDK | 17+ (the plugin is compiled with Java 17) |
| Maven | 3.6+ |
| ORM | sqltoy-orm (recent 5.x recommended; on older versions without @Partition/@MppTable, turn those switches off — see below) |
| JDBC driver | **Must be declared inside the plugin's `<dependencies>`** (the plugin bundles no drivers) |

## Quick Start

### 1. Declare the plugin in pom.xml

```xml
<plugin>
    <groupId>com.sagframe</groupId>
    <artifactId>quickvo-maven-plugin</artifactId>
    <version>2.0.4</version>
    <configuration>
        <configFile>src/main/resources/quickvo.xml</configFile>
        <baseDir>${project.basedir}</baseDir>
    </configuration>
    <dependencies>
        <!-- your JDBC driver goes here -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>26.7.0</version>
        </dependency>
    </dependencies>
</plugin>
```

Plugin parameters (goal `quickvo`):

| Parameter | Default | Description |
|---|---|---|
| configFile | `classpath:quickvo.xml` | Config file path: filesystem relative/absolute path or `classpath:` prefix |
| baseDir | `${project.basedir}` | Execution root; the base for relative paths inside quickvo.xml (output dirs, property files, …) |
| loggerFileEncoding | UTF-8 | Log file encoding |

### 2. Write quickvo.xml

Full example (**with xsi:schemaLocation your IDE validates it and shows hover docs**):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<quickvo xmlns="http://www.sagframe.com/schema/quickvo"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.sagframe.com/schema/quickvo https://sagframe.github.io/schema/quickvo.xsd">

    <!-- Parameters referenced via ${} placeholders; or <property file="xxx.properties|.yml"/> to bulk import -->
    <property name="project.name" value="sqltoy-quickstart" />

    <!-- Datasource, referenced by name from tasks; define as many as you need -->
    <datasource name="demo" driver="com.mysql.cj.jdbc.Driver"
        url="jdbc:mysql://localhost:3306/quickvo_demo?useUnicode=true&amp;characterEncoding=utf8"
        catalog="quickvo_demo" username="root" password="root" />

    <!-- Tasks: dist is the output root (relative to baseDir), encoding the output charset, log-language the log language (XSD-validated) -->
    <tasks dist="src/main/java" encoding="UTF-8" log-language="en">
        <!-- include is a table-name regex (case-insensitive); ^SAG_\w+ matches the SAG_ prefix -->
        <task author="chenrenfei" include="^SAG_\w+" datasource="demo" api-doc="swagger-v3">
            <!-- substr strips the table prefix; #{subName} is the camel name after stripping: SAG_STAFF_INFO → StaffInfo / StaffInfoVO -->
            <entity package="com.demo.entity" substr="SAG_" name="#{subName}" />
            <vo package="com.demo.vo" substr="SAG_" name="#{subName}VO" />
        </task>
    </tasks>

    <!-- Primary-key strategies (optional): auto-derived from database traits when absent -->
    <primary-key>
        <table name="SAG_\w+" strategy="generator" generator="default" />
    </primary-key>

    <!-- Extra annotations (optional): annotations not derivable from the schema, emitted stably on every run -->
    <extra-annotations>
        <table name="SAG_ORDER">
            <class-annotation>@org.sagacity.sqltoy.config.annotation.Sharding(fields="orderId")</class-annotation>
            <field-annotation column="DATA_VERSION">@org.sagacity.sqltoy.config.annotation.DataVersion</field-annotation>
            <imports value="org.sagacity.sqltoy.config.annotation.Sharding,org.sagacity.sqltoy.config.annotation.DataVersion" />
        </table>
    </extra-annotations>

    <!-- Custom type mapping (optional): later rules win -->
    <type-mapping>
        <sql-type native-types="NUMBER,DECIMAL,NUMERIC" precision="1..8" scale="0"
            jdbc-type="INTEGER" java-type="Integer" />
    </type-mapping>
</quickvo>
```

### 3. Run

```sh
mvn quickvo:quickvo
```

The run summary reports tables processed, files created/updated/unchanged and elapsed time.

To run it automatically during a build or in CI, bind the goal to a Maven lifecycle phase:

```xml
<plugin>
    <groupId>com.sagframe</groupId>
    <artifactId>quickvo-maven-plugin</artifactId>
    <version>2.0.4</version>
    <configuration>
        <configFile>src/main/resources/quickvo.xml</configFile>
        <baseDir>${project.basedir}</baseDir>
    </configuration>
    <executions>
        <execution>
            <id>quickvo-generate</id>
            <phase>process-sources</phase>
            <goals>
                <goal>quickvo</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

After binding, `mvn generate-sources` (or any command including that phase, e.g. `mvn package`) triggers generation automatically. If you prefer to review the generated files before committing, keep the manual invocation.

## Config Overview (quickvo.xml)

The XSD is fully documented (hover in your IDE); core structure (child order is flexible):

```
property*      parameters / ${} placeholders / external properties, yml import
api-doc?       custom api-doc annotation templates (for task api-doc="custom")
datasource+    datasources (referenced by name from tasks)
tasks          task collection (include regex selects tables → entity/vo generation)
               attributes: dist (output root) / abstractPath (abstract sub-package, default base)
               / encoding (default UTF-8) / include-partition / include-mpp-table (default true)
               / log-language (zh|en|auto, log language, default auto = system Locale)
primary-key?   primary key strategies
business-primary-key?  business primary key (rule-based codes)
cascade?       parent-child cascade refinement
extra-annotations?     extra annotations (@Sharding/@DataVersion…, new in 2.0)
type-mapping?  custom type mapping
```

### task attributes

| Attribute | Description |
|---|---|
| include (required) | Table-name regex, case-insensitive, contains semantics; `^SAG_\w+` prefix match, `*` matches all |
| exclude | Exclusion regex, takes precedence over include |
| datasource | Datasource name; omittable when only one datasource exists |
| active | Whether the task runs, default true (same tables can be generated by multiple tasks in different styles) |
| author | Author, written into generated file headers |
| api-doc | swagger-v3 / swagger-v2 / custom / none (see below) |
| fields-rid-prefix | Common column prefix stripped, e.g. `A_` turns A_STAFF_ID into staffId |

### entity / vo attributes

| Attribute | entity default | vo default | Description |
|---|---|---|---|
| package | required | required | Java package |
| name | `#{subName}` | `#{subName}` | Class-name template; `#{subName}` is the camel name after prefix stripping |
| substr | — | — | Table-name prefix to strip, e.g. `SAG_` |
| has-abstract | **true** | false | Split into abstract + parent files (see artifacts below) |
| lombok / lombok-chain | false | false | Lombok mode generates a single file (disables the abstract split); chain = `@Accessors(chain=true)` |
| extends | — | — | Fully qualified parent class |
| skip-extends-fields | — | — | Common fields already in the parent, comma separated, skipped |
| to-dir | tasks.dist | tasks.dist | Output directory (absolute or relative to baseDir) |

## Generated Artifacts & Regeneration Safety (Important)

Each artifact type has its own write strategy, so re-running `mvn quickvo:quickvo` after schema changes is safe:

| Artifact | Write strategy | On regeneration |
|---|---|---|
| AbstractXxx (abstract class, holds all fields) | full regeneration | skipped when unchanged; rewritten when the table changes |
| Xxx entity parent (has-abstract=true) | constructor auto-region only | **hand-written code never touched** |
| lombok entity (single file) | field auto-region + import area | hand-written members outside markers kept |
| Plain entity (has-abstract=false, no lombok) | full regeneration | skipped when unchanged; **for hand-written extensions prefer the default abstract mode or lombok** |
| AbstractXxxVO | full regeneration | same as abstract entity |
| XxxVO parent (has-abstract=true) | created once only | existing file never overwritten |
| Plain VO/DTO | field auto-region + import area | hand-written code kept |

Markers come in pairs, four kinds (each artifact type uses its own):

```
/*---begin-imports-area---auto-managed---*/                 auto-managed import area (all artifacts)
/*---begin-class-annotations-area---auto-managed---*/       class-level annotation area (dto / lombok)
/*---begin-auto-generate-don't-update-this-area--*/         auto-generated members area (dto, lombok)
/*---begin-constructor-area---don't-update-this-area--*/    constructor area (has-abstract parent classes)
```

Example: a generated entity parent class in the default abstract mode (all fields live in AbstractXxx, the parent only holds the auto-managed constructor area):

```java
/**
 *@Generated by quickvo-maven-plugin 2.0.4
 */
package com.demo.entity;

/*---begin-imports-area---auto-managed---*/
import org.sagacity.sqltoy.config.annotation.SqlToyEntity;
import com.demo.entity.base.AbstractStaffInfo;
/*---end-imports-area---auto-managed---*/

/**
 * @author chenrenfei
 * Table: SAG_STAFF_INFO,Remark:员工信息表
 */
@SqlToyEntity
public class StaffInfo extends AbstractStaffInfo {
    /** default constructor */
    public StaffInfo() { super(); }

    /*---begin-constructor-area---don't-update-this-area--*/
    /** pk constructor */
    public StaffInfo(String staffId) { this.staffId = staffId; }
    /*---end-constructor-area---don't-update-this-area--*/

    @Override
    public String toString() { return super.toString(); }
}
```

The auto-managed import area grows/shrinks with the schema (new columns add imports, dropped columns remove them); legacy files without markers degrade to **add-only** mode.

| Hand-written content | Protection |
|---|---|
| Methods/fields in parent/entity/DTO classes | never overwritten outside auto-generate markers |
| Your own imports | imports outside the auto-managed area never touched |
| Hand-added annotations (@DataVersion…) | declare them via `extra-annotations` for stable output (see below) |

### Auto carry-over of sqltoy annotations (new in 2.0)

When regenerating after schema changes, quickvo parses the previous entity files and carries over annotations that cannot be derived from the table — zero configuration:

| Annotation | Carry-over rule |
|---|---|
| @DataVersion (class/field level), @Secure, @SecureConfig, @Tenant, @Translate/@Translates, @SqlToyFieldAlias, @OneToOne | inherited verbatim; same-name annotations from extra-annotations take precedence |
| @OneToMany | child tables matched by a cascade configuration use the configuration (overriding manual tweaks); other child tables inherit the previous load/update/orderBy/delete tuning |
| @BusinessId | business-primary-key configuration wins when it matches; otherwise the previous annotation is inherited |

Imports for simple-name carried annotations are added automatically; fully-qualified annotations are handled correctly without imports.

## Schema vs Catalog Cheat Sheet (Important)

Internally quickvo calls the standard JDBC `conn.getMetaData().getTables(catalog, schema, tableNamePattern, TABLE)` — a wrong combination yields no tables or duplicated columns:

| Database | catalog | schema | Example |
|---|---|---|---|
| mysql / mariadb | **database name** (lowercase) | leave empty | `catalog="quickvo_demo"` |
| tidb | **database name** | leave empty | `catalog="test"` |
| doris / starrocks | **database name** | leave empty | `catalog="sqltoy_verify"` |
| oracle | leave empty | **username (UPPERCASE)** | `schema="SYSTEM"` |
| DM (Dameng) | leave empty | **username (UPPERCASE)** | `schema="SYSDBA"` |
| postgresql | usually empty | **schema (lowercase)** | `schema="public"` |
| openGauss / gaussdb / vastbase / mogdb | usually empty | **schema (lowercase)** | `schema="public"` |
| kingbase | usually empty | **schema** | `schema="public"` |
| highgo (HighGo) | usually empty | **schema (lowercase)** | `schema="public"` |
| sqlserver | **database name** | **schema** | `catalog="master" schema="dbo"` |
| db2 | leave empty (database name goes in the URL; the driver ignores catalog) | **username (UPPERCASE)** | `schema="DB2INST1"` |
| clickhouse | leave empty | leave empty | uses system.columns |
| sqlite / h2 | leave empty | leave empty | file/in-memory |

Rules of thumb:

- **Single user & single database: configure neither.** Get it working first, then add filters.
- Configure only what your database actually uses (e.g. mysql: catalog only).
- Since 2.0, when no tables match, the log prints **database-specific guidance plus the actual catalog/schema list available on your connection**.
- **Automatic normalization (2.0)**: after detecting the database type, common mistakes are auto-corrected — mysql family moves a database name wrongly placed in schema to catalog; oracle/DM upper-cases a lowercase schema and moves a wrongly set catalog to schema; the postgresql family corrects case against the actual schema list (`PUBLIC` works even when the real schema is `public`). Corrections are validated against the live database catalog; ambiguous cases stay untouched with precise guidance — normalization never introduces new failures.
- "Duplicated columns" means schema/catalog failed to isolate multiple databases/users.

## Primary Key Strategies

Priority: **database wins**. Auto-increment columns (identity/auto_increment) and sequence-default columns (`nextval(...)`) automatically use the `identity` strategy — an explicit configuration is ignored unless `force="true"`.

Auto-derivation for single non-auto-increment PKs (char or numeric): length ≥19 → snowflake (63-bit long, currently 19 digits), ≥22 → default 22-digit, ≥26 → nanotime, otherwise application-assigned. Composite PKs need explicit configuration.

```xml
<primary-key>
    <table name="SAG_\w+" strategy="generator" generator="default" />   <!-- 22-digit -->
    <table name="SYS_LOG"  strategy="generator" generator="ulid" />     <!-- needs ulid-creator -->
    <table name="BIZ_\w+"  strategy="sequence" sequence="SEQ_${tableName}" />
    <table name="TMP_\w+"  strategy="assign" />
    <table name="SYS_SEQ"  strategy="sequence" sequence="SEQ_SYS" force="true" />
</primary-key>
```

Generators: `default` (22-digit), `snowflake` (63-bit long, 18~19 digits), `nanotime` (26), `UUID`, `ULID` (sortable, requires com.github.f4b6a3:ulid-creator), `redis`. Numeric PKs that could overflow int/short/long are automatically upgraded to `BigInteger`. When several `table` rules match, **the last one wins**.

Business primary keys (rule-based codes like channel_type_date_seq) are configured via `business-primary-key` with `@substr/@case/@df` macros; extra attributes: `sequence-size`, `start`, and `generator` accepting `redis`, `snowflake` or `@bean(beanName)`:

```xml
<business-primary-key>
    <table name="SQLTOY_DEVICE_ORDER" column="ORDER_ID" generator="redis"
        signature="${psType}@case(${deviceType},PC,PC,NET,NT,OT)@df(yyMMdd)"
        related-columns="psType,deviceType" length="12" />
</business-primary-key>
```

## Data Type Mapping

Common types work out of the box (varchar→String, int→Integer, bigint→BigInteger, decimal→BigDecimal, date→LocalDate, oracle date→LocalDateTime, time→LocalTime, datetime→LocalDateTime, timestamp→Timestamp, timestamptz→OffsetDateTime, json/jsonb→String, vector→Float[], blob→byte[], generated columns carry `generatedType`). Built-in defaults (overridable): NUMBER/DECIMAL/NUMERIC precision 1..8 scale 0 → Integer; 9..64 no scale → Long. Custom mappings — later rules win, ranges supported:

```xml
<type-mapping>
    <sql-type native-types="NUMBER,DECIMAL,NUMERIC" precision="1..8" scale="0"
        jdbc-type="INTEGER" java-type="Integer" />
    <sql-type table-field="mytable.metadata" native-types="json"
        java-type="List&lt;MetaVO&gt;" import-types="com.demo.vo.MetaVO" />
    <sql-type table-field="ai_demo.embedding" native-types="vector" java-type="float[]" />
</type-mapping>
```

`table-field` restricts a rule to `table.column` (lowercase); `precision`/`scale` accept `min..max` or a single value; common java types (BigDecimal/BigInteger/LocalDateTime…) may be short-written, others need fully qualified names.

## Cascade (parent-child)

Whenever a primary key is referenced by another table's foreign key, @OneToMany is generated automatically — no configuration needed. The `cascade` element only refines child-table behavior and is **configured on the parent side**:

```xml
<cascade>
    <table name="STAFF_LOG" update-cascade="delete" load="STATUS=1" orderBy="createTime desc" />
    <table name="STAFF_PHOTO" update-cascade="ENABLED=0" />
</cascade>
```

| Attribute | Description |
|---|---|
| update-cascade | how existing child rows are handled on parent update: `delete`, or an SQL fragment (e.g. `ENABLED=0` to invalidate) |
| delete | cascade-delete child rows; **when omitted the foreign key's ON DELETE action decides** (only `ON DELETE CASCADE` emits `delete=true`), `true`/`false` overrides the FK |
| load | extra condition for cascade loading, e.g. `STATUS=1` |
| not-null-field | field used to detect whether joined child rows exist (emits notNullField on @OneToMany) |
| orderBy | sort order for cascade loading |

Tables without FK constraints produce no cascade.

## API Docs (api-doc)

Set `api-doc` on a `<task>`; aliases are normalized automatically so no hand-written templates are needed:

* `swagger-v3` (aliases `openapi3`/`openapi-3`/`openapi`/`springdoc`/`v3`) — the mainstream springdoc / OpenAPI 3 style, emits `io.swagger.v3.oas.annotations.media.@Schema`; non-null columns automatically get `requiredMode = Schema.RequiredMode.REQUIRED` and DB defaults become `example`;
* `swagger-v2` (aliases `springfox`/`v2`) — legacy springfox `@ApiModel`/`@ApiModelProperty` for existing projects;
* `none` (default) — no annotations; table/column remarks are emitted as javadoc, which smart-doc style tools pick up natively;
* `custom` — freemarker templates via the top-level `<api-doc>` element (class vars className/tableName/tableRemark, field vars colName/colRemark/nullable/fieldType/fieldName/defaultValue); mainstream cases are built in.

The legacy `swagger-model` attribute (v3/v2/false) still works but `api-doc` is preferred.

## Data Version (optimistic lock)

Declare per-table version columns, emitting field-level `@DataVersion` on entities (VO/DTO unaffected):

```xml
<data-version>
    <table name="SAG_ORDER" column="LAST_UPDATE"/>
    <table name="SYS_\w+" column="DATA_VERSION" start-date="true"/>
</data-version>
```

A pure `name` (letters/digits/underscore/dash) matches the table exactly ignoring case; values with regex metacharacters are treated as a full-match regex (last match wins). `column` is case-insensitive ignoring `_`/`-`; `start-date="true"` fits `yyyyMMdd+sequence` style versions; a missing column logs a warning without failing. Mutually exclusive with extra-annotations on the same field.

## Partition / MPP Annotation Switches

Declared as first-class attributes on `<tasks>` (both default true, XSD-validatable): `include-partition` controls `@Partition`/`@PartitionKey` (partition strategy extracted by the pg/mysql/oracle dialects), `include-mpp-table` controls `@MppTable` (ClickHouse/Doris/StarRocks engine metadata, consumed by sqltoy DDL generation). **Set them to false when targeting an older sqltoy version that lacks these annotations**, otherwise the generated code will not compile:

```xml
<tasks include-partition="false" include-mpp-table="false" ...>
```

Equivalent property forms (`include.partition` / `include.mpp-table`) are also accepted; the attribute form takes precedence.

## Properties (property element) & Built-ins

```xml
<property name="project.name" value="myProject" />          <!-- name+value -->
<property name="license.text">license text...</property>    <!-- name+body -->
<property file="src/main/resources/application.yml" />      <!-- bulk import properties/yml -->
```

* Referenced anywhere in the config via `${name}`; values support the `##{globa.identity}` global-instance placeholder; yml keys are flattened with `.` per level
* Unknown keys fall back to JVM system properties

Built-in properties:

| Property | Default | Description |
|---|---|---|
| include.schema | false | whether the generated @Entity carries the schema |
| field.support.linked.set | false | setters return this (chained assignment), unrelated to lombok-chain |
| generate.selectFields.class | false | generate the SelectField inner class |
| include.partition | true | emit @Partition/@PartitionKey (set false for old sqltoy) |
| include.mpp-table | true | emit @MppTable (set false for old sqltoy) |
| max.scale.length | — | scale truncation limit for type matching |
| skip.primary.constraint | false | skip PK-constraint-name extraction (mainly for postgresql) |
| fail-on-empty | true | fail the build when a task's include matches no tables (prevents silent zero-output builds); preferred form is `<tasks fail-on-empty="false">` (XSD hints), the property form stays supported |
| project.version / project.name | — | written into generated file headers; falls back to JVM system properties (`-Dproject.version=...`), and the line is omitted when unset |

## Special Characters in Comments

| Character | Handling | Purpose |
|---|---|---|
| `"` / `\` | escaped to `\"` / `\` | keeps annotation strings (`comment="..."`) valid Java literals |
| newline / CR / tab | normalized to a space | comments are flattened before entering single-line annotations (incl. sqlserver multi-line extended properties) |
| `*/` | replaced with `*\/` in javadoc contexts | prevents premature block-comment termination; kept as-is inside annotation strings (valid there) |
| `${}`, single quotes, `<>&` | kept as-is | template values are not re-parsed, no injection risk |

Applied uniformly to table and column remarks; a table exercising all of them (`QUICKVO_DEMO_SPECIAL_NOTE`) is part of the golden baselines.

## Architecture

### Module layout

Since 2.0 there is no global mutable static state (multi-module builds no longer interfere); the code is layered by responsibility:

```
org.sagacity.quickvo
├── QuickVoMojo        Maven plugin entry (goal: quickvo); failures throw MojoExecutionException
├── QuickVoCli         command-line entry (non-Maven scenarios)
├── QuickVoContext     per-run context (config/properties/logger/errors, instance-isolated)
├── QuickVoRunner      orchestrator: parse config → run tasks → summarize stats & errors
├── config/            XmlConfigParser (config + XSD) / PropertyResolver (${}, external properties, yml)
├── datasource/        DataSourceRegistry / DbSession / DbType / SchemaCatalogNormalizer (auto-correction)
├── dialect/           MetadataDialect interface + generic JdbcDialect
│                      + mysql/oracle/postgres/sqlserver/dm/clickhouse/hana/impala dialects
├── metadata/          MetadataReader: tables/columns/PKs/FKs/indexes/partitions/MPP/remarks
├── typemapping/       default mappings + oracle/pg-specific + custom rule merging
├── core/              TableProcessor / PrimaryKeyResolver / CascadeResolver /
│                      TypeMappingResolver / AnnotationCarryOver
├── render/            TemplateRenderer: FreeMarker rendering (7 built-in ftl templates)
├── gen/               ClassFileWriter: four write modes implementing regeneration safety
├── model/             configuration & metadata models
└── utils/             utilities (bilingual Msg logging etc.)
```

### Execution flow

```
quickvo.xml (XSD validation + ${} substitution)
  → per task: connect → detect db type & pick dialect (schema/catalog auto-correction)
      → include/exclude regex matches tables & views (no match → db-specific guidance
        + available catalog/schema listing)
      → per table extract metadata (columns/PKs/FKs/indexes/partitions/MPP/remarks)
      → process: type mapping → pk strategy → cascade → business pk → extra annotations
        → carry-over from previous files
      → FreeMarker render → ClassFileWriter writes per artifact write mode (safe merge)
  → summary: tables, files created/updated/unchanged, elapsed
  → any task failure or duplicate field → exception fails the Maven build
    (the historical fake-SUCCESS behavior is fixed)
```

### Extension points

* **New database**: implement `MetadataDialect` (usually extend `JdbcDialect` and override the differences) and register it in `Dialects` for the DbType; add type mappings in `typemapping`
* **Custom artifact style**: built-in templates live at classpath `templates/*.ftl` (entity/abstract-entity/parent-entity/dto/dto-abstract/dto-parent/entity-lombok); combine with `api-doc="custom"` for custom annotation templates
* **Local debugging**: run `org.sagacity.quickvo.debug.QuickVoDebug` from the test sources (point it at a quickvo.xml and set breakpoints in your IDE); outside Maven use `org.sagacity.quickvo.QuickVoCli` (args[0]=config path, args[1]=execution root)

## Build & Test

```sh
mvn install    # build and install locally (JDK 17+)
mvn test       # run tests
```

* **Pure logic unit tests**: 17 test classes covering config parsing, pk strategies, type mapping, schema/catalog normalization, annotation carry-over and file merging (plus golden integration tests)
* **Golden baselines**: 2 suites each for mysql/postgresql (basic + full type coverage) with byte-level comparison plus an idempotent second-run check; skipped automatically when the database is unreachable
* **Live dialect verification**: mysql9, postgresql16, oracle23, sqlserver2025, DM8, openGauss 6&7, vastbase, db2, clickhouse, doris, starrocks, tidb, sqlite, h2, hana, oceanbase, highgo (HighGo, PG12 kernel) (kingbase was blocked by a local container issue; it shares the generic JDBC dialect path verified by the others)

## 2.0 Release Notes

* **Fully compatible**: quickvo.xml format, region markers, serialVersionUID algorithm and template output are unchanged — upgrading is transparent
* **Architecture**: global static mutable state removed (multi-module builds no longer pollute each other); layered into config/datasource/dialect/typemapping/metadata/core/render/gen
* **Dialects**: `MetadataDialect` interface + default `JdbcDialect`; supporting a new database = adding one dialect class
* **Failure semantics fixed**: generation failures/duplicate fields now fail the Maven build (older versions only logged and still reported SUCCESS)
* **Fixes**: mysql column GROUP BY failure under only_full_group_by (broke partition-key/generated-column detection); view re-run incremental merge exception; sqlserver Chinese remarks lost, DATETIME2 emitting an illegal enum, 2025 VECTOR columns dropped; pg sequence-default PKs getting a wrong strategy; missing ULID generator mapping; composite-PK de-duplication breaking order; h2 CHARACTER VARYING illegal enum
* **New**: schema/catalog per-database auto-correction, sqltoy annotation carry-over, extra-annotations, data-version, include-partition/include-mpp-table switches, api-doc value normalization

## FAQ

| Symptom | Cause & fix |
|---|---|
| No tables matched | ① wrong include regex; ② wrong schema/catalog (case matters) — since 2.0 the log prints database-specific guidance plus the actual catalog/schema list; try removing both first. Since 2.0.4 this **fails the build by default** (no more silent zero-output); set `<tasks fail-on-empty="false">` when an empty task is expected |
| Duplicate fields in generated VO | schema/catalog failed to isolate multiple databases/users; fix per the cheat sheet |
| Log warns a table has no primary key | usually a schema/catalog mistake that matched a same-name table in another schema |
| Generated code fails to compile: @Partition/@MppTable not found | the target sqltoy is older; set `include-partition="false" include-mpp-table="false"` on tasks |
| Driver class not found at runtime | the driver jar must be declared in the **plugin's** `<dependencies>`, not the project's |
| Generation failed but Maven still SUCCESS | historical behavior; since 2.0 failures fail the build — upgrade |
| Hand-added annotations lost on every run | declare them via `extra-annotations`; @DataVersion/@Secure/@Tenant etc. are also carried over automatically |
| A database type maps to an unsuitable Java type | override with `type-mapping`, and please open an issue |

## Release Notes

- **2.0.4 (current)**: adds Java field-name validation at generation time (keywords / illegal identifiers / duplicate names reported with table and column details) and fails the build on empty tasks (`fail-on-empty`); fixes ${} placeholders not applied to task author/datasource/api-doc/fields-rid-prefix and `<datasource name>`, dirty values from YAML list/anchor properties, the literal "@version project.version" header line when project.version is absent, default configFile/baseDir parameters not working under Maven, raw PatternSyntaxException on invalid table regexes, and misleading warnings on view regeneration; hardens quickvo.xml parsing against XXE. See [RELEASE-NOTES-2.0.4.md](RELEASE-NOTES-2.0.4.md)
- **2.0.3**: fixes composite indexes keeping only the first column, json/text literal defaults misdetected as STORED generated columns, and unmapped native types producing uncompilable code; adds HighGo support and upgrades KingbaseES / TiDB to dedicated dialect paths. See [RELEASE-NOTES-2.0.3.md](RELEASE-NOTES-2.0.3.md)
- **2.0**: full architecture overhaul (no global static state, dialect interface), failures now fail the Maven build, schema/catalog auto-correction, sqltoy annotation carry-over, extra-annotations. See [RELEASE-NOTES-2.0.md](RELEASE-NOTES-2.0.md)

## License & Contribution

- Licensed under the **Apache License 2.0** (see [LICENSE](LICENSE)); free for commercial and personal use
- Bugs, questions and feature requests: <https://gitee.com/sagacity/maven-quickvo-plugin/issues>
- Code contributions: fork and open a Pull Request, please include the relevant tests
