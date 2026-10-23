package org.sagacity.quickvo.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.lang.model.SourceVersion;

import org.sagacity.quickvo.QuickVoContext;
import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.model.ColumnTypeMapping;
import org.sagacity.quickvo.model.ConfigModel;
import org.sagacity.quickvo.model.QuickColMeta;
import org.sagacity.quickvo.model.QuickModel;
import org.sagacity.quickvo.model.TableColumnMeta;
import org.sagacity.quickvo.model.TableConstractModel;
import org.sagacity.quickvo.render.TemplateRenderer;
import org.sagacity.quickvo.utils.CommonUtils;
import org.sagacity.quickvo.utils.StringUtil;
import org.sagacity.quickvo.utils.Msg;

/**
 * 表列加工:数据库列元信息→生成模板所需的QuickColMeta(类型映射、外键、命名、api-doc、import收集)。
 * import集合的插入顺序直接决定生成文件的import顺序,历史顺序在此完整保留。
 *
 * @author zhongxuchen
 */
public class TableProcessor {

	/**
	 * 处理表的列信息
	 */
	public static List<QuickColMeta> processTableCols(QuickVoContext context, ConfigModel configModel,
			QuickModel quickModel, String tableName, List<TableColumnMeta> cols, List<TableConstractModel> fks,
			List<String> impList, DbType dbType, String dialect, boolean hasApiDoc,
			TemplateRenderer renderer) throws Exception {
		Logger logger = context.getLogger();
		List<QuickColMeta> quickColMetas = new ArrayList<>();
		String ridPrefix = quickModel.getFieldRidPrefix();
		// include.partition开关只控制@PartitionKey注解渲染与import(老版本sqltoy无该注解);
		// partitionKey标记本身无条件保留——processPartitionAndPk依赖它从复合主键中剔除分区列
		// 推导真实主键与@Id策略,若随开关清掉,include.partition=false会静默改变主键策略推导
		boolean includePartition = context.getProperties().boolValue("include.partition", true);
		int maxScale = context.getProperties().getMaxScale();
		List<ColumnTypeMapping> typeMapping = configModel.getTypeMapping();
		Set<String> colsSet = new HashSet<>();
		boolean hasPartitionKey = false;
		for (TableColumnMeta colMeta : cols) {
			String tableField = tableName.concat(".").concat(colMeta.getColName()).toLowerCase();
			QuickColMeta quickColMeta = new QuickColMeta();
			quickColMeta.setColRemark(colMeta.getColRemark());
			quickColMeta.setAssistPartition(colMeta.isAssistPartition());
			if (colMeta.getGeneratedType() > 0) {
				quickColMeta.setGeneratedType(colMeta.getGeneratedType());
				if (!impList.contains("org.sagacity.sqltoy.config.model.GeneratedType")) {
					impList.add("org.sagacity.sqltoy.config.model.GeneratedType");
				}
			}
			// 判断是否存在重复字段(多schema隔离失效的典型症状)
			if (colsSet.contains(colMeta.getColName().toLowerCase())) {
				context.addError(Msg.get("表:", "Table: ") + tableName
						+ Msg.get(" 存在重复字段:", " has duplicate column: ") + colMeta.getColName());
			} else {
				colsSet.add(colMeta.getColName().toLowerCase());
			}
			// 原始数据类型归一后输出在vo字段上,便于开发者调整
			String jdbcType = TypeMappingResolver.normalizeNativeType(colMeta.getTypeName());
			quickColMeta.setColType(jdbcType);
			String sqlType = jdbcType.toLowerCase();
			quickColMeta.setDataType(TypeMappingResolver.toJdbcType(jdbcType, dbType));
			// 未知原生类型:模板对非数字dataType拼JdbcTypes.<名称>,未知名称产出无法解析的常量
			// 导致生成物编译失败;兜底Types.OTHER并告警(DDL同步的OTHER分支按nativeType还原列类型)
			if (!TypeMappingResolver.isKnownJdbcType(quickColMeta.getDataType())) {
				logger.info(Msg.get("字段:[", "Column: [") + colMeta.getColName() + Msg.get("]原生类型:[", "] type: [")
						+ colMeta.getTypeName() + Msg.get("]没有匹配的jdbc-type,兜底为Types.OTHER!",
						"] has no jdbc-type mapping, falling back to Types.OTHER!"));
				quickColMeta.setDataType(String.valueOf(java.sql.Types.OTHER));
			}
			quickColMeta.setColName(colMeta.getColName());
			if (colMeta.getPartitionKey()) {
				quickColMeta.setPartitionKey(true);
				hasPartitionKey = true;
			}
			quickColMeta.setAutoIncrement(Boolean.toString(colMeta.isAutoIncrement()));
			// 剔除字段统一前缀
			if (StringUtil.isNotBlank(ridPrefix)
					&& colMeta.getColName().toLowerCase(java.util.Locale.ROOT).startsWith(ridPrefix)) {
				quickColMeta.setColJavaName(
						StringUtil.toHumpStr(colMeta.getColName().substring(ridPrefix.length()), true, true));
			} else {
				quickColMeta.setColJavaName(StringUtil.toHumpStr(colMeta.getColName(), true, true));
			}
			quickColMeta.setPrecision(colMeta.getPrecision());
			quickColMeta.setScale(colMeta.getScale());
			quickColMeta.setDefaultValue(colMeta.getColDefault());
			// 避免部分数据库整数类型,默认值小数位后面还有好几个0,如:1.000000
			if (quickColMeta.getDefaultValue() != null && CommonUtils.isNumber(quickColMeta.getDefaultValue())) {
				if (colMeta.getLength() == colMeta.getPrecision()) {
					// 用BigDecimal避免double转换造成长数字精度丢失;非整数或超出long范围则保留原值
					try {
						quickColMeta.setDefaultValue(String.valueOf(
								new java.math.BigDecimal(quickColMeta.getDefaultValue()).longValueExact()));
					} catch (ArithmeticException e) {
						// 保留原值
					}
				}
			}
			quickColMeta.setNullable(colMeta.isNullable() ? "1" : "0");
			String importType = null;
			// 自定义类型匹配命中的配置(每列重置,仅命中时赋值,用于补充import-types)
			ColumnTypeMapping matchedTypeMapping = null;
			// 默认数据类型进行匹配
			String[] defaultMapped = TypeMappingResolver.defaultJavaType(sqlType, dialect, dbType);
			if (defaultMapped != null) {
				quickColMeta.setResultType(defaultMapped[0]);
				if (defaultMapped[1] != null) {
					importType = defaultMapped[1];
				}
			}
			// 额外数据类型匹配,匹配以最后一个设置为准
			matchedTypeMapping = TypeMappingResolver.matchCustomMapping(typeMapping, tableField, sqlType,
					colMeta.getPrecision(), colMeta.getScale(), maxScale);
			if (matchedTypeMapping != null) {
				if (null != matchedTypeMapping.getJdbcType()) {
					quickColMeta.setDataType(matchedTypeMapping.getJdbcType());
				}
				quickColMeta.setResultType(matchedTypeMapping.getResultType());
				// 规避数组类型
				importType = matchedTypeMapping.getJavaType().replaceAll("\\[", "").replaceAll("\\]", "").trim();
			}
			// 存在外键,则设置对应外键表和对应字段
			if (fks != null && !fks.isEmpty()) {
				for (TableConstractModel constractModel : fks) {
					if (colMeta.getColName().equalsIgnoreCase(constractModel.getFkColName())) {
						quickColMeta.setFkName(constractModel.getFkName());
						quickColMeta.setFkRefTableName(constractModel.getFkRefTableName());
						quickColMeta.setFkRefTableColName(constractModel.getPkColName());
						quickColMeta.setDeleteRestict(constractModel.getDeleteRule());
						quickColMeta.setUpdateRestict(constractModel.getUpdateRule());
						// 外键
						if (!impList.contains("org.sagacity.sqltoy.config.annotation.Foreign")) {
							impList.add("org.sagacity.sqltoy.config.annotation.Foreign");
						}
						// 参照动作以常量形式输出时才引入(非标准JDBC规则值回退为字面值,无需引入)
						if (QuickColMeta.referentialActionName(constractModel.getDeleteRule()) != null
								|| QuickColMeta.referentialActionName(constractModel.getUpdateRule()) != null) {
							if (!impList.contains("org.sagacity.sqltoy.config.model.ReferentialAction")) {
								impList.add("org.sagacity.sqltoy.config.model.ReferentialAction");
							}
						}
						break;
					}
				}
			}
			if (quickColMeta.getResultType() == null) {
				logger.info(Msg.get("字段:[","Column: [") + colMeta.getColName() + Msg.get("]数据类型:[","] type: [") + colMeta.getTypeName() + Msg.get("]数据长度:[","] length: [")
						+ colMeta.getPrecision() + Msg.get("]小数位:[","] scale: [") + colMeta.getScale() + Msg.get("]没有设置对应的java-type!","] has no java-type mapping!"));
				logger.info(Msg.get("请在quickvo.xml 正确配置<sql-type native-types=\"","Please configure <sql-type native-types=\"") + colMeta.getTypeName()
						+ Msg.get("\" java-type=\"\" />","\" java-type=\"\" />"));
				// 兜底为String,避免模板渲染null导致整个生成过程以freemarker异常中断
				quickColMeta.setResultType("String");
			} else {
			}
			// 增加类引入类型对象(java-type自身的import与import-types补充导入并存,不再互斥)
			if (importType != null) {
				if (importType.indexOf(".") != -1 && !impList.contains(importType)) {
					impList.add(importType);
				}
				if (importType.toLowerCase().startsWith("list<") && !impList.contains("java.util.List")) {
					impList.add("java.util.List");
				}
			}
			if (matchedTypeMapping != null && StringUtil.isNotBlank(matchedTypeMapping.getImportTypes())) {
				for (String imp : matchedTypeMapping.getImportTypes().split("\\,")) {
					if (!impList.contains(imp.trim())) {
						impList.add(imp.trim());
					}
				}
			}
			if (hasApiDoc && StringUtil.isNotBlank(configModel.getDocApiFieldTemplate())) {
				// @Schema(name="${colName}",description="${colRemark}",nullable=${nullable},type="${filedType}")
				String apiDoc = renderer.renderAdhoc(
						new String[] { "colName", "colRemark", "nullable", "fieldType", "fieldName", "defaultValue" },
						new Object[] { quickColMeta.getColName(), quickColMeta.getColRemark(),
								colMeta.isNullable() ? "true" : "false", quickColMeta.getResultType(),
								StringUtil.firstToLowerCase(quickColMeta.getColJavaName()),
								quickColMeta.getDefaultValue() },
						configModel.getDocApiFieldTemplate());
				quickColMeta.setApiDocContent(apiDoc);
			}
			if (isSkipField(quickModel.getSkipEntityExtendsFields(), quickColMeta.getColJavaName().toLowerCase())) {
				quickColMeta.setSkipEntity(true);
			}
			if (isSkipField(quickModel.getSkipVOExtendsFields(), quickColMeta.getColJavaName().toLowerCase())) {
				quickColMeta.setSkipVO(true);
			}
			quickColMetas.add(quickColMeta);
		}
		if (includePartition && hasPartitionKey) {
			impList.add("org.sagacity.sqltoy.config.annotation.PartitionKey");
		}
		return quickColMetas;
	}

	/** 给字段打上主键标记 */
	public static void setPk(List<QuickColMeta> colList, List<String> pkList) {
		if (pkList == null || pkList.isEmpty()) {
			return;
		}
		for (QuickColMeta colMeta : colList) {
			for (String pkCol : pkList) {
				if (colMeta.getColName().equalsIgnoreCase(pkCol)) {
					colMeta.setPrimaryKey(true);
					colMeta.setPkFlag("1");
				}
			}
		}
	}

	/**
	 * 处理主键和分区字段:存在非分区主键时,主键中属于分区字段的列为辅助主键
	 * (大多数数据库要求分区字段必须纳入主键),返回真实主键数量
	 */
	public static int processPartitionAndPk(List<QuickColMeta> cols) {
		int pkCnt = 0;
		for (QuickColMeta colMeta : cols) {
			// 主键且非分区字段,表明是真正的主键
			if (colMeta.isPrimaryKey() && !colMeta.getPartitionKey()) {
				pkCnt++;
			}
		}
		if (pkCnt > 0) {
			for (QuickColMeta colMeta : cols) {
				// 是主键且是分区字段,表示是辅助主键
				if (colMeta.isPrimaryKey() && colMeta.getPartitionKey()) {
					colMeta.setAssistPartition(true);
				}
			}
		}
		return pkCnt;
	}

	/** 非空字段数量 */
	public static int judgeFullNotNull(List<QuickColMeta> columns) {
		int notNullCnt = 0;
		for (QuickColMeta quickColMeta : columns) {
			if (!quickColMeta.getNullable().equalsIgnoreCase("1")) {
				notNullCnt++;
			}
		}
		return notNullCnt;
	}

	/**
	 * 修正reciveTime和recive_time形态并存的驼峰冲突:以最终java字段名(含前缀剥离)为基准
	 * 检测冲突,冲突组中(剥前缀后)列名含下划线的成员改为保留下划线的驼峰形态消歧;
	 * 检测与改名统一在前缀剥离后的同一键空间,避免配置fields-rid-prefix后误报改名和漏判真冲突
	 */
	public static void adjustCamelConflict(List<QuickColMeta> colList, String ridPrefix) {
		Map<String, Integer> javaNameCnt = new HashMap<>();
		for (QuickColMeta colMeta : colList) {
			javaNameCnt.merge(colMeta.getColJavaName().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
		}
		Set<String> resolved = new HashSet<>();
		for (QuickColMeta colMeta : colList) {
			String lower = colMeta.getColJavaName().toLowerCase(java.util.Locale.ROOT);
			if (javaNameCnt.get(lower) <= 1 || resolved.contains(lower)) {
				continue;
			}
			// 冲突组仅改名一个成员即消歧:优先改(剥前缀后)列名含下划线的成员
			String effectiveName = colMeta.getColName();
			if (StringUtil.isNotBlank(ridPrefix)
					&& effectiveName.toLowerCase(java.util.Locale.ROOT).startsWith(ridPrefix)) {
				effectiveName = effectiveName.substring(ridPrefix.length());
			}
			if (effectiveName.contains("_")) {
				colMeta.setColJavaName(StringUtil.toHumpStr(effectiveName, true, false));
				resolved.add(lower);
			}
		}
	}

	/**
	 * 校验列名转换出的Java字段名:非法标识符/Java关键字/同表字段重名都会让生成的源码
	 * 出现字段与getter无法编译的问题。此处汇总为插件错误并指明表与列,使失败发生在生成
	 * 阶段(quickvo:quickvo)而不是使用方的mvn compile阶段,便于定位到具体列名。
	 * 只报告不修改:字段命名与生成物格式保持历史行为。
	 * 校验须在{@link #adjustCamelConflict}之后调用,避免把已消歧的驼峰冲突误报为重名。
	 */
	public static void validateJavaFieldNames(QuickVoContext context, String tableName, List<QuickColMeta> colList) {
		Set<String> fieldNames = new HashSet<>();
		for (QuickColMeta colMeta : colList) {
			// 模板按?uncap_first输出字段名,首字母小写后的形态才是真正落盘的标识符(关键字判断也须用它)
			String fieldName = uncapFirst(colMeta.getColJavaName());
			String reason;
			if (StringUtil.isBlank(fieldName)) {
				reason = Msg.get("转换后字段名为空", "the converted field name is empty");
			} else if (!SourceVersion.isIdentifier(fieldName) || SourceVersion.isKeyword(fieldName)) {
				reason = Msg.get("不是合法的Java标识符或属于Java关键字",
						"is not a valid Java identifier or is a Java keyword");
			} else if (fieldNames.contains(fieldName)) {
				reason = Msg.get("与本表其它列转换出的字段名重复",
						"duplicates another column's field name in this table");
			} else {
				// 合法且未重复
				fieldNames.add(fieldName);
				continue;
			}
			context.addError(Msg.get("表:", "Table: ") + tableName + Msg.get(" 列:", " column: ")
					+ colMeta.getColName() + Msg.get(" 转换出的Java字段名[", " maps to Java field name [") + fieldName
					+ "] " + reason + Msg.get(",生成的字段/getter无法编译,请调整列名或改用视图为该列起合法别名!",
							"; the generated field/getter won't compile, rename the column or expose it via a view with a legal alias!"));
		}
	}

	/**
	 * 与模板?uncap_first一致的键算法:仅对首字符做Character.toLowerCase。
	 * 不能用StringUtil.firstToLowerCase:U+0130(İ)等字符的字符串级小写会映射为多字符
	 * (İ→i+组合点),导致校验键与实际生成物不一致(同表另有同名列时漏报重复)
	 */
	private static String uncapFirst(String name) {
		if (name == null || name.isEmpty()) {
			return name;
		}
		char first = name.charAt(0);
		char lowered = Character.toLowerCase(first);
		return lowered == first ? name : lowered + name.substring(1);
	}

	private static boolean isSkipField(String[] skipFields, String fieldName) {
		if (skipFields == null || skipFields.length == 0) {
			return false;
		}
		for (String skipField : skipFields) {
			if (skipField.equals(fieldName)) {
				return true;
			}
		}
		return false;
	}
}
