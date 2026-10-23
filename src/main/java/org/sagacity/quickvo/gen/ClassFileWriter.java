package org.sagacity.quickvo.gen;

import java.io.File;
import java.util.logging.Logger;

import org.sagacity.quickvo.model.ChangeModel;
import org.sagacity.quickvo.model.QuickVO;
import org.sagacity.quickvo.render.TemplateRenderer;
import org.sagacity.quickvo.render.TemplateRenderer.Template;
import org.sagacity.quickvo.utils.FileUtil;
import org.sagacity.quickvo.utils.StringUtil;
import org.sagacity.quickvo.utils.Msg;

/**
 * 生成类文件统一写出:按"序列号种类x写盘模式"组合收敛原先6个复制粘贴的generate*方法。
 * 写盘模式:
 * OVERWRITE_IF_CHANGED 全量重生成(规整化比对,内容无变化则跳过)
 * MERGE_FIELDS/MERGE_CONSTRUCTOR 只替换区域标记内的自动生成区,保护区外的用户代码
 * CREATE_ONLY 仅首次生成,之后永不覆盖(用户手工维护)
 *
 * @author zhongxuchen
 */
public class ClassFileWriter {
	/** 字段自动生成区标记 */
	public static final String FIELDS_BEGIN = "/*---begin-auto-generate-don't-update-this-area--*/";
	public static final String FIELDS_END = "/*---end-auto-generate-don't-update-this-area--*/";
	/** 构造器自动生成区标记 */
	public static final String CONSTRUCTOR_BEGIN = "/*---begin-constructor-area---don't-update-this-area--*/";
	public static final String CONSTRUCTOR_END = "/*---end-constructor-area---don't-update-this-area--*/";
	/** import自动管理区标记(区域内import随表结构变化增减,区域外用户import不动) */
	public static final String IMPORTS_BEGIN = "/*---begin-imports-area---auto-managed---*/";
	public static final String IMPORTS_END = "/*---end-imports-area---auto-managed---*/";
	/**
	 * 类级注解自动管理区标记(仅lombok单类实体模板使用):区内数据库生成的@Entity/@Indexes/@Partition/@MppTable
	 * 随表结构覆盖刷新,手工白名单注解(@Sharding/@Tenant等)由继承回填,区外的类声明/serialVersionUID/用户扩展代码保留
	 */
	public static final String CLASS_ANNOTATIONS_BEGIN = "/*---begin-class-annotations-area---auto-managed---*/";
	public static final String CLASS_ANNOTATIONS_END = "/*---end-class-annotations-area---auto-managed---*/";

	/** serialVersionUID的来源(包名+类名hash) */
	public enum SerialKind {
		ENTITY {
			@Override
			void apply(QuickVO quickVO, String serial) {
				quickVO.setEntitySerialUID(serial);
			}

			@Override
			String hashSource(QuickVO quickVO) {
				return quickVO.getEntityPackage().concat(".").concat(quickVO.getEntityName());
			}
		},
		ABSTRACT_ENTITY {
			@Override
			void apply(QuickVO quickVO, String serial) {
				quickVO.setAbstractEntitySerialUID(serial);
			}

			@Override
			String hashSource(QuickVO quickVO) {
				return quickVO.getEntityPackage().concat(".").concat(quickVO.getAbstractPath()).concat(".Abstract")
						.concat(quickVO.getEntityName());
			}
		},
		VO {
			@Override
			void apply(QuickVO quickVO, String serial) {
				quickVO.setVoSerialUID(serial);
			}

			@Override
			String hashSource(QuickVO quickVO) {
				return quickVO.getVoPackage().concat(".").concat(quickVO.getVoName());
			}
		},
		ABSTRACT_VO {
			@Override
			void apply(QuickVO quickVO, String serial) {
				quickVO.setVoAbstractSerialUID(serial);
			}

			@Override
			String hashSource(QuickVO quickVO) {
				return quickVO.getVoPackage().concat(".").concat(quickVO.getAbstractPath()).concat(".Abstract")
						.concat(quickVO.getVoName());
			}
		};

		abstract void apply(QuickVO quickVO, String serial);

		abstract String hashSource(QuickVO quickVO);
	}

	/** 写盘模式 */
	public enum WriteMode {
		/** 全量重生成,规整化比对无变化则跳过 */
		OVERWRITE_IF_CHANGED,
		/** 只更新字段自动生成区,保护区外用户代码 */
		MERGE_FIELDS,
		/** 只更新构造器自动生成区,保护区外用户代码 */
		MERGE_CONSTRUCTOR,
		/** 仅首次生成 */
		CREATE_ONLY
	}

	private final TemplateRenderer renderer;
	private final Logger logger;
	private int createdCount = 0;
	private int updatedCount = 0;
	private int unchangedCount = 0;

	public ClassFileWriter(TemplateRenderer renderer, Logger logger) {
		this.renderer = renderer;
		this.logger = logger;
	}

	/** 新建文件数(完成汇总用) */
	public int getCreatedCount() {
		return createdCount;
	}

	/** 本次发生更新的文件数 */
	public int getUpdatedCount() {
		return updatedCount;
	}

	/** 内容无变化的文件数 */
	public int getUnchangedCount() {
		return unchangedCount;
	}

	/**
	 * 渲染并写出一个生成类文件;按实际动作区分日志级别:新建/更新为info,无变化为fine降低噪音
	 */
	public void write(String file, Template template, QuickVO quickVO, SerialKind serialKind, WriteMode mode)
			throws java.io.IOException {
		// 根据包名和类名称产生hash值作为serialVersionUID(历史算法,不可变更)
		serialKind.apply(quickVO, Long.toString(StringUtil.hash(serialKind.hashSource(quickVO))));
		File generateFile = new File(file);
		if (!generateFile.exists()) {
			createdCount++;
			logger.info(Msg.get("新建文件:", "Created file: ") + file);
			renderer.renderToFile(template, quickVO, file);
			return;
		}
		switch (mode) {
		case CREATE_ONLY:
			// 已存在则不覆盖(用户手工扩展类只允许首生成)
			unchangedCount++;
			logger.fine(Msg.get("保留已存在文件(create-only):", "Keep existing file (create-only): ") + file);
			break;
		case OVERWRITE_IF_CHANGED:
			String oldFileContent = FileUtil.readAsString(generateFile, rendererCharset());
			String newFileContent = renderer.render(template, quickVO);
			if (!normalize(oldFileContent).equals(normalize(newFileContent))) {
				updatedCount++;
				logger.info(Msg.get("表结构变化,重写文件:", "Schema changed, rewrote file: ") + file);
				renderer.writeStringToFile(newFileContent, file);
			} else {
				unchangedCount++;
				logger.fine(Msg.get("内容无变化,跳过文件:", "Unchanged, skipped file: ") + file);
			}
			break;
		case MERGE_FIELDS:
		case MERGE_CONSTRUCTOR:
			boolean isFields = mode == WriteMode.MERGE_FIELDS;
			String begin = isFields ? FIELDS_BEGIN : CONSTRUCTOR_BEGIN;
			String end = isFields ? FIELDS_END : CONSTRUCTOR_END;
			String rendered = renderer.render(template, quickVO);
			String existing = FileUtil.readAsString(generateFile, rendererCharset());
			// 1) 同步import区(表结构变化引起的import增减)
			ChangeModel importResult = mergeImports(rendered, existing, file);
			String merged = importResult.isModified() ? importResult.getResult() : existing;
			// 2) 同步类级注解区(仅lombok单类实体模板有此标记):数据库生成的@Entity/@Indexes/@Partition等覆盖旧值,
			//    手工白名单注解已由继承回填进rendered;DTO等无此标记则跳过(不告警)
			ChangeModel classAnnResult = mergeClassAnnotations(rendered, merged, file);
			merged = classAnnResult.isModified() ? classAnnResult.getResult() : merged;
			// 3) 合并字段/构造器自动生成区
			ChangeModel changeResult = mergeRegion(rendered, merged, begin, end, file);
			if (importResult.isModified() || classAnnResult.isModified() || changeResult.isModified()) {
				updatedCount++;
				logger.info(Msg.get("表结构变化,合并更新自动生成区:", "Schema changed, merged auto-generated region of file: ")
						+ file);
				renderer.writeStringToFile(changeResult.isModified() ? changeResult.getResult() : merged, file);
			} else {
				unchangedCount++;
				logger.fine(Msg.get("内容无变化,跳过文件:", "Unchanged, skipped file: ") + file);
			}
			break;
		default:
			break;
		}
	}

	private String rendererCharset() {
		return renderer.getCharset();
	}

	/** 剔除所有回车换行、tab和空白后的规整化内容(用于无变化判断) */
	private String normalize(String content) {
		String result = StringUtil.clearMistyChars(content, "");
		return StringUtil.replaceAllStr(result, " ", "");
	}

	/**
	 * 同步import区:新内容带import标记时整体替换标记区(增减都生效,标记外用户import保留);
	 * 旧文件无标记(历史版本生成)时退化为增量模式——仅追加缺失的import,不动既有内容
	 */
	ChangeModel mergeImports(String newContent, String oldContent, String file) {
		ChangeModel result = new ChangeModel();
		int newBegin = newContent.indexOf(IMPORTS_BEGIN);
		int newEnd = newContent.indexOf(IMPORTS_END);
		if (newBegin == -1 || newEnd == -1) {
			return result;
		}
		String newImports = newContent.substring(newBegin, newEnd + IMPORTS_END.length());
		int oldBegin = oldContent.indexOf(IMPORTS_BEGIN);
		int oldEnd = oldContent.indexOf(IMPORTS_END);
		// end标记须出现在begin之后,标记被手工挪位/错序时降级走遗留追加路径,而非substring越界中断任务
		if (oldBegin != -1 && oldEnd > oldBegin) {
			String oldImports = oldContent.substring(oldBegin, oldEnd + IMPORTS_END.length());
			if (normalize(oldImports).equals(normalize(newImports))) {
				return result;
			}
			result.setResult(oldContent.substring(0, oldBegin) + newImports
					+ oldContent.substring(oldEnd + IMPORTS_END.length()));
			result.setModified(true);
			return result;
		}
		// 历史文件兼容:提取新内容import行,追加旧文件中缺失的(插在最后一条import之后)
		java.util.List<String> missing = new java.util.ArrayList<>();
		for (String line : newImports.split("\\r\\n|\\n")) {
			String trimmed = line.trim();
			if (trimmed.startsWith("import ") && !oldContent.contains(trimmed)) {
				missing.add(trimmed);
			}
		}
		if (missing.isEmpty()) {
			return result;
		}
		String insertText = String.join("\r\n", missing);
		int lastImportEnd = -1;
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?m)^import [^;]+;\\s*$")
				.matcher(oldContent);
		while (matcher.find()) {
			lastImportEnd = matcher.end();
		}
		if (lastImportEnd == -1) {
			// 无import则放在package行之后
			java.util.regex.Matcher packageMatcher = java.util.regex.Pattern.compile("(?m)^package [^;]+;\\s*$")
					.matcher(oldContent);
			if (packageMatcher.find()) {
				lastImportEnd = packageMatcher.end();
			} else {
				return result;
			}
		}
		result.setResult(oldContent.substring(0, lastImportEnd) + "\r\n" + insertText + "\r\n"
				+ oldContent.substring(lastImportEnd));
		result.setModified(true);
		logger.info(Msg.get("文件:","File: ") + file + Msg.get(" 补充缺失import:"," added missing imports: ") + missing);
		return result;
	}

	/** 类声明行(public [abstract|final|static] class Name):老文件无类头标记时定位注解块下界 */
	private static final java.util.regex.Pattern CLASS_DECL = java.util.regex.Pattern
			.compile("(?m)^[ \\t]*public\\s+(?:abstract\\s+|final\\s+|static\\s+)*class\\s+\\w+");

	/**
	 * 合并类级注解自动管理区,三种情形:
	 * 1) 新内容无该标记(如DTO/parent模板)→跳过不告警;
	 * 2) 旧文件已有标记→按区域替换(mergeRegion);
	 * 3) 旧文件无标记(老项目升级到引入类头标记区的新版本,这是升级后的常见情形)→按结构定位旧类级注解块
	 *    (类javadoc的星号斜杠结束 到 class声明之间),整体替换为新的带标记注解块,一次性迁移到新格式。
	 *    手工白名单注解(@Sharding等)已由继承回填进新块故不丢,数据库生成的@Entity/@Indexes/@Partition被刷新;
	 *    锚点定位失败则保留原文件并告警(不中断、不损坏)
	 */
	ChangeModel mergeClassAnnotations(String newContent, String oldContent, String file) {
		int newBegin = newContent.indexOf(CLASS_ANNOTATIONS_BEGIN);
		if (newBegin == -1) {
			return new ChangeModel();
		}
		if (oldContent.indexOf(CLASS_ANNOTATIONS_BEGIN) != -1) {
			return mergeRegion(newContent, oldContent, CLASS_ANNOTATIONS_BEGIN, CLASS_ANNOTATIONS_END, file);
		}
		return migrateLegacyClassAnnotations(newContent, oldContent, newBegin, file);
	}

	/**
	 * 老格式(无类头标记区)迁移:以"类javadoc结束"与"class声明"为锚点,把两者之间的旧类级注解块
	 * 换成新渲染的带标记注解块。保留旧文件类javadoc(用户可能改过),仅刷新注解并补上标记,迁移后即走按区合并
	 */
	private ChangeModel migrateLegacyClassAnnotations(String newContent, String oldContent, int newBegin, String file) {
		ChangeModel result = new ChangeModel();
		java.util.regex.Matcher newClassM = CLASS_DECL.matcher(newContent);
		java.util.regex.Matcher oldClassM = CLASS_DECL.matcher(oldContent);
		if (!newClassM.find() || !oldClassM.find()) {
			logger.warning(Msg.get("文件:", "File: ") + file + Msg.get(
					" 无法定位class声明,类头注解区未合并,保留原文件(如需刷新类头注解请删除该文件重新生成)!",
					" cannot locate class declaration; class-annotation area not merged, original preserved (delete file to regenerate)!"));
			return result;
		}
		int newJavadocEnd = newContent.lastIndexOf("*/", newBegin);
		int oldJavadocEnd = oldContent.lastIndexOf("*/", oldClassM.start());
		if (newJavadocEnd == -1 || oldJavadocEnd == -1) {
			logger.warning(Msg.get("文件:", "File: ") + file + Msg.get(
					" 无法定位类javadoc结束,类头注解区未合并,保留原文件(如需刷新类头注解请删除该文件重新生成)!",
					" cannot locate class javadoc end; class-annotation area not merged, original preserved (delete file to regenerate)!"));
			return result;
		}
		// 新块:类javadoc结束→class声明前(含begin/end标记及尾随换行);替换旧文件同区间(旧注解块,无标记)
		String newSegment = newContent.substring(newJavadocEnd + 2, newClassM.start());
		result.setResult(oldContent.substring(0, oldJavadocEnd + 2) + newSegment + oldContent.substring(oldClassM.start()));
		result.setModified(true);
		logger.info(Msg.get("文件:", "File: ") + file + Msg.get(
				" 为老格式(无类头标记区),已迁移类头注解区:数据库生成注解刷新,手工注解经继承保留",
				" is legacy format (no class-annotation markers); migrated: DB-generated annotations refreshed, hand-added preserved via carry-over"));
		return result;
	}

	/**
	 * 将新内容的自动生成区替换进旧文件;区域标记缺失时保留旧文件并告警
	 * (视图类parent模板天然无构造器区,历史版本此处抛异常会中断整个任务)
	 */
	ChangeModel mergeRegion(String newContent, String oldContent, String begin, String end, String file) {
		ChangeModel result = new ChangeModel();
		int oldBegin = oldContent.indexOf(begin);
		int oldEnd = oldContent.indexOf(end, oldBegin > 0 ? oldBegin : 0);
		int newBegin = newContent.indexOf(begin);
		int newEnd = newContent.indexOf(end, newBegin > 0 ? newBegin : 0);
		if (newBegin == -1 || newEnd == -1) {
			// 新内容无此区域(如视图parent天然无主键构造器区):预期形态,保留用户文件;
			// 新旧均无标记时静默——视图每次重生成都告警会误导用户"文件需要删除重建"
			if (oldBegin != -1 && oldEnd != -1) {
				// 旧文件有而新模板无:该区域不再由quickvo管理,显式告知一次
				logger.info(Msg.get("文件:","File: ") + file + Msg.get(" 新生成内容无区域标记:"," newly generated content lacks region marker: ") + begin + Msg.get(",保持原文件不变!",", keeping original file!"));
			}
			return result;
		}
		if (oldBegin == -1 || oldEnd == -1) {
			logger.warning(Msg.get("文件:","File: ") + file + Msg.get(" 内容中不存在区域标记:"," region marker not found in content: ") + begin + Msg.get(",已保留原文件内容(如需重新生成请删除该文件)!",", original content preserved (delete file to regenerate)!"));
			return result;
		}
		String newRegion = newContent.substring(newBegin, newEnd + end.length());
		String oldRegion = oldContent.substring(oldBegin, oldEnd + end.length());
		// 内容相同,表示表结构没有发生变化
		if (newRegion.replaceAll("\\s|\t|\n|\r", "").equals(oldRegion.replaceAll("\\s|\t|\n|\r", ""))) {
			return result;
		}
		result.setResult(
				oldContent.substring(0, oldBegin).concat(newRegion).concat(oldContent.substring(oldEnd + end.length())));
		result.setModified(true);
		return result;
	}
}
