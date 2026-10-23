package org.sagacity.quickvo.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * YAML配置文件解析(基于snakeyaml-engine):对象层级key以"."拼接放入Map,如spring.datasource.url。
 * 只接受标量值(字符串/数字/布尔):数组整体跳过——本插件的属性仅用于${}标量替换,列表不是合法
 * 属性值。锚点/引用(&anchor / *alias)与多文档(---)由解析器在Compositor层正确展开;merge key
 * (<<)按"显式键优先、序列中靠前映射优先"的YAML语义合并。历史流式实现把alias取成锚名字符串、
 * merge key写成"<<"脏键,属静默脏值。
 *
 * @author zhongxuchen
 */
public class YamlUtil {

	/** YAML merge key:值为被合并的map(或map列表),键本身不是属性 */
	private static final String MERGE_KEY = "<<";

	private YamlUtil() {
	}

	/**
	 * 加载yml文件(层级key以.拼接,如spring.datasource.url),多文档按序加载(后者覆盖前者)
	 */
	public static void loadYml(Map<String, String> constantMap, File ymlFile) {
		try (var reader = new InputStreamReader(new FileInputStream(ymlFile), StandardCharsets.UTF_8)) {
			// 重复key沿用历史语义:后者覆盖前者,而非解析失败
			var settings = LoadSettings.builder().setAllowDuplicateKeys(true).build();
			Iterable<Object> docs = new Load(settings).loadAllFromReader(reader);
			for (Object doc : docs) {
				if (doc instanceof Map<?, ?> root) {
					// 每文档独立的"当前递归路径":YAML允许自引用锚点(a: &x {b: *x}),不设界会无限递归
					flatten(constantMap, "", root,
							Collections.newSetFromMap(new IdentityHashMap<Map<?, ?>, Boolean>()));
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(Msg.get("加载YAML文件失败: ", "Failed to load YAML file: ") + ymlFile.getPath(), e);
		}
	}

	/**
	 * 递归展平:merge key(<<)先展平合入,同层显式键后写入覆盖之,与YAML"显式键优先"语义一致;
	 * 序列形式的merge按规范"靠前的映射优先",故逆序合入让靠前者最后写入
	 */
	private static void flatten(Map<String, String> out, String prefix, Map<?, ?> node,
			Set<Map<?, ?>> visiting) {
		if (!visiting.add(node)) {
			// 递归别名:同一map已在当前展开路径上,跳过避免StackOverflowError
			return;
		}
		try {
			Object merged = null;
			for (var entry : node.entrySet()) {
				if (MERGE_KEY.equals(String.valueOf(entry.getKey()))) {
					merged = entry.getValue();
					break;
				}
			}
			if (merged instanceof Map<?, ?> map) {
				flatten(out, prefix, map, visiting);
			} else if (merged instanceof Collection<?> list) {
				List<Object> items = new ArrayList<>(list);
				for (int i = items.size() - 1; i >= 0; i--) {
					if (items.get(i) instanceof Map<?, ?> map) {
						flatten(out, prefix, map, visiting);
					}
				}
			}
			for (var entry : node.entrySet()) {
				String key = String.valueOf(entry.getKey()).trim();
				if (MERGE_KEY.equals(key)) {
					continue;
				}
				Object value = entry.getValue();
				if (value == null) {
					// 显式空值同样覆盖merge合入的同名键(YAML:显式键存在即优先)
					out.remove(prefix + key);
					continue;
				}
				putScalar(out, prefix + key, value, visiting);
			}
		} finally {
			visiting.remove(node);
		}
	}

	/** 标量入map;嵌套map继续展平;null(未配置,由调用方移除)与列表(非属性值形态)不写入 */
	private static void putScalar(Map<String, String> out, String key, Object value,
			Set<Map<?, ?>> visiting) {
		if (value == null) {
			return;
		}
		if (value instanceof Map<?, ?> map) {
			flatten(out, key + ".", map, visiting);
			return;
		}
		if (value instanceof Collection<?> || value instanceof Object[]) {
			return;
		}
		out.put(key, String.valueOf(value).trim());
	}
}
