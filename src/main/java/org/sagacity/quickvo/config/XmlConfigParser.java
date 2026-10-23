package org.sagacity.quickvo.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.sagacity.quickvo.QuickVoContext;
import org.sagacity.quickvo.datasource.DataSourceRegistry;
import org.sagacity.quickvo.model.BusinessIdConfig;
import org.sagacity.quickvo.model.CascadeModel;
import org.sagacity.quickvo.model.ColumnTypeMapping;
import org.sagacity.quickvo.model.ConfigModel;
import org.sagacity.quickvo.model.DataSourceModel;
import org.sagacity.quickvo.model.ExtraAnnotationConfig;
import org.sagacity.quickvo.model.PrimaryKeyStrategy;
import org.sagacity.quickvo.model.QuickModel;
import org.sagacity.quickvo.utils.FileUtil;
import org.sagacity.quickvo.utils.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.sagacity.quickvo.utils.Msg;

/**
 * quickvo.xml解析:task任务、主键策略、类型映射、级联、业务主键、api-doc、数据源。
 * 属性语义与历史版本完全兼容(含kebab/camel双写属性),解析结果不可再依赖全局状态。
 *
 * @author zhongxuchen
 */
public class XmlConfigParser {
	/** java-type常用类型别名归一(避免强制写全限定名) */
	private static final String[][] JAVA_TYPE_ALIASES = { { "BigDecimal", "java.math.BigDecimal" },
			{ "BigInteger", "java.math.BigInteger" }, { "LocalDate", "java.time.LocalDate" },
			{ "LocalDateTime", "java.time.LocalDateTime" }, { "LocalTime", "java.time.LocalTime" },
			{ "Timestamp", "java.sql.Timestamp" }, { "Date", "java.util.Date" },
			{ "OffsetDateTime", "java.time.OffsetDateTime" }, { "OffsetTime", "java.time.OffsetTime" },
			{ "ZonedDateTime", "java.time.ZonedDateTime" } };

	/** 解析结果:任务配置+数据源注册表 */
	public record ParseResult(ConfigModel config, DataSourceRegistry dataSources) {
	}

	/**
	 * 语言预扫描:在DOM解析前用正则直接从xml文本提取tasks上的log-language属性,
	 * 使首条日志即跟随语言设置;未配置时重置为自动检测
	 */
	private static void preInitLanguage(File xmlFile) {
		if (!xmlFile.exists()) {
			return;
		}
		for (String charset : new String[] { "UTF-8", "GBK" }) {
			try {
				String content = java.nio.file.Files.readString(xmlFile.toPath(), java.nio.charset.Charset.forName(charset));
				java.util.regex.Matcher taskAttr = java.util.regex.Pattern
						.compile("<tasks\\b[^>]*?\\blog-language=\"\\s*([A-Za-z]+)\\s*\"")
						.matcher(content);
				// 未命中时lang为null:重置为自动检测,清除同JVM前一模块遗留的覆盖及注释中假配置命中的影响
				org.sagacity.quickvo.utils.Msg
						.setLanguage(taskAttr.find() ? taskAttr.group(1) : null);
				return;
			} catch (Exception e) {
				// 尝试下一种编码
			}
		}
	}

	public static ParseResult parse(QuickVoContext context) throws Exception {
		Logger logger = context.getLogger();
		PropertyResolver properties = context.getProperties();
		File xmlFile = resolveConfigFile(context);
		// 语言预扫描须在首条日志之前,确保"检测到配置文件"等提示也跟随log-language设置
		preInitLanguage(xmlFile);
		if (!xmlFile.exists()) {
			logger.info(Msg.get("相对路径:","Relative path: ") + context.getBaseDir() + Msg.get(",配置文件:[",", config file: [") + context.getConfigFile()
					+ Msg.get("]不存在,请正确配置!","] not found, please configure correctly!"));
			throw new Exception(Msg.get("配置文件:", "Config file: ") + xmlFile.getAbsolutePath()
					+ Msg.get(" 不存在,请正确配置!", " does not exist, please configure correctly!"));
		}
		logger.info(Msg.get("检测到quickvo的配置文件:","Detected quickvo config: ") + xmlFile.getPath());
		ConfigModel configModel = new ConfigModel();
		DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		domFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		// XXE加固:quickvo.xml为本地配置文件,正常使用不含DOCTYPE与实体引用;
		// 显式拒绝DOCTYPE与外部实体,防止配置被注入实体读取本地文件或发起外连
		domFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		domFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		domFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
		Document doc = domBuilder.parse(xmlFile);
		Element root = doc.getDocumentElement();
		// 加载常量信息
		properties.loadProperties(root.getElementsByTagName("property"));

		// 任务设置
		Element tasks = (Element) root.getElementsByTagName("tasks").item(0);
		if (tasks == null) {
			throw new Exception(Msg.get("quickvo.xml缺少<tasks>节点,请检查配置文件结构(注意节点名区分大小写)!",
					"quickvo.xml is missing the <tasks> element, please check the config structure (element names are case-sensitive)!"));
		}
		// 日志输出语言:tasks上的log-language属性(zh|en|auto),缺省auto=按系统Locale;
		// 无配置时也要显式重置,纠正预扫描对XML注释中假配置的误命中,并清除同JVM前一模块的遗留覆盖
		String logLanguage = tasks.hasAttribute("log-language") ? StringUtil.trim(tasks.getAttribute("log-language"))
				: null;
		org.sagacity.quickvo.utils.Msg.setLanguage(logLanguage != null && !logLanguage.isEmpty() ? logLanguage : null);
		// 新增DDL类注解开关的正式属性形式(优先于property元素写法,XSD可校验可补全)
		properties.override("include.partition", tasks.hasAttribute("include-partition") ? tasks.getAttribute("include-partition") : null);
		properties.override("include.mpp-table",
				tasks.hasAttribute("include-mpp-table") ? tasks.getAttribute("include-mpp-table") : null);
		// 空任务开关同样提供tasks属性形式(XSD可校验可补全),优先于property元素写法
		properties.override("fail-on-empty",
				tasks.hasAttribute("fail-on-empty") ? tasks.getAttribute("fail-on-empty") : null);
		// 输出路径
		if (tasks.hasAttribute("dist")) {
			configModel.setTargetDir(properties.replace(tasks.getAttribute("dist")));
		} else {
			configModel.setTargetDir(context.getBaseDir().toString());
		}
		// 判断指定的目标路径是否是根路径
		if (!FileUtil.isRootPath(configModel.getTargetDir())) {
			configModel.setTargetDir(
					FileUtil.skipPath(context.getBaseDir().toString(), configModel.getTargetDir()));
		}
		// 设置抽象类路径
		if (tasks.hasAttribute("abstractPath")) {
			configModel.setAbstractPath(properties.replace(tasks.getAttribute("abstractPath")));
		} else if (tasks.hasAttribute("abstract-path")) {
			configModel.setAbstractPath(properties.replace(tasks.getAttribute("abstract-path")));
		}
		// 设置编码格式
		if (tasks.hasAttribute("encoding")) {
			configModel.setEncoding(properties.replace(tasks.getAttribute("encoding")));
		}
		NodeList quickVOs = tasks.getElementsByTagName("task");
		List<QuickModel> quickModels = new ArrayList<>();
		for (int i = 0; i < quickVOs.getLength(); i++) {
			Element quickvo = (Element) quickVOs.item(i);
			boolean active = true;
			if (quickvo.hasAttribute("active")) {
				active = Boolean.parseBoolean(quickvo.getAttribute("active"));
			}
			// 生效的任务
			if (active) {
				quickModels.add(parseTask(quickvo, configModel, context));
			}
		}
		if (quickModels.isEmpty()) {
			logger.info(Msg.get("没有激活的任务可以执行!","No active tasks to execute!"));
			return new ParseResult(null, new DataSourceRegistry());
		}
		configModel.setTasks(quickModels);
		parsePrimaryKey(root, configModel);
		configModel.setTypeMapping(parseTypeMapping(root));
		parseCascade(root, configModel);
		DataSourceRegistry registry = parseDatasources(root, properties);
		parseBusinessPrimaryKey(root, configModel, properties);
		parseExtraAnnotations(root, configModel);
		parseDataVersion(root, configModel);
		parseApiDoc(root, configModel);
		validate(quickModels, registry, logger);
		return new ParseResult(configModel, registry);
	}

	/** classpath: 前缀(配置文件定位用) */
	private static final String CLASSPATH_PREFIX = "classpath:";

	/**
	 * 表名正则预检:非法正则(如通配符风格"*_LOG"的*在行首悬空)在配置解析期报错并给出
	 * 写法指引,而不是提取表阶段抛裸PatternSyntaxException、难以定位到具体属性
	 */
	private static void validateTableRegex(String attrName, String regex) {
		try {
			java.util.regex.Pattern.compile(regex);
		} catch (java.util.regex.PatternSyntaxException e) {
			throw new IllegalArgumentException(Msg.get("属性", "Attribute ") + attrName + "=\"" + regex
					+ Msg.get("\" 不是合法的表名正则(通配请用正则语法,如 *_LOG 应写 .*_LOG),原因: ",
							"=\" is not a valid table-name regex (use regex syntax, e.g. .*_LOG instead of *_LOG), reason: ")
					+ e.getDescription(), e);
		}
	}

	/**
	 * 配置文件定位:classpath:前缀先走类路径,再按执行根路径与工程资源目录回退;
	 * 不带前缀时相对执行根路径(不存在时按原始路径重试)
	 */
	private static File resolveConfigFile(QuickVoContext context) {
		String configPath = context.getConfigFile().trim();
		if (configPath.toLowerCase(java.util.Locale.ROOT).startsWith(CLASSPATH_PREFIX)) {
			File resolved = FileUtil.getFile(configPath);
			if (resolved != null) {
				return resolved;
			}
			// maven插件执行期的TCCL是插件自身的realm,工程的src/main/resources不在其中,
			// classpath:前缀在maven环境几乎必然取不到;按执行根路径与工程资源目录回退,
			// 使默认值classpath:quickvo.xml可直接使用(历史行为是拼出含"classpath:"的非法文件名后报错)
			String relative = configPath.substring(CLASSPATH_PREFIX.length()).trim();
			while (relative.startsWith("/") || relative.startsWith("\\")) {
				relative = relative.substring(1);
			}
			if (!relative.isEmpty()) {
				for (String candidate : new String[] { relative, "src/main/resources/" + relative }) {
					File file = new File(FileUtil.skipPath(context.getBaseDir().toString(), candidate));
					if (file.isFile()) {
						return file;
					}
				}
			}
			// 仍取不到:按执行根路径给出待查文件,让错误信息指向实际查找位置而非"classpath:xxx"
			return new File(FileUtil.skipPath(context.getBaseDir().toString(), relative));
		}
		File xmlFile = new File(FileUtil.skipPath(context.getBaseDir().toString(), configPath));
		// 文件不存在则忽视相对路径进行重试
		if (!xmlFile.exists()) {
			File directFile = new File(configPath);
			if (directFile.exists()) {
				return directFile;
			}
		}
		return xmlFile;
	}

	private static QuickModel parseTask(Element quickvo, ConfigModel configModel, QuickVoContext context) {
		PropertyResolver properties = context.getProperties();
		QuickModel quickModel = new QuickModel();
		if (quickvo.hasAttribute("author")) {
			quickModel.setAuthor(properties.replace(quickvo.getAttribute("author")));
		}
		if (quickvo.hasAttribute("datasource")) {
			quickModel.setDataSource(properties.replace(quickvo.getAttribute("datasource")));
		} else if (quickvo.hasAttribute("dataSource")) {
			quickModel.setDataSource(properties.replace(quickvo.getAttribute("dataSource")));
		}
		if (quickvo.hasAttribute("swagger-model")) {
			quickModel.setApiDoc(normalizeApiDoc(properties.replace(quickvo.getAttribute("swagger-model"))));
		}
		if (quickvo.hasAttribute("api-doc")) {
			quickModel.setApiDoc(normalizeApiDoc(properties.replace(quickvo.getAttribute("api-doc"))));
		}
		// 字段统一剔除的前缀(与列名比较两侧统一Locale.ROOT,土耳其语等locale下I的小写形态才不致错位)
		if (quickvo.hasAttribute("fields-rid-prefix")) {
			quickModel.setFieldRidPrefix(
					properties.replace(quickvo.getAttribute("fields-rid-prefix")).toLowerCase(java.util.Locale.ROOT));
		}
		if (quickvo.hasAttribute("include")) {
			// *表示全部,等同于匹配所有表
			if (!quickvo.getAttribute("include").equals("*")) {
				quickModel.setIncludeTables(properties.replace(quickvo.getAttribute("include")));
				if (!quickModel.getIncludeTables().startsWith("(?i)")) {
					quickModel.setIncludeTables("(?i)".concat(quickModel.getIncludeTables()));
				}
				validateTableRegex("include", quickModel.getIncludeTables());
			}
		} else {
			// XSD契约include为必填(use="required"),运行时同样强制:漏配时按"匹配全部"静默
			// 生成整库所有表,风险远大于显式报错;需全量生成请显式写include="*"
			throw new IllegalArgumentException(
					Msg.get("任务缺少include属性(表名匹配正则,全量生成请写include=\"*\"):<task include=\"^SAG_\\w+\">",
							"Task is missing the include attribute (table name regex; use include=\"*\" to match all tables): <task include=\"^SAG_\\w+\">"));
		}
		// 排除的表
		if (quickvo.hasAttribute("exclude")) {
			quickModel.setExcludeTables(properties.replace(quickvo.getAttribute("exclude")));
			if (!quickModel.getExcludeTables().startsWith("(?i)")) {
				quickModel.setExcludeTables("(?i)".concat(quickModel.getExcludeTables()));
			}
			validateTableRegex("exclude", quickModel.getExcludeTables());
		}
		// 实体bean
		NodeList nodeList = quickvo.getElementsByTagName("entity");
		if (nodeList.getLength() > 0) {
			Element entity = (Element) nodeList.item(0);
			quickModel.setEntityPackage(properties.replace(entity.getAttribute("package")));
			if (entity.hasAttribute("substr")) {
				quickModel.setEntitySubstr(properties.replace(entity.getAttribute("substr")));
			}
			// 是否包含抽象类
			if (entity.hasAttribute("has-abstract")) {
				quickModel.setHasAbstractEntity(Boolean.parseBoolean(entity.getAttribute("has-abstract")));
			}
			// 是否支持lombok
			if (entity.hasAttribute("lombok")) {
				quickModel.setEntityLombok(Boolean.parseBoolean(entity.getAttribute("lombok")));
				if (quickModel.isEntityLombok()) {
					quickModel.setHasAbstractEntity(false);
				}
			}
			if (entity.hasAttribute("lombok-chain")) {
				quickModel.setEntityLombokChain(Boolean.parseBoolean(entity.getAttribute("lombok-chain")));
				if (quickModel.isEntityLombokChain() && !quickModel.isEntityLombok()) {
					quickModel.setEntityLombok(true);
					quickModel.setHasAbstractEntity(false);
				}
			}
			if (entity.hasAttribute("name")) {
				quickModel.setEntityName(properties.replace(entity.getAttribute("name")));
			} else {
				quickModel.setEntityName("#{subName}");
			}
			// 存放路径
			if (entity.hasAttribute("to-dir")) {
				quickModel.setEntityPath(FileUtil.skipPath(context.getBaseDir().toString(),
						properties.replace(entity.getAttribute("to-dir"))));
			} else {
				quickModel.setEntityPath(configModel.getTargetDir());
			}
			// 是否有父类
			if (entity.hasAttribute("extends")) {
				quickModel.setEntityExtends(properties.replace(entity.getAttribute("extends")));
				if (entity.hasAttribute("skip-extends-fields")) {
					quickModel.setSkipEntityExtendsFields(properties
							.replace(entity.getAttribute("skip-extends-fields")).toLowerCase().replaceAll("\\s+", "")
							.split("\\,"));
				}
			}
			quickModel.setHasEntity(true);
		}
		// vo(单独vo时意义转为entity兼容旧模式),如跟entity一起使用等于dto的概念
		nodeList = quickvo.getElementsByTagName("vo");
		if (nodeList.getLength() > 0) {
			Element vo = (Element) nodeList.item(0);
			// 存在entity和dto 形式,这时的vo等同于dto
			quickModel.setVoPackage(properties.replace(vo.getAttribute("package")));
			if (vo.hasAttribute("substr")) {
				quickModel.setVoSubstr(properties.replace(vo.getAttribute("substr")));
			}
			// 是否包含抽象类
			if (vo.hasAttribute("has-abstract")) {
				quickModel.setHasAbstractVO(Boolean.parseBoolean(vo.getAttribute("has-abstract")));
			}
			// 存放路径
			if (vo.hasAttribute("to-dir")) {
				quickModel.setVoPath(FileUtil.skipPath(context.getBaseDir().toString(),
						properties.replace(vo.getAttribute("to-dir"))));
			} else {
				quickModel.setVoPath(configModel.getTargetDir());
			}
			// 是否支持lombok
			if (vo.hasAttribute("lombok")) {
				quickModel.setLombok(Boolean.parseBoolean(vo.getAttribute("lombok")));
				if (quickModel.isLombok()) {
					quickModel.setHasAbstractVO(false);
				}
			}
			if (vo.hasAttribute("lombok-chain")) {
				quickModel.setLombokChain(Boolean.parseBoolean(vo.getAttribute("lombok-chain")));
				// 设置lombok-chain默认设置了isLombok
				if (quickModel.isLombokChain() && !quickModel.isLombok()) {
					quickModel.setLombok(true);
					quickModel.setHasAbstractVO(false);
				}
			}
			if (vo.hasAttribute("name")) {
				quickModel.setVoName(properties.replace(vo.getAttribute("name")));
			} else {
				quickModel.setVoName("#{subName}");
			}
			// 是否有父类
			if (vo.hasAttribute("extends")) {
				quickModel.setVoExtends(properties.replace(vo.getAttribute("extends")));
				if (vo.hasAttribute("skip-extends-fields")) {
					quickModel.setSkipVOExtendsFields(properties
							.replace(vo.getAttribute("skip-extends-fields")).toLowerCase().replaceAll("\\s+", "")
							.split("\\,"));
				}
			}
			quickModel.setHasVO(true);
		}
		return quickModel;
	}

	private static void parsePrimaryKey(Element root, ConfigModel configModel) {
		NodeList nodeList = root.getElementsByTagName("primary-key");
		if (nodeList.getLength() == 0) {
			return;
		}
		NodeList tables = ((Element) nodeList.item(0)).getElementsByTagName("table");
		for (int i = 0; i < tables.getLength(); i++) {
			Element table = (Element) tables.item(i);
			String name = table.getAttribute("name");
			String sequence = null;
			// 默认为赋值模式
			String strategy = "assign";
			String generator = null;
			if (table.hasAttribute("strategy")) {
				strategy = table.getAttribute("strategy");
			}
			if (table.hasAttribute("sequence")) {
				sequence = table.getAttribute("sequence");
				strategy = "sequence";
			}
			// 生成器模式优先
			if (table.hasAttribute("generator")) {
				generator = table.getAttribute("generator");
				strategy = "generator";
				sequence = null;
				if (generator.equalsIgnoreCase("default")) {
					generator = org.sagacity.quickvo.core.PrimaryKeyResolver.PK_DEFAULT_GENERATOR;
				}
			}
			PrimaryKeyStrategy primaryKeyStrategy = new PrimaryKeyStrategy(name, strategy, sequence, generator);
			// force没有必要
			if (table.hasAttribute("force")) {
				primaryKeyStrategy.setForce(Boolean.parseBoolean(table.getAttribute("force")));
			}
			configModel.addPkGeneratorStrategy(primaryKeyStrategy);
		}
	}

	private static List<ColumnTypeMapping> parseTypeMapping(Element root) {
		List<ColumnTypeMapping> typeMapping = new ArrayList<>();
		addDefaultTypeMapping(typeMapping);
		NodeList nodeList = root.getElementsByTagName("type-mapping");
		if (nodeList.getLength() == 0) {
			return typeMapping;
		}
		NodeList jdbcTypes = ((Element) nodeList.item(0)).getElementsByTagName("sql-type");
		for (int i = 0; i < jdbcTypes.getLength(); i++) {
			Element type = (Element) jdbcTypes.item(i);
			ColumnTypeMapping colTypeMapping = new ColumnTypeMapping();
			// 兼容老版本
			colTypeMapping.putNativeTypes(type.getAttribute("native-types").split("\\,"));
			if (type.hasAttribute("table-field")) {
				colTypeMapping.setTableField(type.getAttribute("table-field").toLowerCase());
			}
			if (type.hasAttribute("jdbc-type")) {
				colTypeMapping.setJdbcType(type.getAttribute("jdbc-type"));
			}
			if (type.hasAttribute("import-types")) {
				colTypeMapping.setImportTypes(type.getAttribute("import-types"));
			}
			if (type.hasAttribute("precision") && StringUtil.isNotBlank(type.getAttribute("precision"))) {
				// 区间格式:min..max、min..、..max或单值n(单值=精确等于,空白侧不设限)
				int[] range = parseRangeValue(type.getAttribute("precision"), "precision");
				colTypeMapping.setPrecisionMin(range[0]);
				colTypeMapping.setPrecisionMax(range[1]);
			}
			// 小数位
			if (type.hasAttribute("scale") && StringUtil.isNotBlank(type.getAttribute("scale"))) {
				int[] range = parseRangeValue(type.getAttribute("scale"), "scale");
				colTypeMapping.setScaleMin(range[0]);
				colTypeMapping.setScaleMax(range[1]);
			}
			// 对应的java类型(对常规类型进行容错性处理)
			String javaType = canonicalJavaType(type.getAttribute("java-type"));
			colTypeMapping.setJavaType(javaType);
			if (javaType.indexOf(".") != -1) {
				colTypeMapping.setResultType(javaType.substring(javaType.lastIndexOf(".") + 1));
			} else {
				colTypeMapping.setResultType(javaType);
			}
			typeMapping.add(colTypeMapping);
		}
		return typeMapping;
	}

	private static String canonicalJavaType(String javaType) {
		for (String[] alias : JAVA_TYPE_ALIASES) {
			if (javaType.equalsIgnoreCase(alias[0])) {
				return alias[1];
			}
		}
		return javaType;
	}

	/** 默认类型匹配:oracle的NUMBER按精度区分Integer/Long */
	private static void addDefaultTypeMapping(List<ColumnTypeMapping> typeMapping) {
		ColumnTypeMapping integerMapping = new ColumnTypeMapping();
		integerMapping.putNativeTypes(new String[] { "NUMBER", "DECIMAL", "NUMERIC" });
		integerMapping.setJdbcType("INTEGER");
		integerMapping.setJavaType("Integer");
		integerMapping.setPrecisionMax(8);
		integerMapping.setPrecisionMin(1);
		integerMapping.setScaleMin(0);
		integerMapping.setScaleMax(0);
		integerMapping.setResultType("Integer");
		typeMapping.add(integerMapping);
		ColumnTypeMapping longMapping = new ColumnTypeMapping();
		longMapping.putNativeTypes(new String[] { "NUMBER", "DECIMAL", "NUMERIC" });
		// Long对应的标准JDBC类型为BIGINT(写INTEGER会使sqltoy DDL同步按整型收窄列宽)
		longMapping.setJdbcType("BIGINT");
		longMapping.setJavaType("Long");
		longMapping.setPrecisionMax(64);
		longMapping.setPrecisionMin(9);
		longMapping.setScaleMin(0);
		longMapping.setScaleMax(0);
		longMapping.setResultType("Long");
		typeMapping.add(longMapping);
	}

	private static void parseCascade(Element root, ConfigModel configModel) {
		NodeList nodeList = root.getElementsByTagName("cascade");
		for (int m = 0; m < nodeList.getLength(); m++) {
			Element mainCascade = (Element) nodeList.item(m);
			NodeList cascades = mainCascade.getElementsByTagName("table");
			List<CascadeModel> cascadeModelList = new ArrayList<>();
			for (int i = 0; i < cascades.getLength(); i++) {
				Element cascadeElt = (Element) cascades.item(i);
				CascadeModel cascade = new CascadeModel();
				cascade.setTableName(cascadeElt.getAttribute("name"));
				if (cascadeElt.hasAttribute("delete")) {
					cascade.setDelete(Boolean.parseBoolean(cascadeElt.getAttribute("delete")));
				}
				// lazy load 的特定sql,可以自行定义,如:enabled=1附加条件
				if (cascadeElt.hasAttribute("load")) {
					cascade.setLoad(cascadeElt.getAttribute("load"));
				}
				// 新的配置模式
				if (cascadeElt.hasAttribute("update-cascade")) {
					cascade.setUpdateSql(cascadeElt.getAttribute("update-cascade"));
				}
				// 排序
				if (cascadeElt.hasAttribute("orderBy")) {
					cascade.setOrderBy(cascadeElt.getAttribute("orderBy"));
				} else if (cascadeElt.hasAttribute("order-by")) {
					cascade.setOrderBy(cascadeElt.getAttribute("order-by"));
				}
				// 判断join子表是否存在数据的字段(@OneToMany(notNullField=..))
				if (cascadeElt.hasAttribute("not-null-field")) {
					cascade.setNotNullField(cascadeElt.getAttribute("not-null-field"));
				}
				cascadeModelList.add(cascade);
			}
			configModel.addCascadeConfig(cascadeModelList);
		}
	}

	private static DataSourceRegistry parseDatasources(Element root, PropertyResolver properties) throws Exception {
		DataSourceRegistry registry = new DataSourceRegistry();
		NodeList datasourceElts = root.getElementsByTagName("datasource");
		if (datasourceElts == null || datasourceElts.getLength() == 0) {
			throw new Exception(Msg.get("没有配置相应的数据库","No datasource configured"));
		}
		for (int m = 0; m < datasourceElts.getLength(); m++) {
			Element datasourceElt = (Element) datasourceElts.item(m);
			DataSourceModel dbModel = new DataSourceModel();
			String name = null;
			if (datasourceElt.hasAttribute("name")) {
				// 与<task datasource="${xxx}">同步解析,否则注册名与任务引用名对不上(validate阶段报未定义)
				name = properties.replace(datasourceElt.getAttribute("name"));
			}
			if (datasourceElt.hasAttribute("catalog")) {
				dbModel.setCatalog(properties.replace(datasourceElt.getAttribute("catalog")));
			}
			if (datasourceElt.hasAttribute("schema")) {
				dbModel.setSchema(properties.replace(datasourceElt.getAttribute("schema")));
			}
			dbModel.setUrl(properties.replace(datasourceElt.getAttribute("url")));
			dbModel.setDriver(properties.replace(datasourceElt.getAttribute("driver")));
			dbModel.setUsername(properties.replace(datasourceElt.getAttribute("username")));
			dbModel.setPassword(properties.replace(datasourceElt.getAttribute("password")));
			registry.register(name, dbModel);
		}
		return registry;
	}

	private static void parseBusinessPrimaryKey(Element root, ConfigModel configModel,
			PropertyResolver properties) {
		NodeList nodeList = root.getElementsByTagName("business-primary-key");
		if (nodeList.getLength() == 0) {
			return;
		}
		NodeList tables = ((Element) nodeList.item(0)).getElementsByTagName("table");
		for (int m = 0; m < tables.getLength(); m++) {
			Element tableElt = (Element) tables.item(m);
			BusinessIdConfig bizIdConfig = new BusinessIdConfig();
			bizIdConfig.setTableName(tableElt.getAttribute("name"));
			bizIdConfig.setColumn(tableElt.getAttribute("column"));
			bizIdConfig.setGenerator(tableElt.getAttribute("generator"));
			if (bizIdConfig.getGenerator() != null && bizIdConfig.getGenerator().equalsIgnoreCase("redis")) {
				bizIdConfig.setGenerator(org.sagacity.quickvo.core.PrimaryKeyResolver.PK_REDIS_ID_GENERATOR);
			}
			if (tableElt.hasAttribute("related-column")) {
				bizIdConfig.setRelatedColumns(unifiedSplit(tableElt.getAttribute("related-column")));
			} else if (tableElt.hasAttribute("related-columns")) {
				bizIdConfig.setRelatedColumns(unifiedSplit(tableElt.getAttribute("related-columns")));
			}
			if (tableElt.hasAttribute("signature")) {
				bizIdConfig.setSignature(tableElt.getAttribute("signature"));
			}
			if (tableElt.hasAttribute("length")) {
				bizIdConfig.setLength(parseIntAttr(tableElt.getAttribute("length"), "length", bizIdConfig.getTableName()));
			}
			if (tableElt.hasAttribute("sequence-size")) {
				bizIdConfig.setSequenceSize(
						parseIntAttr(tableElt.getAttribute("sequence-size"), "sequence-size", bizIdConfig.getTableName()));
			}
			if (tableElt.hasAttribute("start")) {
				bizIdConfig.setStart(parseLongAttr(tableElt.getAttribute("start"), "start", bizIdConfig.getTableName()));
			}
			configModel.addBusinessId(bizIdConfig);
		}
	}

	/** 分隔符统一(支持中英文逗号和分号),空白段过滤(全空返回null,模板按未配置跳过输出) */
	private static String[] unifiedSplit(String value) {
		// \uFF0C为中文全角逗号
		List<String> result = new ArrayList<>();
		for (String part : value.replaceAll("\\;", ",").replaceAll("\\\uFF0C", ",").split("\\,")) {
			if (StringUtil.isNotBlank(part)) {
				result.add(part.trim());
			}
		}
		return result.isEmpty() ? null : result.toArray(new String[0]);
	}

	/**
	 * 解析extra-annotations:表名正则匹配的类级/字段级扩展注解及其import,
	 * 用于稳定输出@Sharding、@DataVersion等无法从表结构推导的注解
	 */
	private static void parseExtraAnnotations(Element root, ConfigModel configModel) {
		NodeList nodeList = root.getElementsByTagName("extra-annotations");
		for (int i = 0; i < nodeList.getLength(); i++) {
			NodeList tables = ((Element) nodeList.item(i)).getElementsByTagName("table");
			for (int j = 0; j < tables.getLength(); j++) {
				Element tableElt = (Element) tables.item(j);
				ExtraAnnotationConfig config = new ExtraAnnotationConfig();
				config.setTableName(tableElt.getAttribute("name"));
				validateTableRegex("extra-annotations/table@name", config.getTableName());
				NodeList classElts = tableElt.getElementsByTagName("class-annotation");
				for (int k = 0; k < classElts.getLength(); k++) {
					String content = ((Element) classElts.item(k)).getTextContent().trim();
					if (StringUtil.isNotBlank(content)) {
						config.addClassAnnotation(content);
					}
				}
				NodeList fieldElts = tableElt.getElementsByTagName("field-annotation");
				for (int k = 0; k < fieldElts.getLength(); k++) {
					Element fieldElt = (Element) fieldElts.item(k);
					String column = fieldElt.getAttribute("column");
					String content = fieldElt.getTextContent().trim();
					if (StringUtil.isNotBlank(column) && StringUtil.isNotBlank(content)) {
						config.addFieldAnnotation(column, content);
					}
				}
				NodeList importElts = tableElt.getElementsByTagName("imports");
				if (importElts.getLength() > 0) {
					Element importElt = (Element) importElts.item(0);
					String value = importElt.hasAttribute("value") ? importElt.getAttribute("value")
							: importElt.getTextContent();
					if (StringUtil.isNotBlank(value)) {
						List<String> imports = new ArrayList<>();
						for (String imp : value.split("\\;|\\,")) {
							if (StringUtil.isNotBlank(imp)) {
								imports.add(imp.trim());
							}
						}
						config.addImports(imports);
					}
				}
				configModel.addExtraAnnotationConfig(config);
			}
		}
	}

	/** data-version配置:表正则+版本列+start-date(yyyyMMdd+流水形式) */
	private static void parseDataVersion(Element root, ConfigModel configModel) {
		NodeList nodeList = root.getElementsByTagName("data-version");
		if (nodeList.getLength() == 0) {
			return;
		}
		NodeList tables = ((Element) nodeList.item(0)).getElementsByTagName("table");
		for (int i = 0; i < tables.getLength(); i++) {
			Element tableElt = (Element) tables.item(i);
			configModel.addDataVersion(new org.sagacity.quickvo.model.DataVersionConfig(
					tableElt.getAttribute("name"), tableElt.getAttribute("column"),
					Boolean.parseBoolean(tableElt.getAttribute("start-date"))));
		}
	}

	private static void parseApiDoc(Element root, ConfigModel configModel) {
		NodeList nodeList = root.getElementsByTagName("api-doc");
		if (nodeList.getLength() == 0) {
			return;
		}
		Element apiDocElt = (Element) nodeList.item(0);
		NodeList importElts = apiDocElt.getElementsByTagName("imports");
		if (importElts.getLength() > 0) {
			Element impElt = (Element) importElts.item(0);
			String importsClass = impElt.hasAttribute("value") ? impElt.getAttribute("value")
					: impElt.getTextContent();
			if (StringUtil.isNotBlank(importsClass)) {
				String[] imps = importsClass.split("\\;");
				for (int i = 0; i < imps.length; i++) {
					String imp = imps[i].trim();
					// 剔除掉import
					imps[i] = StringUtil.matches(imp, "(?i)import\\s+") ? imp.substring(6).trim() : imp;
				}
				configModel.setDocApiImports(imps);
			}
		}
		NodeList fieldTempElt = apiDocElt.getElementsByTagName("doc-field-template");
		if (fieldTempElt.getLength() > 0) {
			String docTemplate = ((Element) fieldTempElt.item(0)).getTextContent();
			if (StringUtil.isNotBlank(docTemplate)) {
				configModel.setDocApiFieldTemplate(docTemplate.trim());
			}
		}
		NodeList classTempElt = apiDocElt.getElementsByTagName("doc-class-template");
		if (classTempElt.getLength() > 0) {
			String docTemplate = ((Element) classTempElt.item(0)).getTextContent();
			if (StringUtil.isNotBlank(docTemplate)) {
				configModel.setDocApiClassTemplate(docTemplate.trim());
			}
		}
	}

	/**
	 * 区间属性(precision/scale)解析:支持min..max、min..、..max、单值n四种形式;
	 * 单值语义=精确等于,空白侧不设限(-1);畸形值(如误写单点"1.8")报配置错误并指明属性与原值,
	 * 取代历史split("\\..")把"1.8"静默解析成[1]的错义行为
	 */
	private static int[] parseRangeValue(String raw, String attrName) {
		String value = raw.trim();
		int idx = value.indexOf("..");
		String minStr = idx < 0 ? value : value.substring(0, idx).trim();
		String maxStr = idx < 0 ? value : value.substring(idx + 2).trim();
		int min = minStr.isEmpty() ? -1 : parseRangeInt(minStr, attrName, raw);
		int max = maxStr.isEmpty() ? -1 : parseRangeInt(maxStr, attrName, raw);
		if (min >= 0 && max >= 0 && max < min) {
			throw new IllegalArgumentException(Msg.get("type-mapping的" + attrName + "区间非法(min>max):", "Invalid " + attrName
					+ " range (min>max) in type-mapping: ") + raw);
		}
		return new int[] { min, max };
	}

	private static int parseRangeInt(String numStr, String attrName, String raw) {
		try {
			return Integer.valueOf(numStr);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(Msg.get("type-mapping的" + attrName + "属性值非法(应为min..max区间或单值整数):",
					"Invalid " + attrName + " value in type-mapping (expect min..max range or single integer): ")
					+ raw);
		}
	}

	/** 整型属性解析:非法值报配置错误并指明表名/属性/原值,取代裸parseInt抛无上下文的NumberFormatException */
	private static int parseIntAttr(String raw, String attrName, String tableName) {
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(Msg.get("表" + tableName + "的" + attrName + "属性值非法(应为整数):",
					"Invalid " + attrName + " (expect integer) for table " + tableName + ": ") + raw);
		}
	}

	/** 长整型属性解析:非法值报配置错误并指明表名/属性/原值 */
	private static long parseLongAttr(String raw, String attrName, String tableName) {
		try {
			return Long.parseLong(raw.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(Msg.get("表" + tableName + "的" + attrName + "属性值非法(应为整数):",
					"Invalid " + attrName + " (expect integer) for table " + tableName + ": ") + raw);
		}
	}

	/**
	 * api-doc取值归一:统一主流命名的别名写法,免得用户为不同文档生态手写custom模板。
	 * swagger-v2系(springfox/@ApiModel): swagger-v2、v2、springfox
	 * swagger-v3系(springdoc/OpenAPI3/@Schema): swagger-v3、v3、openapi3、openapi-3、openapi、springdoc
	 * 关闭: none、false、off;其余原样透传(视为custom自定义模板)
	 */
	private static String normalizeApiDoc(String raw) {
		String value = raw == null ? "none" : raw.trim().toLowerCase();
		switch (value) {
		case "swagger-v2":
		case "v2":
		case "springfox":
			return "swagger-v2";
		case "swagger-v3":
		case "v3":
		case "openapi3":
		case "openapi-3":
		case "openapi":
		case "springdoc":
			return "swagger-v3";
		case "":
		case "none":
		case "false":
		case "off":
			return "none";
		default:
			return value;
		}
	}

	/** 启动期校验:task引用的数据源必须已定义(历史行为是连接时才报错,提前暴露配置问题);
	 * datasource属性缺省仅允许单数据源场景(缺省匹配),多数据源时必须显式指定,
	 * 否则任务会在执行期被静默跳过(零文件生成构建假成功) */
	private static void validate(List<QuickModel> tasks, DataSourceRegistry registry, Logger logger)
			throws Exception {
		if (registry.isEmpty()) {
			throw new Exception(Msg.get("没有配置相应的数据库","No datasource configured"));
		}
		for (QuickModel task : tasks) {
			String dataSource = task.getDataSource();
			if (StringUtil.isNotBlank(dataSource)) {
				if (!registry.contains(dataSource)) {
					throw new Exception(Msg.get("任务<task datasource=\"", "Task <task datasource=\"") + dataSource
							+ Msg.get("\"> 引用的数据源未在<datasource name=\"", "\"> references a datasource not defined in <datasource name=\"")
							+ dataSource + Msg.get("\">中定义,请检查quickvo.xml!", "\">, please check quickvo.xml!"));
				}
			} else if (registry.size() > 1) {
				throw new Exception(Msg.get("当前定义了", "There are ") + registry.size()
						+ Msg.get("个数据源,任务缺少datasource属性无法缺省匹配,请在<task datasource=\"名称\">上显式指定!",
								" datasources defined; task missing the datasource attribute cannot default-match, please specify <task datasource=\"name\"> explicitly!"));
			}
		}
	}
}
