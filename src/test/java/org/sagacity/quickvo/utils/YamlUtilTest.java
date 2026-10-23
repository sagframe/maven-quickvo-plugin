package org.sagacity.quickvo.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * YAML属性文件解析单测:对象层级以"."展平,标量(字符串/数字/布尔)按原文入map;
 * 列表整体跳过——历史实现把START_ARRAY当字符串取到"["、并把列表项内的key挂到错误前缀
 * (如app.servers[0].host被写成app.host),属静默脏值。
 */
public class YamlUtilTest {

	@TempDir
	Path tempDir;

	@Test
	public void nestedMapShouldBeFlattenedWithDotKeys() throws Exception {
		Map<String, String> map = load("""
				spring:
				  datasource:
				    url: jdbc:mysql://localhost:3306/demo?useUnicode=true
				    username: root
				    password: p@ss
				app:
				  name: demo
				""");
		assertEquals("jdbc:mysql://localhost:3306/demo?useUnicode=true", map.get("spring.datasource.url"));
		assertEquals("root", map.get("spring.datasource.username"));
		assertEquals("p@ss", map.get("spring.datasource.password"));
		assertEquals("demo", map.get("app.name"));
		assertEquals(4, map.size());
	}

	@Test
	public void scalarTypesShouldKeepTextualValue() throws Exception {
		Map<String, String> map = load("""
				ports: 3306
				ratio: 1.5
				enabled: true
				disabled: false
				""");
		assertEquals("3306", map.get("ports"));
		assertEquals("1.5", map.get("ratio"));
		assertEquals("true", map.get("enabled"));
		assertEquals("false", map.get("disabled"));
	}

	@Test
	public void listValueShouldBeSkippedInsteadOfDirtyBracket() throws Exception {
		// 历史缺陷:app.roles被写成"["(START_ARRAY的文本形态)
		Map<String, String> map = load("""
				app:
				  name: demo
				  roles:
				    - admin
				    - user
				""");
		assertEquals("demo", map.get("app.name"));
		assertFalse(map.containsKey("app.roles"), "列表不是合法属性值,不应写入任何形态");
		assertFalse(map.containsValue("["), "不得出现\"[\"脏值");
	}

	@Test
	public void listOfMapsShouldNotLeakInnerKeys() throws Exception {
		// 历史缺陷:列表项内的host/port被挂到app前缀(app.host/app.port)
		Map<String, String> map = load("""
				app:
				  servers:
				    - host: a
				      port: 8080
				    - host: b
				      port: 9090
				  name: demo
				""");
		assertEquals(1, map.size());
		assertEquals("demo", map.get("app.name"));
		assertFalse(map.containsKey("app.host"));
		assertFalse(map.containsKey("app.port"));
		assertFalse(map.containsKey("app.servers"));
	}

	@Test
	public void inlineListShouldBeSkipped() throws Exception {
		Map<String, String> map = load("""
				server:
				  ports: [8080, 9090]
				  name: x
				""");
		assertEquals(1, map.size());
		assertEquals("x", map.get("server.name"));
	}

	@Test
	public void nullValueShouldNotBeStored() throws Exception {
		Map<String, String> map = load("""
				empty:
				next: value
				""");
		assertFalse(map.containsKey("empty"));
		assertEquals("value", map.get("next"));
		assertEquals(1, map.size());
	}

	@Test
	public void quotedValueShouldKeepColonAndHash() throws Exception {
		Map<String, String> map = load("""
				url: "jdbc:mysql://h:3306/db?x=1"
				tag: "a # b"
				""");
		assertEquals("jdbc:mysql://h:3306/db?x=1", map.get("url"));
		assertEquals("a # b", map.get("tag"));
	}

	@Test
	public void rootLevelListShouldBeIgnoredWithoutError() throws Exception {
		Map<String, String> map = load("""
				- a
				- b
				""");
		assertTrue(map.isEmpty());
	}

	@Test
	public void aliasShouldResolveToAnchoredValue() throws Exception {
		// 历史缺陷:流式实现把alias取成锚名字符串(x=a而非x=val)
		Map<String, String> map = load("""
				scalar: &a val
				x: *a
				base: &b
				  host: h1
				dev:
				  ds: *b
				""");
		assertEquals("val", map.get("x"));
		assertEquals("h1", map.get("dev.ds.host"));
		assertFalse(map.containsValue("a"), "锚名不得作为值");
		assertFalse(map.containsValue("b"), "锚名不得作为值");
	}

	@Test
	public void mergeKeyShouldMergeWithExplicitKeysWinning() throws Exception {
		// 历史缺陷:merge key(<<)被写成"<<"脏键;现按YAML语义合并、显式键优先
		Map<String, String> map = load("""
				base: &b
				  host: h1
				  port: 1
				dev:
				  <<: *b
				  port: 2
				""");
		assertEquals("h1", map.get("dev.host"));
		assertEquals("2", map.get("dev.port"));
		assertFalse(map.containsKey("dev.<<"));
	}

	@Test
	public void multiDocumentShouldLoadInOrder() throws Exception {
		Map<String, String> map = load("""
				a: first
				---
				a: second
				b: kept
				""");
		assertEquals("second", map.get("a"));
		assertEquals("kept", map.get("b"));
	}

	@Test
	public void duplicateKeyShouldLetLaterOneWin() throws Exception {
		Map<String, String> map = load("""
				a: first
				a: second
				""");
		assertEquals("second", map.get("a"));
		assertEquals(1, map.size());
	}

	@Test
	public void recursiveAliasShouldNotOverflow() throws Exception {
		// YAML允许自引用锚点:展开须有界,不能StackOverflowError(Error不会被catch(Exception)兜住)
		Map<String, String> map = load("""
				a: &x
				  b: *x
				c: kept
				""");
		assertEquals("kept", map.get("c"));
	}

	@Test
	public void mergeListShouldPreferEarlierMap() throws Exception {
		// YAML规范:<<的值为映射序列时,靠前的映射优先
		Map<String, String> map = load("""
				a: &a
				  x: fromA
				b: &b
				  x: fromB
				dev:
				  <<: [*a, *b]
				""");
		assertEquals("fromA", map.get("dev.x"));
	}

	@Test
	public void explicitNullShouldOverrideMergedKey() throws Exception {
		// 显式键存在(即使为空值)即优先于merge合入
		Map<String, String> map = load("""
				base: &b
				  host: h1
				dev:
				  <<: *b
				  host:
				""");
		assertEquals("h1", map.get("base.host"));
		assertFalse(map.containsKey("dev.host"));
	}

	private Map<String, String> load(String content) throws Exception {
		Path yml = tempDir.resolve("quickvo-probe.yml");
		Files.writeString(yml, content, StandardCharsets.UTF_8);
		Map<String, String> map = new LinkedHashMap<>();
		YamlUtil.loadYml(map, yml.toFile());
		return map;
	}
}
