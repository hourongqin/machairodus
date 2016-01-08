/**
 * Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 			http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.machairodus.balancer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.machairodus.mappers.domain.NodeConfig;
import org.nanoframework.commons.support.logging.Logger;
import org.nanoframework.commons.support.logging.LoggerFactory;
import org.nanoframework.commons.util.CollectionUtils;

import com.google.common.collect.Maps;

public class CommandTest {
	private Logger LOG = LoggerFactory.getLogger(CommandTest.class);
	
	@Test
	public void createTest() {
		try {
			Map<String, Object> params = Maps.newHashMap();
			NodeConfig node = new NodeConfig();
			node.setId(1L);
			node.setName("JMX-Test");
			node.setPort(8180);
			node.setJmxPort(10180);
			node.setServerName("VM");
			node.setServerAddress("192.168.180.137");
			params.put("nodeConfig", node);
			post("http://localhost:7200/balancer/cmd/create?", params);
		} catch(Throwable e) {
			LOG.error(e.getMessage(), e);
		}
	}
	
	
	public void destroyTest() {
		try {
			post("http://localhost:7200/balancer/cmd/destroy/1", null);
		} catch(Throwable e) {
			LOG.error(e.getMessage(), e);
		}
	}
	
	public void post(String uri, Map<String, Object> params) {
		RequestConfig requestConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD_STRICT).build();
		CloseableHttpClient httpClient = HttpClients.createDefault();  
        HttpPost httpPost = new HttpPost(uri);  
        CloseableHttpResponse response = null;  
  
        try {  
            httpPost.setConfig(requestConfig);  
            if(!CollectionUtils.isEmpty(params)) {
	            List<NameValuePair> pairList = new ArrayList<>(params.size());  
	            for (Map.Entry<String, Object> entry : params.entrySet()) {  
	                NameValuePair pair = new BasicNameValuePair(entry.getKey(), entry.getValue().toString());  
	                pairList.add(pair);  
	            }  
	            httpPost.setEntity(new UrlEncodedFormEntity(pairList, Charset.forName("UTF-8")));  
            }
            
            response = httpClient.execute(httpPost);  
            LOG.debug(response.toString());  
            HttpEntity entity = response.getEntity();  
            LOG.debug(EntityUtils.toString(entity, "UTF-8"));
            
        } catch (IOException e) {  
            LOG.error(e.getMessage(), e);
            
        } finally {  
            if (response != null) {  
                try {  
                    EntityUtils.consume(response.getEntity());  
                } catch (IOException e) {  
                    e.printStackTrace();  
                }  
            }  
        }  
	}
	
}
