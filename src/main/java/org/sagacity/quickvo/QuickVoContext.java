package org.sagacity.quickvo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.sagacity.quickvo.config.PropertyResolver;
import org.sagacity.quickvo.utils.LoggerUtil;

/**
 * quickvo单次执行的上下文:持有配置、属性解析器和运行期告警,替代原先散落在
 * Constants/DBHelper/TaskController中的全局static可变状态,支持同JVM内多模块互不干扰地重复执行。
 *
 * @author zhongxuchen
 */
public class QuickVoContext {
	/** 执行根路径 */
	private final Path baseDir;
	/**
	 * quickvo.xml配置文件路径;保持原始字符串形式——"classpath:xx"等前缀写法
	 * 无法转为Path(Windows上Path.of遇冒号直接抛InvalidPathException),
	 * 由XmlConfigParser.resolveConfigFile统一解析定位
	 */
	private final String configFile;
	/** ${}占位符属性解析 */
	private final PropertyResolver properties;
	/** 日志 */
	private final Logger logger;
	/** 运行期错误(如多schema导致字段重复) */
	private final List<String> errors = new ArrayList<>();

	public QuickVoContext(Path baseDir, String configFile) {
		this.baseDir = baseDir;
		this.configFile = configFile;
		this.properties = new PropertyResolver(baseDir);
		this.logger = LoggerUtil.getLogger(baseDir);
	}

	public Path getBaseDir() {
		return baseDir;
	}

	public String getConfigFile() {
		return configFile;
	}

	public PropertyResolver getProperties() {
		return properties;
	}

	public Logger getLogger() {
		return logger;
	}

	public List<String> getErrors() {
		return errors;
	}

	public void addError(String error) {
		errors.add(error);
	}

	public boolean hasErrors() {
		return !errors.isEmpty();
	}
}
