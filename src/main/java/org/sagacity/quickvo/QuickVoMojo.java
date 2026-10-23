package org.sagacity.quickvo;

import java.nio.file.Path;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.sagacity.quickvo.utils.LoggerUtil;
import org.sagacity.quickvo.utils.Msg;

/**
 * sqltoy-quickvo maven插件入口:生成失败抛出MojoExecutionException令构建失败,
 * 不再吞异常假报成功(历史版本问题)。
 */
@Mojo(name = "quickvo")
public class QuickVoMojo extends AbstractMojo {

	@Parameter(defaultValue = "classpath:quickvo.xml")
	private String configFile;

	@Parameter(defaultValue = "${basedir}")
	private String baseDir;

	@Parameter(defaultValue = "UTF-8")
	private String loggerFileEncoding;

	@Override
	public void execute() throws MojoExecutionException {
		LoggerUtil.setFileEncoding(loggerFileEncoding);
		String version = getClass().getPackage().getImplementationVersion();
		getLog().info("\n\n=========    welcome use quickvo-maven-plugin-" + (version == null ? "dev" : version)
				+ "     ==========");
		Path baseDirPath = (baseDir == null || baseDir.trim().isEmpty())
				? Path.of(System.getProperty("user.dir"))
				: Path.of(baseDir.trim());
		QuickVoContext context = new QuickVoContext(baseDirPath,
				configFile == null ? "quickvo.xml" : configFile);
		try {
			new QuickVoRunner(context).run();
		} catch (Exception e) {
			throw new MojoExecutionException(Msg.get("quickvo代码生成失败:","quickvo code generation failed: ") + e.getMessage(), e);
		}
	}
}
