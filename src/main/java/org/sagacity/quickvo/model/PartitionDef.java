package org.sagacity.quickvo.model;

/**
 * 单个分区定义:名称+边界值(或MAXVALUE)+表空间,渲染为@PartitionDef(name=..,value=..,tablespace=..),
 * 供sqltoy生成DDL时还原真实分区子句(mysql系RANGE/LIST必须显式给出分区清单)。
 * update 2026-10-13 补endValue/endInclusive:DB2表分区为STARTING..ENDING双边界区间
 * (value为下界,endValue为上界,endInclusive=false渲染EXCLUSIVE),单边界库不使用。
 * 注意:用普通getter而非record——record的value()访问器与FreeMarker内置语法冲突无法在模板中取值
 *
 * @author zhongxuchen
 */
public class PartitionDef {
	private final String name;
	private final String value;
	private final String tablespace;
	/** 分区上界(DB2双边界区间用;单边界库为null) */
	private final String endValue;
	/** 上界是否包含(DB2的ENDING默认INCLUSIVE,EXCLUSIVE为false);仅endValue非null时有意义 */
	private final boolean endInclusive;

	public PartitionDef(String name, String value, String tablespace) {
		this(name, value, tablespace, null, true);
	}

	public PartitionDef(String name, String value, String tablespace, String endValue, boolean endInclusive) {
		this.name = name;
		this.value = value;
		this.tablespace = tablespace;
		this.endValue = endValue;
		this.endInclusive = endInclusive;
	}

	public String getName() {
		return name;
	}

	public String getValue() {
		return value;
	}

	public String getTablespace() {
		return tablespace;
	}

	public String getEndValue() {
		return endValue;
	}

	public boolean getEndInclusive() {
		return endInclusive;
	}
}
