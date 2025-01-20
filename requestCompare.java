package com.qihoo.secbrain.test;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Created with IntelliJ IDEA.
 * Description:
 * User: zhangdengfeng
 * Date: 2025/1/20
 *
 * @author zhangdengfeng
 */
public class requestCompare {

	private static final double EPSILON = 1e-9;

	// 测试方法
	public static void main(String[] args) {

		// 第一步 接口返回体处理
		// 假设返回体=
		// {
		// 		"success": true,
		// 		"code": 2000,
		// 		"message": "成功",
		// 		"data": {
		// 			"total": 17,
		// 				"rows": [{
		// 					"id": "1",
		// 					"name": "刘德华",
		// 					"intro": "毕业于师范大学数学系,执教数学6年有余"
		// 				}]
		// 		}
		// }
		String testStr="{\"success\":true,\"code\":2000,\"message\":\"成功\",\"data\":{\"total\":17,\"rows\":[{\"id\":\"1\",\"name\":\"刘德华\",\"intro\":\"毕业于师范大学数学系,执教数学6年有余\"}]}}";

		// 标准化解析 数据flatMap
		Map<String, Object> stringObjectMap = jsonCollapse(JSONObject.parseObject(testStr), "");

		// 数据flatMap后格式为
		// {
		// 	"code": 2000,
		// 	"data.rows.0.id": "1",
		// 	"success": true,
		// 	"data.total": 17,
		// 	"data.rows": [{
		// 		"id": "1",
		// 		"name": "刘德华",
		// 		"intro": "毕业于师范大学数学系,执教数学6年有余"
		// 	}],
		// 	"message": "成功",
		// 	"data.rows.0.name": "刘德华",
		// 	"data.rows.0.intro": "毕业于师范大学数学系,执教数学6年有余"
		// }
		System.out.println(new JSONObject(stringObjectMap));


		// 第二步
		// 定义配置文件  判断接口返回体为true的逻辑
		// 假设为 [ {"key":"success",  "value":true, "operator":"="} , {"key":"data.total",  "value":1, "operator":">"} ]
		// 满足数组内 每个条件 则为ture

		// 模拟读取配置文件
		String target="[ {\"key\":\"success\",  \"value\":true, \"operator\":\"=\"} , {\"key\":\"data.total\",  \"value\":17, \"operator\":\"=\"} ]";

		System.out.println(logicCheck(stringObjectMap,target));

	}


	// 对外暴漏方法 ****************
	public static boolean Check(String requestTxt,String targetTxt){
		if("".equals(requestTxt)){
			return false;
		}

		// 解析
		Map<String, Object> stringObjectMap = jsonCollapse(JSONObject.parseObject(requestTxt), "");

		return logicCheck(stringObjectMap,targetTxt);

	}


	// 逻辑判断hashmap的值 是否符合期望配置 当前版本v1 满足基本需求--------------------------------
	private static boolean logicCheck(Map<String, Object> stringObjectMap, String target){
		JSONArray targetJsonObject = JSONArray.parse(target);
		for (Object o : targetJsonObject) {
			JSONObject jsonObject = (JSONObject) o;

			String key = jsonObject.getString("key");
			String realValue=stringObjectMap.getOrDefault(key,"").toString();
			String targetValue = jsonObject.getString("value");
			String operator = jsonObject.getString("operator");

			if (!stringObjectMap.containsKey(key)){
				// 如果接口返回体无期望key  则说明接口返回体不符合期望
				System.out.println("如果接口返回体无期望key  则说明接口返回体不符合期望");
				return false;
			}

			// 如果 real 大于 target，返回 1。
			// 如果 real 小于 target，返回 -1。
			int compared = compareValues(realValue, targetValue);

			if ("=".equals(operator)){
				if (compared!=0){
					return false;
				}
			}

			else if ("!=".equals(operator)){
				if (compared==0){
					return false;
				}
			}

			else if  (">".equals(operator)){
				if (compared!=1){
					return false;
				}
			}

			else if  (">=".equals(operator)){
				if (compared<=0){
					return false;
				}
			}

			else if  ("<".equals(operator)){
				if (compared!=-1){
					return false;
				}
			}
			else if  ("<=".equals(operator)){
				if (compared>=0){
					return false;
				}
			}

		}

		return true;
	}


	// logicCheck引用的值比较方式
	// 情况1 比较2个值 = > <  -------------------------------------------
	private static int compareValues(String realValue, String targetValue) {

		try {
			// 尝试将字符串转换为双精度浮点数
			double realDouble = Double.parseDouble(realValue);
			double targetDouble = Double.parseDouble(targetValue);
			if (Math.abs(realDouble - targetDouble) < EPSILON) {
				return 0;
			} else if (realDouble > targetDouble) {
				return 1;
			} else {
				return -1;
			}
		} catch (NumberFormatException e) {
			// 无法转换为数字，作为字符串比较
			return realValue.compareTo(targetValue);
		}

	}


	// 其他情况?
	// 比如like?   startWith?  contains?  endWith? 等等  可以在这补充
	// 可以补充  甚至整体logicCheck是否有其他方式实现 后续可以改进此部分


	// core
	// 数据标准化解析类 获取flatMap的结果 -----------------------------
	private static Map<String, Object> jsonCollapse(Map<String, Object> dictObj, String key) {

		// 最终输出的结果map
		Map<String, Object> collect = new HashMap<>();

		// 遍历待处理map的每个值
		for (Map.Entry<String, Object> entry : dictObj.entrySet()) {

			String itemKey = entry.getKey();
			Object itemValue = entry.getValue();

			// 组装当前key
			String mergeKey = key.isEmpty() ? itemKey : key + "." + itemKey;

			if (itemValue instanceof Map) {
				collect.putAll(jsonCollapse((Map<String, Object>) itemValue, mergeKey));
			} else if (itemValue instanceof List) {
				collect.putAll(handleList((List<Object>) itemValue, mergeKey));
			} else {
				collect.put(mergeKey, itemValue);
			}
		}

		return collect;
	}

	private static Map<String, Object> handleList(List<Object> listObj, String passedKey) {
		Map<String, Object> tCollect = new HashMap<>();

		for (int index = 0; index < listObj.size(); index++) {

			Object itemObj = listObj.get(index);
			String newKey = passedKey + "." + index;

			if (itemObj instanceof Map) {
				tCollect.putAll(jsonCollapse((Map<String, Object>) itemObj, newKey));
			} else if (itemObj instanceof List) {
				tCollect.putAll(handleList((List<Object>) itemObj, newKey));
			} else {
				tCollect.put(newKey, itemObj);
			}
		}

		// 中间值是否保存 如果不保存 则该行不需要
		tCollect.put(passedKey, listObj);

		return tCollect;
	}

}
