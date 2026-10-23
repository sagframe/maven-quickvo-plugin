/**
 * 
 */
package org.sagacity.quickvo.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @project sagacity-quickvo
 * @description 任务配置数据模型
 * @author zhongxuchen
 * @version v1.0,Date:2010-7-21
 */
@SuppressWarnings("rawtypes")
public class ConfigModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1745793126948000038L;

	/**
	 * 输出路径
	 */
	private String targetDir;

	/**
	 * 抽象vo的路径
	 */
	private String abstractPath = "base";

	/**
	 * 编码格式
	 */
	private String encoding = "UTF-8";

	/**
	 * 类型匹配
	 */
	private List<ColumnTypeMapping> typeMapping;

	/**
	 * 任务信息
	 */
	private List<QuickModel> tasks;


	
	
	private String[] docApiImports;
	
	private String docApiFieldTemplate;

	private String docApiClassTemplate;
	
	/**
	 * 级联设置
	 */
	// private HashMap<String, List<CascadeModel>> cascadeConfig = new
	// HashMap<String, List<CascadeModel>>();
	private List<CascadeModel> cascadeConfig = new ArrayList<CascadeModel>();

	/**
	 * 主键生成策略
	 */
	private List<PrimaryKeyStrategy> pkGeneratorStrategyList = new ArrayList<PrimaryKeyStrategy>();

	/**
	 * 扩展注解配置(表级)
	 */
	private List<ExtraAnnotationConfig> extraAnnotationList = new ArrayList<ExtraAnnotationConfig>();

	/**
	 * 业务主键id策略配置
	 */
	private HashMap<String, BusinessIdConfig> buisnessIdsMap = new HashMap<String, BusinessIdConfig>();

	/** data-version配置(表正则+列) */
	private java.util.List<DataVersionConfig> dataVersions = new java.util.ArrayList<>();

	/**
	 * @return the targetDir
	 */
	public String getTargetDir() {
		return targetDir;
	}

	/**
	 * @param targetDir the targetDir to set
	 */
	public void setTargetDir(String targetDir) {
		this.targetDir = targetDir;
	}

	/**
	 * @return the encoding
	 */
	public String getEncoding() {
		return encoding;
	}

	/**
	 * @param encoding the encoding to set
	 */
	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	/**
	 * @return the typeMapping
	 */
	public List<ColumnTypeMapping> getTypeMapping() {
		return typeMapping;
	}

	/**
	 * @param typeMapping the typeMapping to set
	 */
	public void setTypeMapping(List<ColumnTypeMapping> typeMapping) {
		this.typeMapping = typeMapping;
	}

	/**
	 * @return the tasks
	 */
	public List<QuickModel> getTasks() {
		return tasks;
	}

	/**
	 * @param tasks the tasks to set
	 */
	public void setTasks(List<QuickModel> tasks) {
		this.tasks = tasks;
	}

	/**
	 * @return the tableIncludes
	 */

	/**
	 * @param tableIncludes the tableIncludes to set
	 */

	/**
	 * @return the tableExcludes
	 */

	/**
	 * @param tableExcludes the tableExcludes to set
	 */

	/**
	 * @return the abstractPath
	 */
	public String getAbstractPath() {
		return abstractPath;
	}

	/**
	 * @param abstractPath the abstractPath to set
	 */
	public void setAbstractPath(String abstractPath) {
		this.abstractPath = abstractPath;
	}

	/**
	 * @return the pkGeneratorStrategyMap
	 */
	public List<PrimaryKeyStrategy> getPkGeneratorStrategy() {
		return pkGeneratorStrategyList;
	}

	/**
	 * @param pkGeneratorStrategyMap the pkGeneratorStrategyMap to set
	 */
	public void addPkGeneratorStrategy(PrimaryKeyStrategy primaryKeyStrategy) {
		this.pkGeneratorStrategyList.add(primaryKeyStrategy);
	}

	public void addExtraAnnotationConfig(ExtraAnnotationConfig extraAnnotationConfig) {
		this.extraAnnotationList.add(extraAnnotationConfig);
	}

	public List<ExtraAnnotationConfig> getExtraAnnotationList() {
		return extraAnnotationList;
	}

	/**
	 * @return the cascadeConfig
	 */
	public List<CascadeModel> getCascadeConfig() {
		return cascadeConfig;
	}

	public void addCascadeConfig(List<CascadeModel> cascadeModels) {
		this.cascadeConfig.addAll(cascadeModels);
	}

	public void addBusinessId(BusinessIdConfig businessIdConfig) {
		this.buisnessIdsMap.put(businessIdConfig.getTableName().toLowerCase(), businessIdConfig);
	}

	public BusinessIdConfig getBusinessId(String tableName) {
		return this.buisnessIdsMap.get(tableName.toLowerCase());
	}

	public void addDataVersion(DataVersionConfig config) {
		dataVersions.add(config);
	}

	/**
	 * 表名匹配:配置值为纯名称(仅字母/数字/下划线/连字符)时按字面精确匹配(忽略大小写),
	 * 含正则元字符时按正则完整匹配;多条命中以最后一条为准。
	 * 纯名称走字面量分支避免表名本身含'('、'.'等字符时被误当正则解析
	 */
	public DataVersionConfig getDataVersion(String tableName) {
		DataVersionConfig result = null;
		for (DataVersionConfig config : dataVersions) {
			String name = config.tableName();
			boolean matched = PLAIN_NAME.matcher(name).matches()
					? name.equalsIgnoreCase(tableName)
					: tableName.matches("(?i)".concat(name));
			if (matched) {
				result = config;
			}
		}
		return result;
	}

	/** 纯名称判定:仅字母/数字/下划线/连字符(含中文) */
	private static final java.util.regex.Pattern PLAIN_NAME = java.util.regex.Pattern
			.compile("[\\w\\-\\u4e00-\\u9fff]+");

	public String[] getDocApiImports() {
		return docApiImports;
	}

	public void setDocApiImports(String[] docApiImports) {
		this.docApiImports = docApiImports;
	}

	public String getDocApiFieldTemplate() {
		return docApiFieldTemplate;
	}

	public void setDocApiFieldTemplate(String docApiFieldTemplate) {
		this.docApiFieldTemplate = docApiFieldTemplate;
	}

	public String getDocApiClassTemplate() {
		return docApiClassTemplate;
	}

	public void setDocApiClassTemplate(String docApiClassTemplate) {
		this.docApiClassTemplate = docApiClassTemplate;
	}
}
