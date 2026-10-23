package org.sagacity.quickvo;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import org.sagacity.quickvo.config.XmlConfigParser;
import org.sagacity.quickvo.config.XmlConfigParser.ParseResult;
import org.sagacity.quickvo.core.CascadeResolver;
import org.sagacity.quickvo.core.PrimaryKeyResolver;
import org.sagacity.quickvo.core.TableProcessor;
import org.sagacity.quickvo.datasource.DataSourceRegistry;
import org.sagacity.quickvo.datasource.DbSession;
import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.gen.ClassFileWriter;
import org.sagacity.quickvo.gen.ClassFileWriter.SerialKind;
import org.sagacity.quickvo.gen.ClassFileWriter.WriteMode;
import org.sagacity.quickvo.metadata.MetadataReader;
import org.sagacity.quickvo.model.BusinessIdConfig;
import org.sagacity.quickvo.model.ConfigModel;
import org.sagacity.quickvo.model.PrimaryKeyStrategy;
import org.sagacity.quickvo.model.QuickColMeta;
import org.sagacity.quickvo.model.QuickModel;
import org.sagacity.quickvo.model.QuickVO;
import org.sagacity.quickvo.model.TableColumnMeta;
import org.sagacity.quickvo.model.TableConstractModel;
import org.sagacity.quickvo.model.TableMeta;
import org.sagacity.quickvo.render.TemplateRenderer;
import org.sagacity.quickvo.render.TemplateRenderer.Template;
import org.sagacity.quickvo.utils.CommonUtils;
import org.sagacity.quickvo.utils.FileUtil;
import org.sagacity.quickvo.utils.StringUtil;
import org.sagacity.quickvo.utils.Msg;

/**
 * quickvo总控:解析配置→循环执行任务(连接库→提取元数据→加工→渲染写盘)→汇总错误。
 * 替代原TaskController的static执行模式,单次执行一个实例。
 *
 * @author zhongxuchen
 */
public class QuickVoRunner {
	/**
	 * 字段重复(多schema隔离失败)的处理指引;延迟到抛错时构建,确保跟随Msg.setLanguage的语言设置
	 */
	private static String repeatFieldGuide() {
		return Msg.get("\n\n/*------------生成的VO对象中有重复字段致命性错误,具体原因:---------------------------*/\n",
				"\n\n/*------------FATAL: duplicate fields in generated VO, root cause: ------------------*/\n")
				+ Msg.get("/*-  就是datasource中的schema 和 catalog 配置问题,导致在多用户多实例环境下隔离不正确!\n",
						"/*-  Incorrect schema/catalog configuration in datasource breaks isolation in multi-user/multi-instance environments!\n")
				+ Msg.get("/*--------------------------处理协助----------------------------------\n",
						"/*--------------------------HOW TO FIX-------------------------------\n")
				+ Msg.get("/*-1、比如mysql场景下可以设置schema和catalog的值一致进行尝试!\n",
						"/*-1、For MySQL, try setting schema and catalog to the same value!\n")
				+ Msg.get("/*-2、datasource中是可以配置schema和catalog属性的，如果没有可以自行调整和尝试赋值(不同数据库一般跟用户名，库名、tablespace等相关)!\n",
						"/*-2、schema/catalog are configurable in <datasource>; adjust them per database (usually username, database name or tablespace)!\n")
				+ Msg.get("/*-3、取表字段的内部原理:conn.getMetaData().getColumns(catalog, schema, tableName, \"%\");\n",
						"/*-3、Internally columns come from: conn.getMetaData().getColumns(catalog, schema, tableName, \"%\");\n")
				+ "/*-------------------------------------------------------------*/";
	}

	private final QuickVoContext context;

	public QuickVoRunner(QuickVoContext context) {
		this.context = context;
	}

	/**
	 * 常用问题提示;parse之后调用确保跟随quickvo.xml中log-language的语言设置
	 */
	private static String tips() {
		return Msg.get("/*----友情提示: 遇到问题请关注日志提示，最常见错误有3个:     -----------------------*/\n",
				"/*----Tips: watch the log output; the 3 most common errors are: -------------------*/\n")
				+ Msg.get("/*-1、没有匹配到表: 检查 include 正则表达式配置是否正确        \n",
						"/*-1. No matching tables: check the include regex configuration\n")
				+ Msg.get("/*-2、没有匹配到表: 检查 schema和catalog配置是否正确(含大小写)，可尝试去除schema、catalog配置项     \n",
						"/*-2. No matching tables: check schema/catalog configuration (case sensitive); try removing schema/catalog\n")
				+ Msg.get("/*-3、VO中字段出现重复字段，错误就是schema和catalog配置问题,没有隔离不同schema   \n",
						"/*-3. Duplicate fields in VO: caused by schema/catalog misconfiguration failing to isolate schemas\n")
				+ "/*---------------------------------------------------------------*/";
	}

	/**
	 * 总控启动执行所有任务;存在失败(任务异常/字段重复)时抛出QuickVoException令构建失败
	 */
	public void run() throws Exception {
		ParseResult parseResult = XmlConfigParser.parse(context);
		ConfigModel configModel = parseResult.config();
		if (configModel == null || configModel.getTasks() == null || configModel.getTasks().isEmpty()) {
			context.getLogger().info(Msg.get("没有配置可执行任务信息或所有任务状态全部为失效!","No executable tasks configured or all tasks are inactive!"));
			return;
		}
		context.getLogger().info(tips());
		TemplateRenderer renderer = new TemplateRenderer(configModel.getEncoding());
		renderer.loadBuiltinTemplates();
		// 支持链式set(field.support.linked.set)
		boolean isLinkSet = context.getProperties().boolValue("field.support.linked.set", false);
		// 产生SelectField内部类(generate.selectFields.class)
		boolean generateFieldsClass = context.getProperties().boolValue("generate.selectFields.class", false);
		// 空任务(include未匹配到任何表)默认按失败处理:零产物却构建成功是最难发现的配置错误
		boolean failOnEmpty = context.getProperties().boolValue("fail-on-empty", true);
		DataSourceRegistry registry = parseResult.dataSources();
		// 单一writer跨任务累计文件增改统计,供完成汇总
		ClassFileWriter writer = new ClassFileWriter(renderer, context.getLogger());
		List<String> failures = new ArrayList<>();
		List<String> emptyTasks = new ArrayList<>();
		long startMillis = System.currentTimeMillis();
		int tableCount = 0;
		int totalTasks = configModel.getTasks().size();
		int i = 0;
		for (QuickModel quickModel : configModel.getTasks()) {
			i++;
			Optional<DbSession> sessionOpt;
			try {
				sessionOpt = registry.open(quickModel.getDataSource(), context.getLogger());
			} catch (Exception e) {
				failures.add(Msg.get("任务[", "Task[") + i + Msg.get("] 数据库:[", "] datasource:[")
						+ quickModel.getDataSource() + Msg.get("]连接失败:", "] connection failed: ") + e.getMessage());
				context.getLogger().info(Msg.get("数据库:[","Database:[") + quickModel.getDataSource() + Msg.get("]连接异常,请确认你的数据库配置信息或者数据库环境!","] connection failed, please verify datasource configuration or database availability!"));
				continue;
			}
			if (sessionOpt.isEmpty()) {
				// 数据源未注册(理论上已被解析期校验拦截,此处兜底):按失败处理,杜绝零文件生成构建假成功
				failures.add(Msg.get("任务[", "Task[") + i + Msg.get("] 数据库:[", "] datasource:[")
						+ quickModel.getDataSource() + Msg.get("]未在数据源定义中注册,已跳过!", "] not registered in datasource definitions, skipped!"));
				context.getLogger().info(Msg.get("数据库:[","Database:[") + quickModel.getDataSource() + Msg.get("]连接异常,请确认你的数据库配置信息或者数据库环境!","] connection failed, please verify datasource configuration or database availability!"));
				continue;
			}
			context.getLogger().info(taskHeader(i, totalTasks, quickModel));
			try (DbSession session = sessionOpt.get()) {
				int matched = createTask(configModel, quickModel, session, renderer, writer, isLinkSet,
						generateFieldsClass);
				tableCount += matched;
				if (matched == 0) {
					// createTask的唯一0返回路径即"未匹配到任何表":零产物必须可见,不能以BUILD SUCCESS收场
					String emptyMsg = Msg.get("任务[", "Task[") + i + "] include=" + quickModel.getIncludeTables()
							+ Msg.get(" 未匹配到任何表或视图!", " matched no tables/views!");
					if (failOnEmpty) {
						emptyTasks.add(emptyMsg + Msg.get(
								"请检查 include 正则与 schema/catalog 配置;确认该任务无需生成时配置 <property name=\"fail-on-empty\" value=\"false\"/> 允许空任务。",
								"Check the include regex and schema/catalog config; set <property name=\"fail-on-empty\" value=\"false\"/> when an empty task is expected."));
					} else {
						context.getLogger().warning(emptyMsg
								+ Msg.get(" 已按 fail-on-empty=false 允许空任务继续!", " allowed by fail-on-empty=false!"));
					}
				}
			} catch (Exception e) {
				context.getLogger().log(Level.SEVERE,
						Msg.get("任务[", "Task[") + i + Msg.get("]执行失败,includes=", "] failed, includes=")
								+ quickModel.getIncludeTables(),
						e);
				failures.add(Msg.get("任务[", "Task[") + i + "] includes=" + quickModel.getIncludeTables()
						+ Msg.get(" 执行失败:", " failed: ") + e.getMessage());
			}
		}
		// errors与failures合并呈现,避免一类问题掩盖另一类(如连接失败根因被字段重复错误吞没)
		if (context.hasErrors() || !failures.isEmpty() || !emptyTasks.isEmpty()) {
			StringBuilder problem = new StringBuilder();
			if (context.hasErrors()) {
				problem.append(Msg.get("生成过程存在错误:\n", "Errors occurred during generation:\n"))
						.append(String.join("\n", context.getErrors())).append(repeatFieldGuide());
			}
			if (!failures.isEmpty()) {
				if (problem.length() > 0) {
					problem.append("\n\n");
				}
				problem.append(Msg.get("任务执行失败:\n", "Task execution failed:\n")).append(String.join("\n", failures));
			}
			if (!emptyTasks.isEmpty()) {
				if (problem.length() > 0) {
					problem.append("\n\n");
				}
				problem.append(Msg.get("任务未匹配到表:\n", "Tasks matched no tables:\n"))
						.append(String.join("\n", emptyTasks));
			}
			throw new QuickVoException(problem.toString());
		}
		context.getLogger().info(completionSummary(tableCount, writer, startMillis));
	}

	/**
	 * 任务头:序号/总数/数据源/include/exclude一览
	 */
	private static String taskHeader(int index, int total, QuickModel quickModel) {
		StringBuilder sb = new StringBuilder();
		sb.append(Msg.get("开始执行任务:{", "Starting task ")).append(index).append('/').append(total)
				.append(Msg.get(",数据源:", " (datasource: ")).append(quickModel.getDataSource());
		if (quickModel.getIncludeTables() != null) {
			sb.append(Msg.get(",include=", ", include=")).append(quickModel.getIncludeTables());
		}
		if (quickModel.getExcludeTables() != null) {
			sb.append(Msg.get(",exclude=", ", exclude=")).append(quickModel.getExcludeTables());
		}
		sb.append(')');
		return sb.toString();
	}

	/**
	 * 完成汇总:表数/文件增改统计/耗时
	 */
	private static String completionSummary(int tableCount, ClassFileWriter writer, long startMillis) {
		double seconds = (System.currentTimeMillis() - startMillis) / 1000.0;
		return Msg.get("生成完成:共处理", "Generation completed: ") + tableCount
				+ Msg.get("张表,文件[新建:", " tables processed, files [created: ")
				+ writer.getCreatedCount() + Msg.get(",更新:", ", updated: ") + writer.getUpdatedCount()
				+ Msg.get(",无变化:", ", unchanged: ") + writer.getUnchangedCount()
				+ Msg.get("],耗时:", "], elapsed: ") + String.format(java.util.Locale.ROOT, "%.1f", seconds) + "s";
	}

	/**
	 * 单表产物目标类清单(entity/vo及其抽象类),用于单表处理日志
	 */
	private static String generationTargets(QuickModel quickModel, QuickVO quickVO) {
		List<String> targets = new ArrayList<>();
		if (quickModel.isHasEntity()) {
			targets.add(quickModel.isHasAbstractEntity() ? "Abstract" + quickVO.getEntityName() + "/" + quickVO.getEntityName()
					: quickVO.getEntityName());
		}
		if (quickModel.isHasVO()) {
			targets.add(quickModel.isHasAbstractVO() ? "Abstract" + quickVO.getVoName() + "/" + quickVO.getVoName()
					: quickVO.getVoName());
		}
		return targets.isEmpty() ? "-" : String.join(", ", targets);
	}

	/**
	 * 执行单个任务:提取匹配表→逐表加工生成
	 */
	private int createTask(ConfigModel configModel, QuickModel quickModel, DbSession session,
			TemplateRenderer renderer, ClassFileWriter writer, boolean supportLinkSet, boolean selectFields)
			throws Exception {
		String[] includes = null;
		if (quickModel.getIncludeTables() != null) {
			includes = new String[] { quickModel.getIncludeTables().startsWith("(?i)") ? quickModel.getIncludeTables()
					: "(?i)".concat(quickModel.getIncludeTables()) };
		}
		String[] excludes = null;
		if (quickModel.getExcludeTables() != null) {
			excludes = new String[] { quickModel.getExcludeTables().startsWith("(?i)") ? quickModel.getExcludeTables()
					: "(?i)".concat(quickModel.getExcludeTables()) };
		}
		MetadataReader dbReader = new MetadataReader(session, context.getLogger());
		DbType dbType = dbReader.getDbType();
		String dialect = dbReader.getDBDialect();
		List<TableMeta> tables = dbReader.getTableAndView(includes, excludes);
		if (tables == null || tables.isEmpty()) {
			// 无表提示用WARNING:即便配置 fail-on-empty=false,也要让"零产物"在构建日志里可见
			context.getLogger().warning(noTableGuidance(quickModel, dbReader, dbType));
			return 0;
		}
		context.getLogger().info(Msg.get("按include规则匹配到:", "Matched ") + tables.size()
				+ Msg.get(" 张表或视图(数据库:", " tables/views (database: ") + dbType + ")");
		boolean skipPkConstraint = context.getProperties().skipPkConstraint();
		boolean includeSchema = context.getProperties().includeSchema();
		boolean hasApiDoc = quickModel.getApiDoc().equalsIgnoreCase("custom");
		// 目标目录
		String voPackageDir = "";
		String entityDir = "";
		if (quickModel.isHasVO()) {
			voPackageDir = quickModel.getVoPath() + File.separator
					+ StringUtil.replaceAllStr(quickModel.getVoPackage(), ".", File.separator);
			if (quickModel.isHasAbstractVO()) {
				FileUtil.createFolder(
						FileUtil.formatPath(voPackageDir + File.separator + configModel.getAbstractPath()));
			} else {
				FileUtil.createFolder(FileUtil.formatPath(voPackageDir));
			}
		}
		if (quickModel.isHasEntity()) {
			entityDir = quickModel.getEntityPath() + File.separator
					+ StringUtil.replaceAllStr(quickModel.getEntityPackage(), ".", File.separator);
			// 创建abstract entity包文件
			if (quickModel.isHasAbstractEntity()) {
				FileUtil.createFolder(FileUtil.formatPath(entityDir + File.separator + configModel.getAbstractPath()));
			} else {
				FileUtil.createFolder(FileUtil.formatPath(entityDir));
			}
		}
		// oracle存在schema大小写问题,任务内通过转大写重试(成功后粘滞)
		boolean toUpperCase = false;
		for (TableMeta tableMeta : tables) {
			toUpperCase = processTable(configModel, quickModel, dbReader, renderer, writer, dbType, dialect, tableMeta,
					voPackageDir, entityDir, supportLinkSet, selectFields, skipPkConstraint, includeSchema, hasApiDoc,
					toUpperCase);
		}
		return tables.size();
	}

	/**
	 * 加工并生成单张表/视图的全部产物;返回oracle大写重试是否已生效(任务内粘滞)
	 */
	private boolean processTable(ConfigModel configModel, QuickModel quickModel, MetadataReader dbReader,
			TemplateRenderer renderer, ClassFileWriter writer, DbType dbType, String dialect, TableMeta tableMeta,
			String voPackageDir, String entityDir, boolean supportLinkSet, boolean selectFields,
			boolean skipPkConstraint, boolean includeSchema, boolean hasApiDoc, boolean toUpperCase) throws Exception {
		String tableName = tableMeta.getTableName();
		// 新增DDL类注解开关(默认true):老版本sqltoy无@Partition/@PartitionKey/@MppTable注解,
		// 生成物引用不存在的注解会编译失败,老版本用户显式配置false关闭
		boolean includePartition = context.getProperties().boolValue("include.partition", true);
		boolean includeMppTable = context.getProperties().boolValue("include.mpp-table", true);
		BusinessIdConfig businessIdConfig = configModel.getBusinessId(tableName);
		QuickVO quickVO = new QuickVO();
		// 字段级@PartitionKey的渲染开关(类级@Partition提取由下方if门控);
		// partitionKey标记本身不随开关清除,主键策略推导(processPartitionAndPk)不受影响
		quickVO.setIncludePartitionKey(includePartition);
		quickVO.setSelectFields(selectFields);
		quickVO.setVoExtends(quickModel.getVoExtends());
		quickVO.setEntityExtends(quickModel.getEntityExtends());
		// 匹配表主键产生策略，主键策略通过配置文件进行附加定义
		PrimaryKeyStrategy primaryKeyStrategy = PrimaryKeyResolver.findStrategy(configModel.getPkGeneratorStrategy(),
				tableName);
		String entityName = StringUtil.toHumpStr(tableName, true, true);
		quickVO.setApiDoc(quickModel.getApiDoc());
		quickVO.setReturnSelf(supportLinkSet);
		quickVO.setAbstractPath(configModel.getAbstractPath());
		quickVO.setVersion(context.getProperties().getPropertyValue("project.version"));
		quickVO.setProjectName(context.getProperties().getPropertyValue("project.name"));
		quickVO.setAuthor(quickModel.getAuthor());
		quickVO.setDateTime(CommonUtils.formatDate(CommonUtils.getNowTime(), "yyyy-MM-dd HH:mm:ss"));
		quickVO.setTableName(tableName);
		quickVO.setType(tableMeta.getTableType());
		// 包含schema
		if (includeSchema) {
			quickVO.setSchema(tableMeta.getSchema());
		}
		// 针对sqlserver等JDBC元数据注释缺失的库做补充提取
		if (StringUtil.isBlank(tableMeta.getTableRemark())) {
			quickVO.setTableRemark(StringUtil.escapeComment(dbReader.getTableRemark(tableName)));
		} else {
			quickVO.setTableRemark(StringUtil.escapeComment(tableMeta.getTableRemark()));
		}
		quickVO.setEntityPackage(quickModel.getEntityPackage());
		// 截取类名前面的模块标识名称(一般数据库表名前缀为特定的模块名称)
		quickVO.setEntityName(StringUtil.firstToUpperCase(StringUtil.replaceStr(quickModel.getEntityName(),
				"#{subName}", StringUtil.subStart(entityName, quickModel.getEntitySubstr()))));
		quickVO.setVoPackage(quickModel.getVoPackage());
		quickVO.setVoName(StringUtil.firstToUpperCase(StringUtil.replaceStr(quickModel.getVoName(), "#{subName}",
				StringUtil.subStart(entityName, quickModel.getVoSubstr()))));
		boolean isTable = !"VIEW".equals(quickVO.getType());
		// 单表处理日志:表名→目标类一览(在元数据提取前输出,便于定位卡在哪张表)
		context.getLogger().info((isTable ? Msg.get("处理表:", "Processing table ") : Msg.get("处理视图:", "Processing view "))
				+ tableName + " → " + generationTargets(quickModel, quickVO));
		List<TableColumnMeta> tableCols = dbReader.getTableColumnMeta(tableName, toUpperCase);
		// oracle 存在schema大小写问题，通过转大写进行重试
		if ((tableCols == null || tableCols.isEmpty()) && dbType.isOracleFamily() && !toUpperCase) {
			tableCols = dbReader.getTableColumnMeta(tableName, true);
			if (tableCols != null && !tableCols.isEmpty()) {
				toUpperCase = true;
			}
		}
		// 空列必然是配置问题(schema/catalog与表清单来源不一致/权限缺失),fail-fast跳过该表,
		// 避免零字段文件覆盖既有正确产物且构建假成功
		if (tableCols == null || tableCols.isEmpty()) {
			context.addError(Msg.get("表:", "Table ") + tableName
					+ Msg.get(" 未取到任何列元数据,已跳过生成以保护既有文件!常见原因:schema/catalog配置与表清单视角不一致"
							+ "(如oracle配置schema与连接用户不同)、当前用户对该表无权限",
							" has no column metadata, generation skipped to protect existing files! Common causes: schema/catalog inconsistent with table listing (e.g. oracle schema differs from connecting user), or no permission on this table"));
			return toUpperCase;
		}
		// vo中需要import的数据类型(顺序即生成文件的import顺序)
		List<String> impList = new ArrayList<>();
		// 关闭分区提取时仅跳过@Partition/@PartitionKey渲染(模板guard),主键策略推导不受影响
		List<QuickColMeta> colList = TableProcessor.processTableCols(context, configModel, quickModel, tableName,
				tableCols, isTable ? (List<TableConstractModel>) dbReader.getTableImpForeignKeys(tableName) : null,
				impList, dbType, dialect, hasApiDoc, renderer);
		// 处理主键被其它表作为外键关联
		CascadeResolver.processExportTables(quickVO, dbReader.getTableExportKeys(tableName), quickModel,
				configModel.getCascadeConfig());
		// 判断表字段是否全部为非空约束
		int notNullCnt = TableProcessor.judgeFullNotNull(colList);
		quickVO.setFullNotNull(notNullCnt == colList.size() ? "1" : "0");
		if (isTable) {
			List<String> pks = dbReader.getTablePrimaryKeys(tableName);
			int pkSize = pks == null ? 0 : pks.size();
			// 给字段打上主键标记
			TableProcessor.setPk(colList, pks);
			// 实际打上主键标记的列数:主键元数据与列元数据零交集(schema错配,主键来自
			// 另一schema的同名表)时为0,此时按无主键处理,避免pk构造器退化成与默认构造器
			// 签名重复的零参形式导致产物编译失败
			int markedPkCnt = 0;
			for (QuickColMeta colMeta : colList) {
				if ("1".equals(colMeta.getPkFlag())) {
					markedPkCnt++;
				}
			}
			// 主键字段长度等于表字段长度，设置所有字段为主键标志为1
			if (pkSize == colList.size()) {
				quickVO.setPkIsAllColumn("1");
			}
			if (notNullCnt == pkSize) {
				quickVO.setPkSizeEqualNotNullSize("1");
			}
			// 存在有主键、分区字段时(大多数数据库分区字段必须纳入主键)，
			// 如果主键不是分区字段，则将分区字段标记成辅助主键
			int realPKCnt = TableProcessor.processPartitionAndPk(colList);
			// 存在独立主键，能从主键中区分出实际的分区字段，在设置主键策略时排除掉分区字段
			boolean filterAssistPK = pkSize > realPKCnt;
			boolean onePk = realPKCnt == 1 || pkSize == 1;
			quickVO.setSinglePk(onePk ? "1" : "0");
			if (pkSize == 0 || markedPkCnt == 0) {
				quickVO.setSinglePk("-1");
				if (pkSize == 0) {
					context.getLogger().warning(Msg.get("表:", "Table ") + tableName
							+ Msg.get(" 无主键:生成的类将不支持按主键update/delete!若该表实际有主键,通常是schema/catalog配置错误导致匹配到其它schema的同名表!",
									" has no primary key: generated class won't support pk-based update/delete! If this table should have a PK, a wrong schema/catalog usually matched a same-name table in another schema!"));
				} else {
					context.getLogger().warning(Msg.get("表:", "Table ") + tableName
							+ Msg.get(" 的主键列与列元数据零交集(共", "'s primary key columns match none of its columns (")
							+ pkSize + Msg.get("个主键,0个命中):通常是schema/catalog配置错误导致主键与列元数据来自不同schema的同名表,已按无主键处理!",
									" pk columns, 0 matched): usually a schema/catalog misconfiguration matching same-name tables across schemas; treated as no primary key!"));
				}
			} else {
				// 设置主键约束配置,对postgresql 有意义
				if (!skipPkConstraint) {
					quickVO.setPkConstraint(dbReader.getTablePKConstraint(tableName));
				}
				PrimaryKeyResolver.applyStrategy(colList, onePk, filterAssistPK, primaryKeyStrategy, tableName,
						impList);
			}
			// 业务主键不依赖库端主键存在:无库主键的表同样允许通过business-primary-key标注业务主键列
			markBusinessId(quickVO, colList, businessIdConfig);
		}
		// 索引信息
		quickVO.setIndexModels(dbReader.getIndexInfo(tableName, quickVO.getPkConstraint()));
		// 分区策略(pg/mysql/oracle),生成@Partition供sqltoy的DDL同步等场景消费
		if (includePartition) {
			org.sagacity.quickvo.dialect.TablePartition tablePartition = dbReader.getTablePartition(tableName);
			if (tablePartition != null) {
				quickVO.setPartitionStrategy(tablePartition.strategy());
				quickVO.setPartitionExpression(tablePartition.expression());
				quickVO.setPartitionDefs(tablePartition.defs());
				if (!impList.contains("org.sagacity.sqltoy.config.annotation.Partition")) {
					impList.add("org.sagacity.sqltoy.config.annotation.Partition");
				}
				if (tablePartition.defs() != null && !tablePartition.defs().isEmpty()
						&& !impList.contains("org.sagacity.sqltoy.config.annotation.PartitionDef")) {
					impList.add("org.sagacity.sqltoy.config.annotation.PartitionDef");
				}
			}
		}
		// MPP表引擎元数据(ClickHouse/Doris/StarRocks),生成@MppTable供sqltoy DDL生成还原引擎定义
		org.sagacity.quickvo.dialect.MppTableInfo mppInfo = includeMppTable ? dbReader.getMppTableInfo(tableName) : null;
		if (mppInfo != null) {
			StringBuilder mppAnn = new StringBuilder("@MppTable(");
			mppAnn.append("engine=\"").append(mppInfo.engine()).append("\"");
			if (!mppInfo.engineArgs().isBlank()) {
				mppAnn.append(", engineArgs=\"").append(mppInfo.engineArgs()).append("\"");
			}
			if (!mppInfo.keyModel().isBlank()) {
				mppAnn.append(", keyModel=\"").append(mppInfo.keyModel()).append("\"");
			}
			if (mppInfo.orderBy().length > 0) {
				mppAnn.append(", orderBy={").append(csvQuoted(mppInfo.orderBy())).append("}");
			}
			if (mppInfo.distributedBy().length > 0) {
				mppAnn.append(", distributedBy={").append(csvQuoted(mppInfo.distributedBy())).append("}");
			}
			if (mppInfo.buckets() > 0) {
				mppAnn.append(", buckets=").append(mppInfo.buckets());
			}
			if (mppInfo.properties().length > 0) {
				mppAnn.append(", properties={").append(csvQuoted(mppInfo.properties())).append("}");
			}
			mppAnn.append(")");
			quickVO.setMppTableContent(mppAnn.toString());
			if (!impList.contains("org.sagacity.sqltoy.config.annotation.MppTable")) {
				impList.add("org.sagacity.sqltoy.config.annotation.MppTable");
			}
		}
		// 针对数据库表中字段存在reciveTime 和recive_time 这种形态优化
		TableProcessor.adjustCamelConflict(colList, quickModel.getFieldRidPrefix());
		// 字段名合法性/唯一性校验(须在冲突消歧之后):命名与生成物保持历史行为,
		// 仅把"生成的字段/getter无法编译"提前暴露为插件错误并指明表与列
		TableProcessor.validateJavaFieldNames(context, tableName, colList);
		// 应用extra-annotations配置的表级/字段级扩展注解及其import
		applyExtraAnnotations(quickVO, colList, impList, configModel, tableName);
		// 重新生成时继承旧文件中的sqltoy语义注解(DataVersion/Secure/Tenant/Translate等),
		// OneToMany/BusinessId按quickvo.xml配置优先规则处理
		markDataVersion(tableName, colList, impList, configModel.getDataVersion(tableName));
		applyAnnotationCarryOver(quickVO, colList, impList, configModel, tableName, quickModel, entityDir,
				renderer.getCharset());
		quickVO.setColumns(colList);
		quickVO.setColumnSize(colList.size());
		// 加入自定义api-doc依赖的类
		if (hasApiDoc) {
			if (configModel.getDocApiImports() != null) {
				quickVO.setApiDocImports(configModel.getDocApiImports());
			}
		}
		if (impList.contains("java.util.List")) {
			quickVO.setHasListType(true);
		}
		quickVO.setImports(impList);
		if (quickModel.isHasEntity() && quickModel.isHasVO()) {
			quickVO.setHasVoEntity(true);
		}
		String docClassTemplate = configModel.getDocApiClassTemplate();
		boolean hasClassApiDoc = hasApiDoc && docClassTemplate != null;
		String[] apiDocNames = { "className", "tableName", "tableRemark" };
		// 创建entity文件
		if (quickModel.isHasEntity()) {
			quickVO.setLombok(quickModel.isEntityLombok());
			quickVO.setLombokChain(quickModel.isEntityLombokChain());
			if (quickModel.isHasAbstractEntity()) {
				if (hasClassApiDoc) {
					quickVO.setApiDocContent(
							renderer.renderAdhoc(apiDocNames, new Object[] { "Abstract" + quickVO.getEntityName(),
									quickVO.getTableName(), quickVO.getTableRemark() }, docClassTemplate));
				}
				// 创建abstract entity文件
				writer.write(
						entityDir + File.separator + configModel.getAbstractPath() + File.separator + "Abstract"
								+ quickVO.getEntityName() + ".java",
						Template.ENTITY_ABSTRACT, quickVO, SerialKind.ABSTRACT_ENTITY, WriteMode.OVERWRITE_IF_CHANGED);
				if (hasClassApiDoc) {
					quickVO.setApiDocContent(renderer.renderAdhoc(apiDocNames,
							new Object[] { quickVO.getEntityName(), quickVO.getTableName(), quickVO.getTableRemark() },
							docClassTemplate));
				}
				// 创建entity 文件(用户可扩展,仅更新构造器自动生成区);
				// 实现类仅实际引用主键构造器参数类型,临时换成过滤后的import集合,
				// 剔除仅字段级使用的import(Foreign/Partition等注解及字段类型import只在抽象基类中出现)
				quickVO.setImports(entityImplImports(impList, colList, quickVO));
				writer.write(entityDir + File.separator + quickVO.getEntityName() + ".java", Template.ENTITY_PARENT,
						quickVO, SerialKind.ENTITY, WriteMode.MERGE_CONSTRUCTOR);
				quickVO.setImports(impList);
			} else {
				if (hasClassApiDoc) {
					quickVO.setApiDocContent(renderer.renderAdhoc(apiDocNames,
							new Object[] { quickVO.getEntityName(), quickVO.getTableName(), quickVO.getTableRemark() },
							docClassTemplate));
				}
				if (quickModel.isEntityLombok()) {
					writer.write(entityDir + File.separator + quickVO.getEntityName() + ".java", Template.ENTITY_LOMBOK,
							quickVO, SerialKind.ENTITY, WriteMode.MERGE_FIELDS);
				} else {
					writer.write(entityDir + File.separator + quickVO.getEntityName() + ".java", Template.ENTITY,
							quickVO, SerialKind.ENTITY, WriteMode.OVERWRITE_IF_CHANGED);
				}
			}
		}
		// 创建DTO 文件
		if (quickModel.isHasVO()) {
			quickVO.setLombok(quickModel.isLombok());
			quickVO.setLombokChain(quickModel.isLombokChain());
			if (quickModel.isHasAbstractVO()) {
				if (hasClassApiDoc) {
					quickVO.setApiDocContent(
							renderer.renderAdhoc(apiDocNames, new Object[] { "Abstract" + quickVO.getVoName(),
									quickVO.getTableName(), quickVO.getTableRemark() }, docClassTemplate));
				}
				// 创建抽象dto
				writer.write(
						voPackageDir + File.separator + configModel.getAbstractPath() + File.separator + "Abstract"
								+ quickVO.getVoName() + ".java",
						Template.DTO_ABSTRACT, quickVO, SerialKind.ABSTRACT_VO, WriteMode.OVERWRITE_IF_CHANGED);
				if (hasClassApiDoc) {
					quickVO.setApiDocContent(renderer.renderAdhoc(apiDocNames,
							new Object[] { quickVO.getVoName(), quickVO.getTableName(), quickVO.getTableRemark() },
							docClassTemplate));
				}
				writer.write(voPackageDir + File.separator + quickVO.getVoName() + ".java", Template.DTO_PARENT,
						quickVO, SerialKind.VO, WriteMode.CREATE_ONLY);
			} else {
				if (hasClassApiDoc) {
					quickVO.setApiDocContent(renderer.renderAdhoc(apiDocNames,
							new Object[] { quickVO.getVoName(), quickVO.getTableName(), quickVO.getTableRemark() },
							docClassTemplate));
				}
				writer.write(voPackageDir + File.separator + quickVO.getVoName() + ".java", Template.DTO, quickVO,
						SerialKind.VO, WriteMode.MERGE_FIELDS);
			}
		}
		return toUpperCase;
	}

	/** 判断是否是业务主键字段并打标记 */
	/** data-version配置命中:标注版本字段并补import;列不存在打warning(增强性配置不中断) */
	private void markDataVersion(String tableName, List<QuickColMeta> colList, List<String> impList,
			org.sagacity.quickvo.model.DataVersionConfig dataVersionConfig) {
		if (dataVersionConfig == null) {
			return;
		}
		String target = dataVersionConfig.column().replaceAll("\\_|\\-", "");
		for (QuickColMeta quickColMeta : colList) {
			if (quickColMeta.getColName().replaceAll("\\_|\\-", "").equalsIgnoreCase(target)) {
				quickColMeta.setDataVersion(true);
				quickColMeta.setDataVersionStartDate(dataVersionConfig.startDate());
				if (!impList.contains("org.sagacity.sqltoy.config.annotation.DataVersion")) {
					impList.add("org.sagacity.sqltoy.config.annotation.DataVersion");
				}
				return;
			}
		}
		context.getLogger().warning(Msg.get("data-version配置的列[", "data-version column [") + dataVersionConfig.column()
				+ Msg.get("]在表", "] not found in table ") + tableName + Msg.get("中不存在,已忽略!", ", ignored!"));
	}

	private void markBusinessId(QuickVO quickVO, List<QuickColMeta> colList, BusinessIdConfig businessIdConfig) {
		if (businessIdConfig == null) {
			return;
		}
		for (QuickColMeta quickColMeta : colList) {
			if (quickColMeta.getColName().replaceAll("\\_|\\-", "")
					.equalsIgnoreCase(businessIdConfig.getColumn().replaceAll("\\_|\\-", ""))) {
				quickColMeta.setBusinessIdConfig(businessIdConfig);
				quickVO.setHasBusinessId(true);
				break;
			}
		}
	}

	/**
	 * 实现类(extends AbstractXxx)仅实际引用主键构造器参数类型,从全量import中
	 * 过滤出参数类型对应的import;字段级import(Foreign/ReferentialAction/Partition注解、
	 * 字段类型)只在抽象基类中使用,进实现类即成多余import。
	 * 视图或无主键(模板不渲染pk构造器)时实现类不引用任何字段类型
	 */
	private static List<String> entityImplImports(List<String> imports, List<QuickColMeta> columns, QuickVO quickVO) {
		List<String> filtered = new ArrayList<>();
		// 与parent-entity模板的pk构造器渲染条件一致:仅TABLE且singlePk为1或0
		if (!"TABLE".equals(quickVO.getType())
				|| (!"1".equals(quickVO.getSinglePk()) && !"0".equals(quickVO.getSinglePk()))) {
			return filtered;
		}
		Set<String> pkTypes = new HashSet<>();
		for (QuickColMeta column : columns) {
			if ("1".equals(column.getPkFlag())) {
				String resultType = column.getResultType();
				// 全限定类型模板原样输出,无需import
				if (resultType != null && resultType.indexOf('.') == -1) {
					pkTypes.add(resultType);
				}
			}
		}
		// 保持全量import的原有顺序,输出顺序稳定
		for (String imp : imports) {
			int dot = imp.lastIndexOf('.');
			if (dot > -1 && pkTypes.contains(imp.substring(dot + 1))) {
				filtered.add(imp);
			}
		}
		return filtered;
	}

	/**
	 * 应用extra-annotations配置:表名正则完整匹配(忽略大小写)的配置逐个生效,
	 * 类级注解/字段级注解(多行以\r\n\t衔接保证缩进)/补充import
	 */
	private void applyExtraAnnotations(QuickVO quickVO, List<QuickColMeta> colList, List<String> impList,
			ConfigModel configModel, String tableName) {
		List<org.sagacity.quickvo.model.ExtraAnnotationConfig> configs = configModel.getExtraAnnotationList();
		if (configs == null || configs.isEmpty()) {
			return;
		}
		List<String> classAnnotations = new ArrayList<>();
		for (org.sagacity.quickvo.model.ExtraAnnotationConfig config : configs) {
			if (!tableName.matches("(?i)".concat(config.getTableName()))) {
				continue;
			}
			classAnnotations.addAll(config.getClassAnnotations());
			for (QuickColMeta colMeta : colList) {
				String annotation = config.getFieldAnnotations()
						.get(colMeta.getColName().replaceAll("\\_|\\-", "").toLowerCase());
				if (annotation != null) {
					String joined = annotation.replace("\r\n", "\n").replace("\n", "\r\n\t");
					colMeta.setExtraAnnotations(
							joined.endsWith("\t") ? joined.substring(0, joined.length() - 1) : joined);
				}
			}
			for (String imp : config.getImports()) {
				if (!impList.contains(imp)) {
					impList.add(imp);
				}
			}
		}
		if (!classAnnotations.isEmpty()) {
			quickVO.setExtraClassAnnotations(classAnnotations);
		}
	}

	/**
	 * 重新生成时的注解继承:解析承载字段的旧实体文件,白名单注解(DataVersion/Secure/SecureConfig/
	 * Tenant/Translate/Translates/SqlToyFieldAlias/OneToOne)原样继承;
	 * OneToMany优先级:quickvo.xml cascade配置命中子表→quickvo生成结果为准,否则继承旧注解的
	 * load/update/orderBy/delete调优;BusinessId优先级:quickvo.xml
	 * business-primary-key命中→
	 * quickvo为准,否则继承旧注解;extra-annotations(xml)配置的注解优先于继承,同名去重
	 */
	private void applyAnnotationCarryOver(QuickVO quickVO, List<QuickColMeta> colList, List<String> impList,
			ConfigModel configModel, String tableName, QuickModel quickModel, String entityDir, String charset) {
		if (!quickModel.isHasEntity()) {
			return;
		}
		// 承载字段的文件:有抽象类为AbstractX,否则为实体本身
		String fieldFile = quickModel.isHasAbstractEntity()
				? entityDir + File.separator + configModel.getAbstractPath() + File.separator + "Abstract"
						+ quickVO.getEntityName() + ".java"
				: entityDir + File.separator + quickVO.getEntityName() + ".java";
		java.io.File existing = new java.io.File(fieldFile);
		if (!existing.exists()) {
			return;
		}
		org.sagacity.quickvo.core.AnnotationCarryOver.CarryOverResult carried;
		try {
			// 与ClassFileWriter读旧文件一致,用产物编码而非平台默认编码,避免中文环境读UTF-8产物乱码
			carried = org.sagacity.quickvo.core.AnnotationCarryOver.parse(FileUtil.readAsString(existing, charset));
		} catch (Exception e) {
			context.getLogger().warning(Msg.get("解析旧文件注解失败(", "Failed to parse annotations from existing file (")
					+ fieldFile + "):" + e.getMessage());
			return;
		}
		if (carried == null || (carried.fieldAnnotations.isEmpty() && carried.classAnnotations.isEmpty())) {
			return;
		}
		boolean businessIdConfigured = configModel.getBusinessId(tableName) != null;
		List<String> cascadeTables = new ArrayList<>();
		if (configModel.getCascadeConfig() != null) {
			for (org.sagacity.quickvo.model.CascadeModel cascade : configModel.getCascadeConfig()) {
				cascadeTables.add(cascade.getTableName().toLowerCase());
			}
		}
		// 类级注解继承(xml extra-annotations优先,同名去重)
		List<String> classMerged = quickVO.getExtraClassAnnotations() == null ? new ArrayList<>()
				: new ArrayList<>(quickVO.getExtraClassAnnotations());
		for (String annotation : carried.classAnnotations) {
			if (!containsAnnotation(classMerged, annotation)) {
				classMerged.add(annotation);
				addCarryImport(impList, carried, annotation);
			}
		}
		if (!classMerged.isEmpty()) {
			quickVO.setExtraClassAnnotations(classMerged);
		}
		// 字段级注解继承
		for (QuickColMeta colMeta : colList) {
			List<String> olds = carried.fieldAnnotations.get(colMeta.getColJavaName().toLowerCase());
			if (olds == null || olds.isEmpty()) {
				continue;
			}
			List<String> merged = new ArrayList<>();
			String existingExtra = colMeta.getExtraAnnotations();
			if (existingExtra != null && !existingExtra.isEmpty()) {
				merged.add(existingExtra);
			}
			for (String annotation : olds) {
				// BusinessId:quickvo.xml配置命中时quickvo生成结果为准,跳过继承
				if (annotation.startsWith("@BusinessId") || annotation.contains(".BusinessId")) {
					if (businessIdConfigured) {
						continue;
					}
					if (colMeta.getBusinessIdConfig() != null) {
						continue;
					}
				}
				// DataVersion:data-version配置命中时以配置为准,跳过继承避免重复注解
				if ((annotation.startsWith("@DataVersion") || annotation.contains(".DataVersion"))
						&& colMeta.isDataVersion()) {
					continue;
				}
				if (!containsAnnotation(merged, annotation)) {
					merged.add(annotation);
					addCarryImport(impList, carried, annotation);
				}
			}
			if (merged.size() > 0) {
				colMeta.setExtraAnnotations(String.join("\r\n\t", merged));
			}
		}
		// OneToMany:quickvo.xml
		// cascade命中→quickvo为准;否则旧注解的调优(load/update/orderBy/delete)继承
		if (quickVO.getExportTables() != null) {
			for (Object export : quickVO.getExportTables()) {
				org.sagacity.quickvo.model.TableConstractModel exportTable = (org.sagacity.quickvo.model.TableConstractModel) export;
				String refTable = exportTable.getPkRefTableName();
				String cascadeName = cascadeTables.stream().filter(t -> t.equalsIgnoreCase(refTable.replace("\"", "")))
						.findFirst().orElse(null);
				if (cascadeName != null) {
					continue;
				}
				// 关联字段名按模板规则:uncap(实体名)+复数(原名以s结尾加es,否则加s)
				String refJavaName = exportTable.getPkRefTableJavaName();
				String refFieldName = (StringUtil.firstToLowerCase(refJavaName)
						+ (refJavaName.endsWith("s") ? "es" : "s")).toLowerCase();
				String oneToMany = carried.oneToManyByField.get(refFieldName);
				if (oneToMany == null) {
					continue;
				}
				java.util.regex.Matcher load = java.util.regex.Pattern.compile("load=\"([^\"]*)\"").matcher(oneToMany);
				java.util.regex.Matcher update = java.util.regex.Pattern.compile("update=\"([^\"]*)\"")
						.matcher(oneToMany);
				java.util.regex.Matcher orderBy = java.util.regex.Pattern.compile("orderBy=\"([^\"]*)\"")
						.matcher(oneToMany);
				if (load.find()) {
					exportTable.setLoad(load.group(1));
				}
				if (update.find()) {
					exportTable.setUpdateSql(update.group(1));
				}
				if (orderBy.find()) {
					exportTable.setOrderBy(orderBy.group(1));
				}
				if (oneToMany.contains("delete=true")) {
					exportTable.setDeleteRule(1);
				}
			}
		}
	}

	/** 列名/属性数组转"v1","v2"形式 */
	private String csvQuoted(String[] values) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				result.append(",");
			}
			result.append("\"").append(values[i]).append("\"");
		}
		return result.toString();
	}

	/** 判断注解列表中是否已存在同名注解(xml配置优先去重) */
	private boolean containsAnnotation(List<String> annotations, String annotation) {
		String simpleName = annotationSimpleName(annotation);
		for (String existing : annotations) {
			if (annotationSimpleName(existing).equals(simpleName)) {
				return true;
			}
		}
		return false;
	}

	/** 提取注解简单名(兼容全限定名形式) */
	private String annotationSimpleName(String annotation) {
		String name = annotation.trim();
		if (name.startsWith("@")) {
			name = name.substring(1);
		}
		int paren = name.indexOf('(');
		if (paren > -1) {
			name = name.substring(0, paren);
		}
		int dot = name.lastIndexOf('.');
		return dot > -1 ? name.substring(dot + 1) : name;
	}

	/** 简单名形式的继承注解需要import(全限定名形式不需要) */
	private void addCarryImport(List<String> impList,
			org.sagacity.quickvo.core.AnnotationCarryOver.CarryOverResult carried, String annotation) {
		String simpleName = annotationSimpleName(annotation);
		boolean fullyQualified = annotation.contains("." + simpleName) && !annotation.startsWith("@" + simpleName);
		if (fullyQualified) {
			return;
		}
		for (String imp : carried.imports) {
			if (imp.endsWith("." + simpleName) && !impList.contains(imp)) {
				impList.add(imp);
			}
		}
	}

	/**
	 * 未匹配到表时的精准指引:数据库专属的schema/catalog用法 + 当前库实际可用的schema/catalog清单
	 */
	private String noTableGuidance(QuickModel quickModel, MetadataReader dbReader, DbType dbType) {
		StringBuilder guide = new StringBuilder();
		guide.append(Msg.get("/*--------没有取到匹配的表，错误原因提示,请严重关注!-------------------------------*/\n",
				"/*--------No matching tables found, common causes, please review:------------------*/\n"));
		guide.append(Msg.get("/*-1、请检查task配置中的: include=\"", "/*-1. Check task config: include=\"")).append(quickModel.getIncludeTables())
				.append(Msg.get("\"是否正确!这是用来表名匹配的正则表达式!\n", "\" - it is the regex used to match table names!\n"));
		guide.append(Msg.get("/*-2、schema/catalog配置指引(当前数据库:","/*-2. schema/catalog guide (current db: ")).append(dbType).append("):\n");
		switch (dbType) {
		case MYSQL, MYSQL57 -> guide.append(Msg.get("/*-   mysql/mariadb/tidb等: catalog=数据库名(小写),不要配置schema;当前连接库=",
				"/*-   mysql/mariadb/tidb: catalog=database name (lowercase), no schema; connected catalog="))
				.append(dbReader.currentCatalog()).append("\n");
		case ORACLE, ORACLE11 -> guide.append(Msg.get("/*-   oracle: schema=用户名(通常大写),不要配置catalog;如schema=\"SYSTEM\"\n",
				"/*-   oracle: schema=username (usually UPPERCASE), no catalog; e.g. schema=\"SYSTEM\"\n"));
		case POSTGRESQL, POSTGRESQL14, GAUSSDB, OPENGAUSS, STARDB, VASTBASE, OSCAR ->
			guide.append(Msg.get("/*-   postgresql系: schema=模式名(通常小写public),catalog一般无需配置\n",
					"/*-   postgresql family: schema=schema name (usually lowercase public), catalog normally not needed\n"));
		case SQLSERVER -> guide.append(Msg.get("/*-   sqlserver: catalog=数据库名,schema=dbo等架构名\n",
				"/*-   sqlserver: catalog=database name, schema=schema name like dbo\n"));
		case DB2 -> guide.append(Msg.get("/*-   db2: schema=用户名(通常大写),catalog=数据库名\n",
				"/*-   db2: schema=username (usually UPPERCASE), catalog=database name\n"));
		case DM -> guide.append(Msg.get("/*-   达梦: schema=用户名(通常大写),如SYSDBA\n",
				"/*-   DM (Dameng): schema=username (usually UPPERCASE), e.g. SYSDBA\n"));
		case KINGBASE -> guide.append(Msg.get("/*-   金仓: schema=模式名(默认public)\n",
				"/*-   Kingbase: schema=schema name (default public)\n"));
		default -> guide.append(Msg.get("/*-   通用建议: 先同时去除schema和catalog配置重试,再按数据库类型补配\n",
				"/*-   General advice: remove schema and catalog first and retry, then add per database type\n"));
		}
		List<String> schemas = dbReader.listSchemas();
		List<String> catalogs = dbReader.listCatalogs();
		if (!catalogs.isEmpty()) {
			guide.append(Msg.get("/*-   当前可用catalog(","/*-   Available catalogs(")).append(catalogs.size()).append(Msg.get("个): ","): ")).append(String.join(",", catalogs))
					.append("\n");
		}
		if (!schemas.isEmpty()) {
			guide.append(Msg.get("/*-   当前可用schema(","/*-   Available schemas(")).append(schemas.size()).append(Msg.get("个): ","): ")).append(String.join(",", schemas))
					.append("\n");
		}
		guide.append(Msg.get("/*-3、内部原理: conn.getMetaData().getTables(catalog, schema, tableNamePattern, TABLE);\n",
				"/*-3. Internally: conn.getMetaData().getTables(catalog, schema, tableNamePattern, TABLE);\n"));
		guide.append("/*--------------------------------------------------------------------------------*/");
		return guide.toString();
	}
}
