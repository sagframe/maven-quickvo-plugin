package org.sagacity.quickvo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * ${}占位符解析单测:属性值展开与定义顺序无关(前向引用/嵌套链均正确解析),
 * 未命中的占位符保留字面量(历史实现在put时即解析,前向引用永不展开)
 */
public class PropertyResolverTest {

	@Test
	public void forwardReferenceShouldResolve() throws Exception {
		PropertyResolver resolver = new PropertyResolver(null);
		resolver.loadProperties(props("project.name", "${project.prefix}-demo", "project.prefix", "quickvo"));
		assertEquals("quickvo-demo", resolver.replace("${project.name}"));
	}

	@Test
	public void chainedPlaceholdersShouldResolve() throws Exception {
		PropertyResolver resolver = new PropertyResolver(null);
		resolver.loadProperties(props("a", "${b}", "b", "${c}", "c", "x"));
		assertEquals("x", resolver.replace("${a}"));
	}

	@Test
	public void unknownPlaceholderShouldKeepLiteral() throws Exception {
		PropertyResolver resolver = new PropertyResolver(null);
		resolver.loadProperties(props("a", "1"));
		assertEquals("${undefined}", resolver.replace("${undefined}"));
		assertEquals("1/${undefined}", resolver.replace("${a}/${undefined}"));
	}

	@Test
	public void circularReferenceShouldNotLoopForever() throws Exception {
		PropertyResolver resolver = new PropertyResolver(null);
		resolver.loadProperties(props("a", "${b}", "b", "${a}"));
		// 循环引用有限轮次后停止,保留字面量
		assertEquals("${a}", resolver.replace("${b}"));
	}

	@Test
	public void globaIdentityShouldStillReplace() throws Exception {
		PropertyResolver resolver = new PropertyResolver(null);
		resolver.loadProperties(props("instance", "node-##{globa.identity}"));
		String guid = System.getProperty("globa.identity");
		assertEquals("node-" + (guid == null ? "" : guid), resolver.replace("${instance}"));
	}

	@Test
	public void getPropertyValueShouldReturnNullWhenAbsent() throws Exception {
		// 未配置时返回null:生成物头注释据此省略@project/@version行,
		// 历史实现返回key本身,产物里留下"@version project.version"字面量
		PropertyResolver resolver = new PropertyResolver(null);
		resolver.loadProperties(props("project.version", "1.0.0"));
		assertEquals("1.0.0", resolver.getPropertyValue("project.version"));
		assertEquals("1.0.0", resolver.getPropertyValue("${project.version}"));
		assertNull(resolver.getPropertyValue("project.name"));
		assertNull(resolver.getPropertyValue("${project.name}"));
	}

	@Test
	public void getPropertyValueShouldFallBackToSystemProperty() throws Exception {
		PropertyResolver resolver = new PropertyResolver(null);
		resolver.loadProperties(props("a", "1"));
		System.setProperty("quickvo.test.sysprop", "from-system");
		try {
			assertEquals("from-system", resolver.getPropertyValue("quickvo.test.sysprop"));
			// 已配置的key优先于系统属性
			assertEquals("1", resolver.getPropertyValue("a"));
		} finally {
			System.clearProperty("quickvo.test.sysprop");
		}
	}

	/** 内存DOM构造property元素列表 */
	private static NodeList props(String... nameValuePairs) throws Exception {
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element root = doc.createElement("properties");
		doc.appendChild(root);
		for (int i = 0; i < nameValuePairs.length; i += 2) {
			Element property = doc.createElement("property");
			property.setAttribute("name", nameValuePairs[i]);
			property.setAttribute("value", nameValuePairs[i + 1]);
			root.appendChild(property);
		}
		return root.getElementsByTagName("property");
	}
}
