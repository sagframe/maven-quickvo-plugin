/**
 * 
 */
package org.sagacity.quickvo.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @project sagacity-quickvo
 * @description quickvo 存放列信息的对象
 * @author zhongxuchen
 * @version v1.0,Date:2009-04-19
 */
public class QuickColMeta implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3913303659891945911L;

	private boolean skipEntity = false;

	private boolean skipVO = false;

	/**
	 * 字段名称
	 */
	private String colName;

	/**
	 * 字段注释
	 */
	private String colRemark;

	/**
	 * 字段java名称
	 */
	private String colJavaName;

	/**
	 * 字段类型
	 */
	private String colType;

	/**
	 * jdbc中的数据类型
	 */
	private String dataType;

	/**
	 * 数据总长度
	 */
	private Integer precision;

	/**
	 * 小数位
	 */
	private Integer scale;

	/**
	 * 代码中的类型
	 */
	private String resultType;

	/**
	 * 可否位空
	 */
	private String nullable;


	/**
	 * 外键名称
	 */
	private String fkName;

	/**
	 * 外键对应的表名称
	 */
	private String fkRefTableName;


	/**
	 * 外键对应表字段名称
	 */
	private String fkRefTableColName;


	/**
	 * 是否为主键
	 */
	private String pkFlag = "0";

	/**
	 * 主键生成策略
	 */
	private String strategy = "assign";

	private String sequence;

	private String generator;

	private String autoIncrement = "false";

	private int generatedType = 0;


	private BusinessIdConfig businessIdConfig;

	private String apiDocContent;

	/**
	 * extra-annotations配置的字段级扩展注解(多行以换行分隔,如@DataVersion等)
	 */
	private String extraAnnotations;

	private boolean primaryKey = false;

	/**
	 * 乐观锁版本字段(data-version配置命中)
	 */
	private boolean dataVersion = false;

	/**
	 * 版本形式为yyyyMMdd+流水(data-version的start-date)
	 */
	private boolean dataVersionStartDate = false;
	/**
	 * 分区字段
	 */
	private Boolean partitionKey = false;

	/**
	 * default值
	 */
	private String defaultValue;

	private int deleteRestict;

	private int updateRestict;

	/**
	 * 是否是辅助分区字段
	 */
	private boolean assistPartition = false;

	public String getPkFlag() {
		return pkFlag;
	}

	public void setPkFlag(String pkFlag) {
		this.pkFlag = pkFlag;
	}

	/**
	 * @return the colName
	 */
	public String getColName() {
		return colName;
	}

	/**
	 * @param colName the colName to set
	 */
	public void setColName(String colName) {
		this.colName = colName;
	}

	/**
	 * @return the colJavaName
	 */
	public String getColJavaName() {
		return colJavaName;
	}

	/**
	 * @param colJavaName the colJavaName to set
	 */
	public void setColJavaName(String colJavaName) {
		this.colJavaName = colJavaName;
	}

	/**
	 * @return the precision
	 */
	public Integer getPrecision() {
		return precision;
	}

	/**
	 * @param precision the precision to set
	 */
	public void setPrecision(Integer precision) {
		this.precision = precision;
	}

	/**
	 * @return the scale
	 */
	public Integer getScale() {
		return scale;
	}

	/**
	 * @param scale the scale to set
	 */
	public void setScale(Integer scale) {
		this.scale = scale;
	}

	/**
	 * @return the resultType
	 */
	public String getResultType() {
		return resultType;
	}

	/**
	 * @param resultType the resultType to set
	 */
	public void setResultType(String resultType) {
		this.resultType = resultType;
	}

	/**
	 * @return the nullable
	 */
	public String getNullable() {
		return nullable;
	}

	/**
	 * @param nullable the nullable to set
	 */
	public void setNullable(String nullable) {
		this.nullable = nullable;
	}

	/**
	 * @return the fkRefTableName
	 */
	public String getFkRefTableName() {
		return fkRefTableName;
	}

	/**
	 * @param fkRefTableName the fkRefTableName to set
	 */
	public void setFkRefTableName(String fkRefTableName) {
		this.fkRefTableName = fkRefTableName;
	}

	/**
	 * @return the fkRefJavaTableName
	 */

	/**
	 * @param fkRefJavaTableName the fkRefJavaTableName to set
	 */

	/**
	 * @return the fkRefTableColName
	 */
	public String getFkRefTableColName() {
		return fkRefTableColName;
	}

	/**
	 * @param fkRefTableColName the fkRefTableColName to set
	 */
	public void setFkRefTableColName(String fkRefTableColName) {
		this.fkRefTableColName = fkRefTableColName;
	}

	/**
	 * @return the fkRefTableColJavaName
	 */

	/**
	 * @param fkRefTableColJavaName the fkRefTableColJavaName to set
	 */

	/**
	 * @return the colRemark
	 */
	public String getColRemark() {
		return colRemark;
	}

	/**
	 * @param colRemark the colRemark to set
	 */
	public void setColRemark(String colRemark) {
		// 入参已经TableColumnMeta.getColRemark完成escapeComment的Java字面量转义,必须原样存储;
		// 历史上的引号替换+Matcher.quoteReplacement是旧正则替换时代残留,会把$变成\$、
		// 把已转义反斜杠二次翻倍,导致生成代码编译失败或备注内容损坏
		this.colRemark = colRemark;
	}

	/**
	 * @return the colTypeFlag
	 */

	/**
	 * @param colTypeFlag the colTypeFlag to set
	 */

	/**
	 * @return the dataType
	 */
	public String getDataType() {
		return dataType;
	}

	/**
	 * @param dataType the dataType to set
	 */
	public void setDataType(String dataType) {
		this.dataType = dataType;
	}

	/**
	 * @return the strategy
	 */
	public String getStrategy() {
		return strategy;
	}

	/**
	 * @param strategy the strategy to set
	 */
	public void setStrategy(String strategy) {
		this.strategy = strategy;
	}

	/**
	 * @return the sequence
	 */
	public String getSequence() {
		return sequence;
	}

	/**
	 * @param sequence the sequence to set
	 */
	public void setSequence(String sequence) {
		this.sequence = sequence;
	}

	/**
	 * @return the generator
	 */
	public String getGenerator() {
		return generator;
	}

	/**
	 * @param generator the generator to set
	 */
	public void setGenerator(String generator) {
		this.generator = generator;
	}

	/**
	 * @return the autoIncrement
	 */
	public String getAutoIncrement() {
		return autoIncrement;
	}

	/**
	 * @param autoIncrement the autoIncrement to set
	 */
	public void setAutoIncrement(String autoIncrement) {
		this.autoIncrement = autoIncrement;
	}

	/**
	 * @return the defaultValue
	 */
	public String getDefaultValue() {
		return defaultValue;
	}

	/**
	 * @param defaultValue the defaultValue to set
	 */
	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	/**
	 * @return the pkRefConstract
	 */

	/**
	 * @param pkRefConstract the pkRefConstract to set
	 */

	/**
	 * @return the businessIdConfig
	 */
	public BusinessIdConfig getBusinessIdConfig() {
		return businessIdConfig;
	}

	/**
	 * @param businessIdConfig the businessIdConfig to set
	 */
	public void setBusinessIdConfig(BusinessIdConfig businessIdConfig) {
		this.businessIdConfig = businessIdConfig;
	}

	public String getColType() {
		return colType;
	}

	public void setColType(String colType) {
		this.colType = colType;
	}

	/**
	 * @return the partitionKey
	 */
	public Boolean getPartitionKey() {
		return partitionKey;
	}

	/**
	 * @param partitionKey the partitionKey to set
	 */
	public void setPartitionKey(Boolean partitionKey) {
		this.partitionKey = partitionKey;
	}

	public String getApiDocContent() {
		return apiDocContent;
	}

	public void setApiDocContent(String apiDocContent) {
		this.apiDocContent = apiDocContent;
	}

	public String getExtraAnnotations() {
		return extraAnnotations;
	}

	public void setExtraAnnotations(String extraAnnotations) {
		this.extraAnnotations = extraAnnotations;
	}

	public boolean isSkipEntity() {
		return skipEntity;
	}

	public void setSkipEntity(boolean skipEntity) {
		this.skipEntity = skipEntity;
	}

	public boolean isSkipVO() {
		return skipVO;
	}

	public void setSkipVO(boolean skipVO) {
		this.skipVO = skipVO;
	}

	public String getFkName() {
		return fkName;
	}

	public void setFkName(String fkName) {
		this.fkName = fkName;
	}

	public int getDeleteRestict() {
		return deleteRestict;
	}

	public void setDeleteRestict(int deleteRestict) {
		this.deleteRestict = deleteRestict;
	}

	public int getUpdateRestict() {
		return updateRestict;
	}

	public void setUpdateRestict(int updateRestict) {
		this.updateRestict = updateRestict;
	}

	/**
	 * deleteRestict在注解中的输出表达式,0-4映射为ReferentialAction常量便于阅读,
	 * 其余值(如JDBC延迟约束的5:deferred、6:immediate)保留原值
	 */
	public String getDeleteRestictExpr() {
		return toRestictExpr(deleteRestict);
	}

	/**
	 * updateRestict在注解中的输出表达式,映射规则同deleteRestict
	 */
	public String getUpdateRestictExpr() {
		return toRestictExpr(updateRestict);
	}

	/**
	 * 参照动作值对应的ReferentialAction常量名,非标准JDBC规则值返回null
	 */
	public static String referentialActionName(int rule) {
		switch (rule) {
		case 0:
			return "CASCADE";
		case 1:
			return "RESTRICT";
		case 2:
			return "SET_NULL";
		case 3:
			return "NO_ACTION";
		case 4:
			return "SET_DEFAULT";
		default:
			return null;
		}
	}

	private static String toRestictExpr(int rule) {
		String name = referentialActionName(rule);
		return name == null ? String.valueOf(rule) : "ReferentialAction.".concat(name);
	}

	public int getGeneratedType() {
		return generatedType;
	}

	public void setGeneratedType(int generatedType) {
		this.generatedType = generatedType;
	}

	public boolean isAssistPartition() {
		return assistPartition;
	}

	public void setAssistPartition(boolean assistPartition) {
		this.assistPartition = assistPartition;
	}

	public boolean isPrimaryKey() {
		return primaryKey;
	}

	public void setPrimaryKey(boolean primaryKey) {
		this.primaryKey = primaryKey;
	}


	/**
	 * @return the dataVersion
	 */
	public boolean isDataVersion() {
		return dataVersion;
	}

	/**
	 * @param dataVersion the dataVersion to set
	 */
	public void setDataVersion(boolean dataVersion) {
		this.dataVersion = dataVersion;
	}

	/**
	 * @return the dataVersionStartDate
	 */
	public boolean isDataVersionStartDate() {
		return dataVersionStartDate;
	}

	/**
	 * @param dataVersionStartDate the dataVersionStartDate to set
	 */
	public void setDataVersionStartDate(boolean dataVersionStartDate) {
		this.dataVersionStartDate = dataVersionStartDate;
	}
}
