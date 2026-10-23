package org.sagacity.quickvo.dialect;

/**
 * 表分区元数据:策略(RANGE/LIST/HASH等)与分区表达式原文,
 * 生成@Partition注解供sqltoy的DDL同步等场景消费
 *
 * @author zhongxuchen
 */
public record TablePartition(String strategy, String expression,
		java.util.List<org.sagacity.quickvo.model.PartitionDef> defs) {

	/** 兼容无分区明细的构造(oracle/pg等策略型分区) */
	public TablePartition(String strategy, String expression) {
		this(strategy, expression, java.util.List.of());
	}

	/**
	 * 从分区表达式"RANGE (col1, col2)"提取键列名清单(引号包裹的标识符去引号);
	 * 供方言在列元信息上回标partitionKey——无pg_partitioned_table.partattrs类原生标记的库
	 * (openGauss系按pg_get_tabledef表达式回标/db2按PART_KEY_SEQ组装表达式后回标/sqlserver同)
	 */
	public static java.util.List<String> keyColumns(String expression) {
		java.util.List<String> columns = new java.util.ArrayList<>();
		if (expression == null) {
			return columns;
		}
		int open = expression.indexOf('(');
		int close = expression.lastIndexOf(')');
		if (open < 0 || close <= open) {
			return columns;
		}
		for (String col : expression.substring(open + 1, close).split(",")) {
			String name = col.trim().replaceAll("^\"|\"$", "");
			if (!name.isEmpty()) {
				columns.add(name);
			}
		}
		return columns;
	}

	/**
	 * 分区边界值格式化为DDL就绪字面量:空→MAXVALUE;纯数值原样(VALUES LESS THAN (100));
	 * 其余按SQL字面量加单引号(日期/时间戳/字符串边界,内部单引号翻倍转义)。
	 * openGauss系(pg_partition.boundaries)与sqlserver(sys.partition_range_values的CAST文本)
	 * 目录存裸值须格式化;db2的syscat.datapartitions已存DDL就绪形态(含引号/MAXVALUE关键字)直接透传
	 */
	public static String formatBoundaryValue(String rawValue) {
		if (rawValue == null || rawValue.trim().isEmpty()) {
			return "MAXVALUE";
		}
		String value = rawValue.trim();
		if (value.matches("-?\\d+(\\.\\d+)?")) {
			return value;
		}
		if (value.startsWith("'") || value.equalsIgnoreCase("MAXVALUE") || value.equalsIgnoreCase("MINVALUE")) {
			return value;
		}
		return "'" + value.replace("'", "''") + "'";
	}
}
