/**
 * Copyright 2018 mall开源
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.mall.utils;

import com.mall.entity.ColumnEntity;
import com.mall.entity.TableEntity;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成器   工具类
 * 
 * @author chenshun
 * @email sunlightcs@gmail.com
 */
public class GenUtils {

	public static List<String> getTemplates(){
		List<String> templates = new ArrayList<>();
		templates.add("template/Entity.java.vm");
		templates.add("template/Dao.java.vm");
		templates.add("template/Dao.xml.vm");
		templates.add("template/Service.java.vm");
		templates.add("template/ServiceImpl.java.vm");
		templates.add("template/Controller.java.vm");
		templates.add("template/Excel.java.vm");
		templates.add("template/Redis.java.vm");
		templates.add("template/DTO.java.vm");
		templates.add("template/index.vue.vm");
		templates.add("template/add-or-update.vue.vm");
		return templates;
	}
	
	/**
	 * 生成代码
	 */
	public static void generatorCode(Map<String, String> table,
			List<Map<String, Object>> columns, ZipOutputStream zip){
		//配置信息
		Configuration config = getConfig();
		boolean hasBigDecimal = false;
		//表信息
		TableEntity tableEntity = new TableEntity();
		tableEntity.setTableName(table.get("tableName"));
		tableEntity.setComments(table.get("tableComment"));
		//表名转换成Java类名
		String className = tableToJava(tableEntity.getTableName(), config.getString("tablePrefix"));
		tableEntity.setClassName(className);
		tableEntity.setClassname(StringUtils.uncapitalize(className));
		
		//列信息
		List<ColumnEntity> columsList = new ArrayList<>();
		for(Map<String, Object> column : columns){
			ColumnEntity columnEntity = new ColumnEntity();
			columnEntity.setColumnName((String)column.get("columnName"));
			columnEntity.setDataType((String)column.get("dataType"));
			BigInteger len = (BigInteger) column.get("length");
			columnEntity.setLength(len == null ? null : len.longValue());
			columnEntity.setNullable("YES".equals(column.get("nullable")));
			columnEntity.setDefaultValue(column.get("defaultValue") != null);
			columnEntity.setComments((String)column.get("columnComment"));
			columnEntity.setExtra((String)column.get("extra"));
			columnEntity.setIsPic(isPic(columnEntity));
			
			//列名转换成Java属性名
			String attrName = columnToJava(columnEntity.getColumnName());
			columnEntity.setAttrName(attrName);
			columnEntity.setAttrname(StringUtils.uncapitalize(attrName));
			
			//列的数据类型，转换成Java类型
			String attrType = config.getString(columnEntity.getDataType(), "unknowType");
			columnEntity.setAttrType(attrType);
			if (!hasBigDecimal && attrType.equals("BigDecimal" )) {
				hasBigDecimal = true;
			}
			//是否主键
			if("PRI".equalsIgnoreCase((String)column.get("columnKey")) && tableEntity.getPk() == null){
				tableEntity.setPk(columnEntity);
			}
			
			columsList.add(columnEntity);
		}
		tableEntity.setColumns(columsList);
		
		//没主键，则第一个字段为主键
		if(tableEntity.getPk() == null){
			tableEntity.setPk(tableEntity.getColumns().get(0));
		}
		
		//设置velocity资源加载器
		Properties prop = new Properties();  
		prop.put("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");  
		Velocity.init(prop);

		String main = config.getString("main" );
		main = StringUtils.isBlank(main) ? config.getString("package" ) : main;
		
		//封装模板数据
		Map<String, Object> map = new HashMap<>();
		map.put("tableName", tableEntity.getTableName());
		map.put("comments", tableEntity.getComments());
		map.put("pk", tableEntity.getPk());
		map.put("className", tableEntity.getClassName());
		map.put("classname", tableEntity.getClassname());
		map.put("pathName", tableEntity.getClassname().toLowerCase());
		map.put("columns", tableEntity.getColumns());
		map.put("hasBigDecimal", hasBigDecimal);
		map.put("main", main);

		String moduleName = config.getString("moduleName" );
		if(StringUtils.isNotBlank(moduleName)){
			map.put("package", config.getString("package" ) + "." + moduleName);
		}else {
			map.put("package", config.getString("package" ));
		}

		map.put("moduleName", config.getString("moduleName" ));
		map.put("author", config.getString("author"));
		map.put("version", config.getString("version"));
		map.put("email", config.getString("email"));
		map.put("datetime", DateUtils.format(new Date(), DateUtils.DATE_TIME_PATTERN));
		map.put("date", DateUtils.format(new Date(), DateUtils.DATE_PATTERN));
        VelocityContext context = new VelocityContext(map);


        
        //获取模板列表
		List<String> templates = getTemplates();
		for(String template : templates){
			//渲染模板
			StringWriter sw = new StringWriter();
			Template tpl = Velocity.getTemplate(template, "UTF-8");
			tpl.merge(context, sw);
			
			try {
				//添加到zip
				zip.putNextEntry(new ZipEntry(getFileName(template, tableEntity.getClassName(), config.getString("package"), config.getString("moduleName"))));
				IOUtils.write(sw.toString(), zip, "UTF-8");
				IOUtils.closeQuietly(sw);
				zip.closeEntry();
			} catch (IOException e) {
				throw new RRException("渲染模板失败，表名：" + tableEntity.getTableName(), e);
			}
		}
	}

	private static Boolean isPic(ColumnEntity columnEntity) {
		return columnEntity.getColumnName().endsWith("pic") || columnEntity.getColumnName().endsWith("pics")
				|| columnEntity.getColumnName().endsWith("logo") || columnEntity.getComments().contains("图");
	}


	/**
	 * 列名转换成Java属性名
	 */
	public static String columnToJava(String columnName) {
		return WordUtils.capitalizeFully(columnName, new char[]{'_'}).replace("_", "");
	}
	
	/**
	 * 表名转换成Java类名
	 */
	public static String tableToJava(String tableName, String tablePrefix) {
		if(StringUtils.isNotBlank(tablePrefix)){
			tableName = tableName.replaceFirst(tablePrefix, "");
		}
		return columnToJava(tableName);
	}
	
	/**
	 * 获取配置信息
	 */
	public static Configuration getConfig(){
		try {
			return new PropertiesConfiguration("generator.properties");
		} catch (ConfigurationException e) {
			throw new RRException("获取配置文件失败，", e);
		}
	}

	/**
	 * 获取文件名
	 */
	public static String getFileName(String template, String className, String packageName, String moduleName) {
		String packagePath = "main" + File.separator + "java" + File.separator;
		if (StringUtils.isNotBlank(packageName)) {
			packagePath += packageName.replace(".", File.separator) + File.separator + moduleName + File.separator;
		}

		if (template.contains("Entity.java.vm" )) {
			return packagePath + "entity" + File.separator + className + "Entity.java";
		}

		if (template.contains("Excel.java.vm" )) {
			return packagePath + "excel" + File.separator + className + "Excel.java";
		}

		if (template.contains("Dao.java.vm" )) {
			return packagePath + "dao" + File.separator + className + "Dao.java";
		}

		if (template.contains("Service.java.vm" )) {
			return packagePath + "service" + File.separator + className + "Service.java";
		}

		if (template.contains("ServiceImpl.java.vm" )) {
			return packagePath + "service" + File.separator + "impl" + File.separator + className + "ServiceImpl.java";
		}

		if (template.contains("Controller.java.vm" )) {
			return packagePath + "controller" + File.separator + className + "Controller.java";
		}

		if (template.contains("Redis.java.vm" )) {
			return packagePath + "redis" + File.separator + className + "Redis.java";
		}

		if (template.contains("DTO.java.vm" )) {
			return moduleName + File.separator + "dto" + File.separator + className + "DTO.java";
		}

		if (template.contains("Dao.xml.vm" )) {
			return "main" + File.separator + "resources" + File.separator + "mapper" + File.separator + className + "Dao.xml";
		}

		if (template.contains("index.vue.vm" )) {
			return "vue" + File.separator + "views" + File.separator + "modules" +
					File.separator + moduleName + File.separator + className.toLowerCase() + ".vue";
		}

		if (template.contains("add-or-update.vue.vm" )) {
			return "vue" + File.separator + "views" + File.separator + "modules" +
					File.separator + moduleName + File.separator + className.toLowerCase() + "-add-or-update.vue";
		}
		return null;
	}
}
