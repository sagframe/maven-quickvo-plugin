package org.sagacity.quickvo.config;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.sagacity.quickvo.utils.FileUtil;
import org.sagacity.quickvo.utils.StringUtil;
import org.sagacity.quickvo.utils.YamlUtil;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.sagacity.quickvo.utils.Msg;

/**
 * ${}占位符属性解析:quickvo.xml中的property元素、外部properties/yml文件、系统属性三层来源。
 * 原先存放于Constants的static HashMap,现按次执行实例化隔离。
 *
 * @author zhongxuchen
 */
public class PropertyResolver {
	/** 全局实例标识,配合##{globa.identity}占位符使用 */
	private static final String GLOBA_IDENTITY_NAME = "globa.identity";

	private static final String GLOBA_IDENTITY = "##{globa.identity}";

	private final Path baseDir;

	private final Map<String, String> constantMap = new HashMap<>();

	public PropertyResolver(Path baseDir) {
		this.baseDir = baseDir;
	}

	/**
	 * 加载quickvo.xml中的property元素(name/value或file属性)
	 */
	public void loadProperties(NodeList paramElts) throws Exception {
		String guid = System.getProperty(GLOBA_IDENTITY_NAME);
		if (guid == null) {
			guid = "";
		}
		if (paramElts == null || paramElts.getLength() == 0) {
			return;
		}
		for (int i = 0; i < paramElts.getLength(); i++) {
			Element elt = (Element) paramElts.item(i);
			if (elt.hasAttribute("name")) {
				String rawValue = elt.hasAttribute("value") ? elt.getAttribute("value")
						: StringUtil.trim(elt.getTextContent());
				// 先存原值,全部加载后统一做不动点解析:属性值的${}展开不再依赖定义顺序
				constantMap.put(elt.getAttribute("name"),
						StringUtil.replaceAllStr(rawValue, GLOBA_IDENTITY, guid));
			} else if (elt.hasAttribute("file")) {
				loadPropertyFile(
						replace(StringUtil.replaceAllStr(elt.getAttribute("file"), GLOBA_IDENTITY, guid)));
			}
		}
		resolvePlaceholders();
	}

	/**
	 * 迭代解析常量值中的${}占位符至不动点:与属性定义顺序及HashMap迭代序无关
	 * (历史实现在put时即解析,前向引用永不展开、嵌套值是否展开随迭代序漂移);
	 * 循环引用(a=${b},b=${a})在有限轮次后停止,未命中键保留字面量
	 */
	private void resolvePlaceholders() {
		for (int round = 0; round < 10; round++) {
			boolean changed = false;
			for (Map.Entry<String, String> entry : constantMap.entrySet()) {
				String value = entry.getValue();
				if (value == null || value.indexOf("${") < 0) {
					continue;
				}
				String resolved = replace(value);
				if (!resolved.equals(value)) {
					// entry.setValue非结构性变更,迭代中安全;replace()只读不动结构
					entry.setValue(resolved);
					changed = true;
				}
			}
			if (!changed) {
				return;
			}
		}
	}

	/**
	 * 将target中的${key}占位符替换为已加载属性值
	 */
	public String replace(String target) {
		if (constantMap.isEmpty() || target == null) {
			return target;
		}
		String result = target;
		if (StringUtil.matches(result, "\\$\\{") && StringUtil.matches(result, "\\}")) {
			for (Map.Entry<String, String> entry : constantMap.entrySet()) {
				result = StringUtil.replaceAllStr(result, "${" + entry.getKey() + "}", entry.getValue());
			}
		}
		return result;
	}

	/**
	 * 配置覆写(tasks等元素上的正式属性优先于property元素写法);null值忽略不覆写
	 */
	public void override(String key, String value) {
		if (key != null && value != null && !value.trim().isEmpty()) {
			constantMap.put(key, value.trim());
		}
	}

	/** 获取属性值,未配置时回退系统属性 */
	public String getKeyValue(String key) {
		if (StringUtil.isBlank(key)) {
			return key;
		}
		String value = constantMap.get(key);
		if (null == value) {
			value = System.getProperty(key);
		}
		return value;
	}

	/**
	 * 获取属性值,key支持${xxx}完整包裹形式;未配置返回null(而不是key本身),
	 * 调用方据此判断是否产出该内容——历史实现返回key,导致未配置project.version时
	 * 生成物头注释出现"@version project.version"字面量
	 */
	public String getPropertyValue(String key) {
		if (StringUtil.isBlank(key)) {
			return key;
		}
		String realKey = key.trim();
		if (realKey.startsWith("${") && realKey.endsWith("}")) {
			return getKeyValue(realKey.substring(2, realKey.length() - 1));
		}
		return getKeyValue(key);
	}

	/** 布尔属性,未配置返回默认值 */
	public boolean boolValue(String key, boolean defaultValue) {
		String value = getKeyValue(key);
		if (StringUtil.isBlank(value)) {
			return defaultValue;
		}
		return Boolean.parseBoolean(value);
	}

	/** 小数位最大长度(max.scale.length),未配置返回-1 */
	public int getMaxScale() {
		String maxScale = getKeyValue("max.scale.length");
		if (maxScale == null) {
			return -1;
		}
		try {
			return Integer.parseInt(maxScale.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					Msg.get("配置项max.scale.length值非法(应为整数):", "Invalid max.scale.length (expect integer): ")
							+ maxScale);
		}
	}

	/** 是否跳过主键约束信息提取(主要针对postgresql) */
	public boolean skipPkConstraint() {
		return boolValue("skip.primary.constraint", false);
	}

	/** 生成对象是否携带schema */
	public boolean includeSchema() {
		return boolValue("include.schema", false);
	}

	private void loadPropertyFile(String propertyFile) throws Exception {
		if (StringUtil.isNotBlank(propertyFile)) {
			File propFile;
			if (FileUtil.isRootPath(propertyFile)) {
				propFile = new File(propertyFile);
			} else {
				propFile = new File(FileUtil.skipPath(baseDir.toString(), propertyFile));
			}
			if (!propFile.exists()) {
				throw new Exception(Msg.get("参数文件:","Property file: ") + propertyFile + Msg.get("不存在,请确认!"," not found!"));
			}
			String lowerName = propertyFile.toLowerCase(java.util.Locale.ROOT);
			if (lowerName.endsWith("yml") || lowerName.endsWith("yaml")) {
				YamlUtil.loadYml(constantMap, propFile);
			} else {
				Properties props = new Properties();
				// Properties.load(InputStream)按规范以ISO-8859-1解码,UTF-8中文属性值会乱码;
				// 按UTF-8读取(unixxx转义在Reader模式下同样生效),与YAML路径的UTF-8行为对齐
				try (FileInputStream in = new FileInputStream(propFile);
						java.io.InputStreamReader reader = new java.io.InputStreamReader(in,
								java.nio.charset.StandardCharsets.UTF_8)) {
					props.load(reader);
				}
				for (String key : props.stringPropertyNames()) {
					constantMap.put(key, props.getProperty(key));
				}
			}
		}
	}
}
