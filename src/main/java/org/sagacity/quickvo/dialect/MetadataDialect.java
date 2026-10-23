package org.sagacity.quickvo.dialect;

import java.sql.SQLException;
import java.util.List;

import org.sagacity.quickvo.datasource.DbSession;
import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.model.IndexModel;
import org.sagacity.quickvo.model.TableColumnMeta;
import org.sagacity.quickvo.model.TableMeta;

/**
 * 数据库元数据提取方言:表列表、列信息、主键、主键约束、表注释、索引。
 * 默认实现走JDBC DatabaseMetaData,各数据库只在存在注释/分区/生成列等信息缺失时覆盖对应方法。
 *
 * @author zhongxuchen
 */
public interface MetadataDialect {

	/** 方言对应的数据库类型 */
	DbType getDbType();

	/**
	 * 列出当前数据源全部表和视图(含注释),任务的include/exclude过滤由调用方完成
	 */
	List<TableMeta> listTables(DbSession db) throws SQLException;

	/**
	 * 提取表列信息(含注释/默认值/分区/生成列等扩展信息);toUpperCase针对oracle的schema大小写问题
	 */
	List<TableColumnMeta> getColumns(DbSession db, String tableName, boolean toUpperCase) throws SQLException;

	/**
	 * 提取主键列(按约束内顺序)
	 */
	List<String> getPrimaryKeys(DbSession db, String tableName) throws SQLException;

	/**
	 * 提取主键约束名称
	 */
	String getPkConstraint(DbSession db, String tableName) throws SQLException;

	/**
	 * 提取表注释(仅部分数据库JDBC元数据不返回注释时需要覆盖)
	 */
	default String getTableRemark(DbSession db, String tableName) throws SQLException {
		return null;
	}

	/**
	 * 提取表分区策略(RANGE/LIST/HASH等)与表达式;非分区表返回null
	 */
	default TablePartition getTablePartition(DbSession db, String tableName) throws SQLException {
		return null;
	}

	/**
	 * 提取MPP表引擎元数据(ClickHouse/Doris/StarRocks的ENGINE/排序键/键模型/分桶/属性);
	 * 非MPP数据库或提取不到返回null
	 */
	default MppTableInfo getMppTableInfo(DbSession db, String tableName) throws SQLException {
		return null;
	}

	/**
	 * 提取索引信息(排除主键索引),唯一索引在前
	 */
	List<IndexModel> getIndexInfo(DbSession db, String tableName, String pkName) throws SQLException;
}
