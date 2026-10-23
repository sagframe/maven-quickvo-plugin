package org.sagacity.quickvo.dialect;

/**
 * MPP分析库表引擎元数据(ClickHouse/Doris/StarRocks),
 * 生成@MppTable注解供sqltoy的DDL生成还原ENGINE/KEY/DISTRIBUTED/SETTINGS
 *
 * @author zhongxuchen
 */
public record MppTableInfo(String engine, String engineArgs, String keyModel, String[] orderBy,
		String[] distributedBy, int buckets, String[] properties) {
}
