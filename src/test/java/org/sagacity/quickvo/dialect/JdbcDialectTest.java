package org.sagacity.quickvo.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.model.TableColumnMeta;

/**
 * JdbcDialect默认值启发式单测:引号标识符(列引用)判定不得把字符串字面量内的双引号
 * (json/文本默认值)误判为STORED生成列,误判会使列被sqltoy排除出insert/update
 */
public class JdbcDialectTest {

	@Test
	public void jsonLiteralDefaultShouldNotBeTreatedAsGeneratedColumn() {
		assertFalse(JdbcDialect.isQuotedIdentifierGeneratedDefault("'{\"a\":1}'::jsonb"));
		assertFalse(JdbcDialect.isQuotedIdentifierGeneratedDefault("'{\"type\":\"x\"}'"));
		assertFalse(JdbcDialect.isQuotedIdentifierGeneratedDefault("'say \"hi\"'"));
		// 含''转义的单引号字面量
		assertFalse(JdbcDialect.isQuotedIdentifierGeneratedDefault("'it''s \"x\"'"));
	}

	@Test
	public void columnReferenceDefaultShouldBeTreatedAsGeneratedColumn() {
		assertTrue(JdbcDialect.isQuotedIdentifierGeneratedDefault("\"SALARY\"+\"BONUS\""));
	}

	@Test
	public void sequenceAndPlainDefaultShouldNotBeTreatedAsGeneratedColumn() {
		assertFalse(JdbcDialect.isQuotedIdentifierGeneratedDefault("\"SCHEMA\".\"SEQ_X\".nextval"));
		assertFalse(JdbcDialect.isQuotedIdentifierGeneratedDefault("nextval('seq'::regclass)"));
		assertFalse(JdbcDialect.isQuotedIdentifierGeneratedDefault(null));
		assertFalse(JdbcDialect.isQuotedIdentifierGeneratedDefault("1"));
		assertFalse(JdbcDialect.isQuotedIdentifierGeneratedDefault("CURRENT_TIMESTAMP"));
	}

	@Test
	public void hiddenColumnsShouldBeRemovedButOthersKept() {
		// Oracle/达梦的invisible列经JDBC getColumns仍会返回,须按数据字典HIDDEN_COLUMN剔除
		JdbcDialect dialect = new JdbcDialect(DbType.ORACLE);
		List<TableColumnMeta> cols = new ArrayList<>();
		for (String name : new String[] { "ID", "SECRET_COL", "NAME" }) {
			TableColumnMeta col = new TableColumnMeta();
			col.setColName(name);
			cols.add(col);
		}
		List<TableColumnMeta> result = dialect.removeHiddenColumns("T_PROBE", cols, Set.of("SECRET_COL"));
		assertEquals(2, result.size());
		assertFalse(result.stream().anyMatch(c -> "SECRET_COL".equals(c.getColName())));
		// 无隐藏列时原样返回
		assertEquals(3, dialect.removeHiddenColumns("T_PROBE", cols, Set.of()).size());
	}
}
