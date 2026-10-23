package org.sagacity.quickvo.render;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.sagacity.quickvo.model.QuickVO;
import org.sagacity.quickvo.utils.FileUtil;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import org.sagacity.quickvo.utils.Msg;

/**
 * freemarker渲染器:构造时一次性加载全部代码模板,api-doc等动态模板按唯一key临时注册。
 * 模板内容沿用FileUtil.inputStream2String按行读取(行尾统一为\r\n),保持历史生成物字节不变。
 *
 * @author zhongxuchen
 */
public final class TemplateRenderer {
	/** 代码生成模板 */
	public enum Template {
		/** dto(vo)类 */
		DTO("templates/dto.ftl"),
		/** dto抽象类 */
		DTO_ABSTRACT("templates/dto-abstract.ftl"),
		/** dto手工扩展父类 */
		DTO_PARENT("templates/dto-parent.ftl"),
		/** entity类 */
		ENTITY("templates/entity.ftl"),
		/** lombok entity类 */
		ENTITY_LOMBOK("templates/entity-lombok.ftl"),
		/** 抽象entity类 */
		ENTITY_ABSTRACT("templates/abstract-entity.ftl"),
		/** entity手工扩展父类 */
		ENTITY_PARENT("templates/parent-entity.ftl");

		private final String resource;

		Template(String resource) {
			this.resource = resource;
		}
	}

	private static final String MODEL_KEY = "quickVO";

	private final StringTemplateLoader loader = new StringTemplateLoader();
	private final Configuration cfg;
	private final Charset charset;
	private final AtomicLong adhocSeq = new AtomicLong();
	/** 模板内注释等静态文案的双语开关,构造时取值(晚于xml语言设置) */
	private final Map<String, Object> sharedModel = new HashMap<>(Map.of(
			"isZh", Msg.isChinese(),
			"generatorVersion", generatorVersion()));

	public TemplateRenderer(String encoding) {
		this.charset = encoding == null || encoding.trim().isEmpty()
				? StandardCharsets.UTF_8
				: Charset.forName(encoding.trim());
		this.cfg = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
		cfg.setDefaultEncoding(charset.name());
		cfg.setTemplateLoader(loader);
	}

	/**
	 * 注册内置代码模板(按配置编码读取classpath资源)
	 */
	public void loadBuiltinTemplates() {
		for (Template template : Template.values()) {
			// 内置模板资源恒为UTF-8,读取编码固定UTF-8;用户配置的encoding仅用于生成文件写出,
			// 历史上用配置编码解码模板,encoding=GBK时模板中文全部乱码进产物
			String content = FileUtil.inputStream2String(FileUtil.getResourceAsStream(template.resource),
					StandardCharsets.UTF_8.name());
			// 加载失败(inputStream2String吞异常返回"")会注册空模板,进而把已有源文件覆盖成0字节,必须显式失败
			if (content == null || content.trim().isEmpty()) {
				throw new IllegalStateException(Msg.get("内置模板加载失败:", "Failed to load built-in template: ")
						+ template.resource);
			}
			loader.putTemplate(template.name(), content);
		}
	}

	/**
	 * 渲染代码模板
	 */
	public String render(Template template, QuickVO quickVO) {
		Map<String, Object> root = new HashMap<>(sharedModel);
		root.put(MODEL_KEY, quickVO);
		return process(template.name(), root);
	}

	/**
	 * 渲染代码模板并直接写文件
	 */
	public void renderToFile(Template template, QuickVO quickVO, String distFile) {
		writeToFile(render(template, quickVO), distFile);
	}

	/**
	 * 渲染动态模板(api-doc的class/field模板等)
	 */
	public String renderAdhoc(String[] keys, Object[] values, String templateContent) {
		if (keys == null || values == null) {
			return null;
		}
		String name = "adhoc-" + adhocSeq.incrementAndGet();
		loader.putTemplate(name, templateContent);
		Map<String, Object> root = new HashMap<>(sharedModel);
		for (int i = 0; i < keys.length; i++) {
			root.put(keys[i], values[i]);
		}
		return process(name, root);
	}

	/** 字符串写盘(编码与模板一致),供增量合并结果落盘复用 */
	public void writeStringToFile(String content, String file) {
		writeToFile(content, file);
	}

	/** 渲染/写盘使用的编码 */
	public String getCharset() {
		return charset.name();
	}

	/**
	 * 当前插件版本:优先构建期过滤注入的quickvo-build.properties,
	 * 兜底读取jar清单(直接以classes运行时为空),避免@Generated头出现硬编码错误版本
	 */
	private static String generatorVersion() {
		try {
			var in = FileUtil.getResourceAsStream("quickvo-build.properties");
			if (in != null) {
				// try-with-resources确保props.load抛异常时流也关闭(历史缺陷:close不在finally,异常路径泄漏)
				try (in) {
					var props = new java.util.Properties();
					props.load(in);
					String version = props.getProperty("quickvo.version");
					if (version != null && !version.isBlank() && !version.startsWith("${")) {
						return version.trim();
					}
				}
			}
		} catch (Exception ignore) {
			// 兜底jar清单
		}
		String impl = TemplateRenderer.class.getPackage().getImplementationVersion();
		return impl == null ? "unknown" : impl;
	}

	private String process(String templateName, Map<String, Object> root) {
		try {
			freemarker.template.Template template = cfg.getTemplate(templateName, charset.name());
			StringWriter writer = new StringWriter();
			template.process(root, writer);
			writer.flush();
			return writer.toString();
		} catch (Exception e) {
			throw new IllegalStateException(Msg.get("模板渲染失败:","Template render failed: ") + templateName + "," + e.getMessage(), e);
		}
	}

	private void writeToFile(String content, String distFile) {
		try (FileOutputStream out = new FileOutputStream(distFile);
				Writer writer = new BufferedWriter(new OutputStreamWriter(out, charset))) {
			writer.write(content);
			writer.flush();
		} catch (Exception e) {
			throw new IllegalStateException(Msg.get("模板结果写文件失败:","Template write to file failed: ") + distFile + "," + e.getMessage(), e);
		}
	}
}
