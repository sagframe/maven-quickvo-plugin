package org.sagacity.quickvo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * 空任务(include未匹配到任何表)默认令构建失败单测:零产物却BUILD SUCCESS是最难发现的配置错误。
 * 用H2内存库(无表)复现,不依赖外部数据库;放行方式为tasks属性 fail-on-empty="false"
 * (XSD可提示)或历史property写法,前者优先。
 */
public class QuickVoRunnerEmptyTaskTest {

	// 不用@TempDir:QuickVoContext会在目录内打开quickvo.log的FileHandler,Windows下文件被锁导致清理失败
	private static Path newBaseDir() throws Exception {
		Path dir = Path.of(System.getProperty("user.dir"), "target", "empty-task-" + System.nanoTime());
		Files.createDirectories(dir);
		return dir;
	}

	private static Path config(Path baseDir, String extraProperty, String tasksAttributes) throws Exception {
		Path config = baseDir.resolve("quickvo.xml");
		Files.writeString(config, """
				<quickvo xmlns="http://www.sagframe.com/schema/quickvo">
				<datasource name="demo" driver="org.h2.Driver"
					url="jdbc:h2:mem:empty_probe_%d" username="sa" password=""/>
				%s
				<tasks dist="gen" encoding="UTF-8"%s>
				<task active="true" include="^NO_SUCH_TABLE$" datasource="demo">
				<entity package="com.empty.entity" name="#{subName}"/>
				</task>
				</tasks>
				</quickvo>
				""".formatted(System.nanoTime(), extraProperty, tasksAttributes));
		return config;
	}

	private static QuickVoException run(Path baseDir, String extraProperty, String tasksAttributes) throws Exception {
		QuickVoContext context = new QuickVoContext(baseDir, config(baseDir, extraProperty, tasksAttributes).toString());
		return assertThrows(QuickVoException.class, () -> new QuickVoRunner(context).run());
	}

	@Test
	public void emptyTaskShouldFailBuildByDefault() throws Exception {
		QuickVoException ex = run(newBaseDir(), "", "");
		// 错误信息须能定位到具体任务(include正则)并提示放行方式
		assertTrue(ex.getMessage().contains("NO_SUCH_TABLE"), ex.getMessage());
		assertTrue(ex.getMessage().contains("fail-on-empty"), ex.getMessage());
	}

	@Test
	public void emptyTaskShouldPassWithTasksAttribute() throws Exception {
		// 推荐写法:<tasks fail-on-empty="false">(XSD可校验/可补全)
		Path baseDir = newBaseDir();
		QuickVoContext context = new QuickVoContext(baseDir,
				config(baseDir, "", " fail-on-empty=\"false\"").toString());
		assertDoesNotThrow(() -> new QuickVoRunner(context).run());
	}

	@Test
	public void emptyTaskShouldPassWithLegacyProperty() throws Exception {
		// 兼容写法:<property name="fail-on-empty" value="false"/>
		Path baseDir = newBaseDir();
		QuickVoContext context = new QuickVoContext(baseDir,
				config(baseDir, "<property name=\"fail-on-empty\" value=\"false\"/>", "").toString());
		assertDoesNotThrow(() -> new QuickVoRunner(context).run());
	}

	@Test
	public void tasksAttributeShouldOverrideProperty() throws Exception {
		// tasks属性优先于property元素(与include-partition/include-mpp-table一致)
		run(newBaseDir(), "<property name=\"fail-on-empty\" value=\"false\"/>", " fail-on-empty=\"true\"");
	}
}
