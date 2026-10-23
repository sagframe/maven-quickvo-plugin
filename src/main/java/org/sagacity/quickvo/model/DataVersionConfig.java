package org.sagacity.quickvo.model;

/**
 * data-version配置:按表正则声明乐观锁版本字段,生成sqltoy字段级@DataVersion注解。
 * 版本字段直接标注(非类级field=..指向),字段重命名安全。
 *
 * @author zhongxuchen
 */
public record DataVersionConfig(String tableName, String column, boolean startDate) {
}
